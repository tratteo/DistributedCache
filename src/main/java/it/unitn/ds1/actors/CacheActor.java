package it.unitn.ds1.actors;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import it.unitn.ds1.Configuration;
import it.unitn.ds1.Messages;
import scala.concurrent.duration.Duration;

import java.util.Hashtable;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class CacheActor extends AgentActor {
    private final Hashtable<Integer, Integer> cache;
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
        return receiveBuilder()
                .match(Messages.TopologyMessage.class, this::onTopologyMessage)
                .match(Messages.ReadMessage.class, this::onReadMessage)
                .match(Messages.WriteMessage.class, this::onWriteMessage)
                .match(Messages.ClientsMessage.class, this::onClientsMessage)
                .match(Messages.OperationResultMessage.class, this::onOperationResultMessage)
                .match(Messages.RefillMessage.class, this::onRefillMessage)
                .match(Messages.RecoveryMessage.class, this::onRecoveryMessage)
                .match(Messages.RemoveMessage.class, this::onRemoveMessage);
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
            crash();
            return;
        }

        //Send the message to the parent
        ActorRef issuer = getSender();
        //We cannot crash here, send back to the issuer the ack
        issuer.tell(new Messages.AckMessage(msg.id), getSelf());
        activeRequests.put(msg.id, issuer);
        //        System.out.format("WRITE Request from [%s] | %s %n", getSelf().path().name(), msg);
        //        System.out.flush();

        sendWithTimeout(msg, parent);
    }

    private void onOperationResultMessage(Messages.OperationResultMessage msg) {
        // Update our cache
        cache.put(msg.key, msg.value);
        // Forward back the message to the request issuer
        if (activeRequests.containsKey(msg.id)) {
            ActorRef issuer = activeRequests.get(msg.id);
            issuer.tell(msg, getSelf());
            activeRequests.remove(msg.id);
        }
    }

    private void onReadMessage(Messages.ReadMessage msg) {
        if (shouldCrash()) {
            crash();
            return;
        }

        ActorRef issuer = getSender();

        //We cannot crash here, send back to the issuer the ack
        issuer.tell(new Messages.AckMessage(msg.id), getSelf());

        //Check if the operation is critical or not
        if (msg.isCritical) {
            //if it is critical, then send the message regardless of cached item
            activeRequests.put(msg.id, issuer);
            System.out.format("[%s] CRITICAL READ | %s %n", getSelf().path().name(), msg);
            System.out.flush();
            sendWithTimeout(msg, parent);
        }
        else {
            // Cache hit, respond immediately to the requester (sender), client or cache
            if (cache.containsKey(msg.key)) {
                Messages.OperationResultMessage res = new Messages.OperationResultMessage(msg.id, Messages.OperationResultMessage.Operation.Read, msg.key, cache.get(msg.key));
                issuer.tell(res, getSelf());
                System.out.format("[%s] HIT | %s %n", getSelf().path().name(), msg);
                System.out.flush();
            }
            // Cache miss, add the request to the active requests and forward it to our parent. Keeping a list of all the pending requests,
            // allows the cache to respond to the issuer of the request on the result is received
            else {
                activeRequests.put(msg.id, issuer);
                System.out.format("[%s] MISS | %s %n", getSelf().path().name(), msg);
                System.out.flush();
                sendWithTimeout(msg, parent);
            }
        }


    }

    private void onRefillMessage(Messages.RefillMessage msg) {
        // Update our cache
        cache.put(msg.key, msg.value);

        if (children != null) {
            for (ActorRef l2cache : children) {
                l2cache.tell(msg, getSelf());
            }
        }
        System.out.format("[%s] | %s %n", getSelf().path().name(), msg);
        System.out.flush();


    }

    private void onRemoveMessage(Messages.RemoveMessage msg) {
        cache.remove(msg.key);

        if (children != null) {
            for (ActorRef l1cache : children) {
                l1cache.tell(msg, getSelf());
            }
        }
        System.out.format("[%s] | %s %n", getSelf().path().name(), msg);
        System.out.flush();

    }

    private void onRecoveryMessage(Messages.RecoveryMessage msg) {

        if (getSender() == getSelf()) {
            getContext().become(createReceive());
            Configuration.currentCrashes--;
            System.out.format("[%s] Recovered! :D %n", getSelf().path().name());
            System.out.flush();

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
            System.out.format("[%s] parent %s recovered! :D %n", getSelf().path().name(), parent.path().name());
            System.out.flush();
        }
    }

    @Override
    public void onTimeout(Messages.IdentifiableMessage msg, ActorRef dest) {
        parent = database;
        System.out.format("[%s] | Removed %s as it seems dead X(, targeting %s %n", getSelf().path().name(), dest.path().name(), parent.path().name());
        sendWithTimeout(msg, parent);
    }

    //endregion

    private boolean shouldCrash() {
        return Configuration.currentCrashes < Configuration.MAX_CONCURRENT_CRASHES && random.nextDouble() < Configuration.P_CRASH;
    }

    /**
     * Emulate a crash and setup a notifier to recover after a given time
     **/
    void crash() {
        getContext().become(crashed());
        cache.clear();
        activeRequests.clear();
        clearTimeoutsMessages();
        Configuration.currentCrashes++;
        System.out.format("[%s] Crash! X( %n", getSelf().path().name());
        System.out.flush();
        getContext().system().scheduler().scheduleOnce(Duration.create(Configuration.RECOVERY_TIME, TimeUnit.MILLISECONDS), getSelf(), new Messages.RecoveryMessage(), // message sent to myself
                                                       getContext().system().dispatcher(), getSelf());
    }

    /**
     * Create the crashed receive builder
     **/
    private Receive crashed() {
        return receiveBuilder().match(Messages.RecoveryMessage.class, this::onRecoveryMessage).matchAny(msg -> System.out.print("")).build();
    }


}
