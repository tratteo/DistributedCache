package it.unitn.ds1.actors;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import it.unitn.ds1.CacheElement;
import it.unitn.ds1.Configuration;
import it.unitn.ds1.Messages;
import scala.concurrent.duration.Duration;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class CacheActor extends AgentActor {
    private final Map<Integer, CacheElement> cache;
    private final Hashtable<UUID, ActorRef> activeRequests;
    private ActorRef parent;
    private List<ActorRef> children;
    private List<ActorRef> clients;
    private ActorRef database;

    public CacheActor() {
        super();
        cache = new Hashtable<>();
        activeRequests = new Hashtable<>();
    }

    static public Props props() {
        return Props.create(CacheActor.class, CacheActor::new);
    }


    @Override
    public ReceiveBuilder receiveBuilderFactory() {
        getContext()
                .system()
                .scheduler()
                .scheduleOnce(Duration.create(Configuration.EVICT_GRANULARITY, TimeUnit.MILLISECONDS), getSelf(), new CacheCycleMessage(Configuration.EVICT_GRANULARITY), getContext().system().dispatcher(), ActorRef.noSender());

        return receiveBuilder()
                .match(Messages.TopologyMessage.class, this::onTopologyMessage)
                .match(Messages.ReadMessage.class, this::onReadMessage)
                .match(Messages.WriteMessage.class, this::onWriteMessage)
                .match(Messages.ClientsMessage.class, this::onClientsMessage)
                .match(Messages.OperationResultMessage.class, this::onOperationResultMessage)
                .match(Messages.RefillMessage.class, this::onRefillMessage)
                .match(CacheCycleMessage.class, this::onCacheCycle)
                .match(Messages.RecoveryMessage.class, this::onRecoveryMessage)
                .match(Messages.RemoveMessage.class, this::onRemoveMessage);
    }

    private void onCacheCycle(CacheCycleMessage msg) {

        ArrayList<Integer> toRemove = new ArrayList<>();
        for (Map.Entry<Integer, CacheElement> entry : cache.entrySet()) {
            entry.getValue().step(msg.deltaTime);
            if (!entry.getValue().isValid()) {
                toRemove.add(entry.getKey());
            }
        }
        for (Integer k : toRemove) {
            cache.keySet().remove(k);
        }
        getContext()
                .system()
                .scheduler()
                .scheduleOnce(Duration.create(Configuration.EVICT_GRANULARITY, TimeUnit.MILLISECONDS), getSelf(), new CacheCycleMessage(Configuration.EVICT_GRANULARITY), getContext().system().dispatcher(), ActorRef.noSender());
    }

    // region Message Handlers
    private void onClientsMessage(Messages.ClientsMessage msg) {
        this.clients = msg.clients;
    }

    protected void onTopologyMessage(Messages.TopologyMessage message) {
        this.database = message.database;
        this.parent = message.parent;
        this.children = message.children;
    }

    private void onWriteMessage(Messages.WriteMessage msg) {
        if (shouldCrash()) {
            crash("Write");
            return;
        }
        //Send the message to the parent
        ActorRef issuer = getSender();
        activeRequests.put(msg.id, issuer);
        sendWithTimeout(msg, parent, Configuration.TIMEOUT);
    }

    private void updateCacheElement(int key, int value) {
        CacheElement elem = cache.get(key);
        if (elem == null) {
            elem = new CacheElement(value, Configuration.EVICT_TIME);
            cache.put(key, elem);
        }
        else {
            elem.refreshed(value);
        }
    }

    private void onOperationResultMessage(Messages.OperationResultMessage msg) {
        if (shouldCrash()) {
            crash("Result");
            return;
        }

        updateCacheElement(msg.key, msg.value);
        // Forward back the message to the request issuer
        if (activeRequests.containsKey(msg.id)) {
            ActorRef issuer = activeRequests.get(msg.id);
            issuer.tell(new Messages.AckMessage(msg.id), getSelf());
            issuer.tell(msg, getSelf());
            activeRequests.remove(msg.id);
        }
    }

    private void onReadMessage(Messages.ReadMessage msg) {
        if (shouldCrash()) {
            crash("Read");
            return;
        }

        ActorRef issuer = getSender();
        //Check if the operation is critical or not
        if (msg.isCritical) {
            //if it is critical, then send the message regardless of cached item
            activeRequests.put(msg.id, issuer);
            printFormatted("%s", msg);
            sendWithTimeout(msg, parent, Configuration.TIMEOUT);
        }
        else {
            // Cache hit, respond immediately to the requester (sender), client or cache
            if (cache.containsKey(msg.key)) {
                Messages.OperationResultMessage res = new Messages.OperationResultMessage(msg.id, Messages.OperationResultMessage.Operation.Read, msg.key, cache.get(msg.key).value());
                printFormatted("(cache hit) %s", msg);
                issuer.tell(new Messages.AckMessage(msg.id), getSelf());
                issuer.tell(res, getSelf());

            }
            // Cache miss, add the request to the active requests and forward it to our parent. Keeping a list of all the pending requests,
            // allows the cache to respond to the issuer of the request on the result is received
            else {
                activeRequests.put(msg.id, issuer);
                printFormatted("(cache miss) %s", msg);
                sendWithTimeout(msg, parent, Configuration.TIMEOUT);
            }
        }


    }

    private void onRefillMessage(Messages.RefillMessage msg) {
        if (shouldCrash()) {
            crash("Refill");
            return;
        }
        // Update our cache
        updateCacheElement(msg.key, msg.value);

        if (children != null) {
            for (ActorRef l2cache : children) {
                l2cache.tell(msg, getSelf());
            }
        }
        //printFormatted("%s", msg);
    }

    private void onRemoveMessage(Messages.RemoveMessage msg) {
        if (shouldCrash()) {
            crash("Remove");
            return;
        }
        cache.remove(msg.key);

        if (children != null) {
            for (ActorRef l1cache : children) {
                l1cache.tell(msg, getSelf());
            }
        }
        //printFormatted("%s", msg);
    }

    private void onRecoveryMessage(Messages.RecoveryMessage msg) {

        if (getSender() == getSelf()) {
            getContext().become(createReceive());
            Configuration.currentCrashes--;
            Configuration.currentCrashes = Math.max(Configuration.currentCrashes, 0);
            printFormatted("Recovered! :D ");

            Messages.RecoveryMessage recoveryMsg = new Messages.RecoveryMessage();
            if (children != null) {
                for (ActorRef c : children) {
                    c.tell(recoveryMsg, getSelf());
                }
            }
            else if (clients != null) {
                for (ActorRef c : clients) {
                    c.tell(recoveryMsg, getSelf());
                }
            }
        }
        else {
            parent = getSender();
            Messages.TopologyMessage topMsg = new Messages.TopologyMessage(getSelf(), new ArrayList<>(Collections.singletonList(parent)), database);
            database.tell(topMsg, getSelf());
            printFormatted("Parent %s recovered! Rebuilding topology :D", parent.path().name());
        }
    }

    @Override
    public void onTimeout(Messages.IdentifiableMessage msg, ActorRef dest) {
        parent = database;
        Messages.TopologyMessage topMsg = new Messages.TopologyMessage(dest, new ArrayList<>(Collections.singletonList(getSelf())), database);
        parent.tell(topMsg, getSelf());
        printFormatted("Removed %s as it seems dead X(, targeting %s ", dest.path().name(), parent.path().name());
        sendWithTimeout(msg, parent, Configuration.TIMEOUT);
    }

    //endregion

    private boolean shouldCrash() {
        return Configuration.currentCrashes < Configuration.MAX_CONCURRENT_CRASHES && random.nextDouble() < Configuration.P_CRASH && children != null;
    }

    /**
     * Emulate a crash and setup a notifier to recover after a given time
     **/
    void crash(String context) {
        getContext().become(crashed());
        cache.clear();
        activeRequests.clear();
        clearTimeoutsMessages();
        Configuration.currentCrashes++;
        printFormatted("Crash in context [%s]! X(", context, parent.path().name());
        getContext()
                .system()
                .scheduler()
                .scheduleOnce(Duration.create(random.nextInt(Configuration.RECOVERY_MAX_TIME - Configuration.RECOVERY_MIN_TIME) + Configuration.RECOVERY_MIN_TIME, TimeUnit.MILLISECONDS), getSelf(), new Messages.RecoveryMessage(),
                              getContext().system().dispatcher(), getSelf());
    }

    /**
     * Create the crashed receive builder
     **/
    private Receive crashed() {
        return receiveBuilder().match(Messages.RecoveryMessage.class, this::onRecoveryMessage).matchAny(msg -> System.out.print("")).build();
    }

    private static class CacheCycleMessage implements Serializable {
        public final int deltaTime;

        public CacheCycleMessage(int deltaTime) {
            this.deltaTime = deltaTime;
        }
    }


}
