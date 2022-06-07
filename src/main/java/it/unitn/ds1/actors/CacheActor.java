package it.unitn.ds1.actors;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import it.unitn.ds1.utils.*;
import it.unitn.ds1.utils.enums.CacheProtocolStage;
import it.unitn.ds1.utils.enums.Operation;
import scala.concurrent.duration.Duration;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class CacheActor extends AgentActor {
    private final Map<Integer, CacheElement> cache;
    private final Hashtable<UUID, ActorRef> activeRequests;
    private final ArrayList<RemoveRequest> removeRequests;
    private final CrashSynchronizationContext crashSynchronizationContext;
    private final Queue<CrashMessage> pendingCrashes;
    private ActorRef parent;
    private List<ActorRef> children;
    private List<ActorRef> clients;
    private ActorRef database;

    public CacheActor(boolean isManaged, CrashSynchronizationContext crashSynchronizationContext) {
        super(isManaged);
        this.crashSynchronizationContext = crashSynchronizationContext;
        cache = new Hashtable<>();
        pendingCrashes = new ArrayDeque<>();
        activeRequests = new Hashtable<>();
        removeRequests = new ArrayList<>();
    }

    static public Props props(boolean isManaged, CrashSynchronizationContext crashSynchronizationContext) {
        return Props.create(CacheActor.class, () -> new CacheActor(isManaged, crashSynchronizationContext));
    }

    @Override
    protected void onAck(UUID ackId) {
        ArrayList<RemoveRequest> toRemove = new ArrayList<>();
        for (RemoveRequest request : removeRequests) {
            request.requestsIds.remove(ackId);
            if (request.requestsIds.size() <= 0) {
                toRemove.add(request);
                printFormatted("REMOVE sub-protocol completed");
                request.issuer.tell(new Messages.AckMessage(request.originalRequestId), getSelf());
            }
        }
        removeRequests.removeAll(toRemove);
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
                .match(CrashMessage.class, this::onCrashMessage)
                .match(CacheCycleMessage.class, this::onCacheCycle)
                .match(Messages.RecoveryMessage.class, this::onRecoveryMessage)
                .match(Messages.RemoveMessage.class, this::onRemoveMessage);
    }

    private void onCrashMessage(CrashMessage msg) {
        pendingCrashes.add(msg);
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
        if (shouldCrash(CacheProtocolStage.Write)) {
            crash(CacheProtocolStage.Write);
            return;
        }
        //Send the message to the parent
        ActorRef issuer = getSender();
        activeRequests.put(msg.id, issuer);
        sendWithTimeout(msg, parent, Configuration.TIMEOUT);
    }

    private void updateOrAddCacheElement(int key, int value) {
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
        if (shouldCrash(CacheProtocolStage.Result)) {
            crash(CacheProtocolStage.Result);
            return;
        }

        if (msg.success && msg.operation == Operation.Read) {
            updateOrAddCacheElement(msg.key, msg.value);
        }
        else {
            removeTimeoutRequest(msg.id);
        }
        // Forward back the message to the request issuer
        if (activeRequests.containsKey(msg.id)) {
            ActorRef issuer = activeRequests.get(msg.id);
            issuer.tell(new Messages.AckMessage(msg.id), getSelf());
            issuer.tell(msg, getSelf());
            activeRequests.remove(msg.id);
        }

    }

    private void onReadMessage(Messages.ReadMessage msg) {
        if (shouldCrash(CacheProtocolStage.Read)) {
            crash(CacheProtocolStage.Read);
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
                Messages.OperationResultMessage res = Messages.OperationResultMessage.Success(msg.id, Operation.Read, msg.key, cache.get(msg.key).value());
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
        if (shouldCrash(CacheProtocolStage.Refill)) {
            crash(CacheProtocolStage.Refill);
            return;
        }
        if (cache.containsKey(msg.key)) {
            updateOrAddCacheElement(msg.key, msg.value);
        }

        if (children != null) {
            for (ActorRef l2cache : children) {
                l2cache.tell(msg, getSelf());
            }
        }
        //printFormatted("%s", msg);
    }

    private void onRemoveMessage(Messages.RemoveMessage msg) {
        if (shouldCrash(CacheProtocolStage.Remove)) {
            crash(CacheProtocolStage.Remove);
            return;
        }
        cache.remove(msg.key);
        if (children != null) {
            ArrayList<UUID> requestsIds = new ArrayList<>();
            for (ActorRef l1cache : children) {
                UUID id = UUID.randomUUID();
                requestsIds.add(id);
                sendWithTimeout(new Messages.RemoveMessage(id, msg.key), l1cache, Configuration.TIMEOUT);
            }
            removeRequests.add(new RemoveRequest(msg.key, 0, msg.id, getSender(), requestsIds));
        }
        else {
            printFormatted("REMOVE sub-protocol completed");
            getSender().tell(new Messages.AckMessage(msg.id), getSelf());
        }
        //printFormatted("%s", msg);
    }

    private void onRecoveryMessage(Messages.RecoveryMessage msg) {
        if (getSender() == getSelf()) {
            getContext().become(createReceive());

            try {crashSynchronizationContext.decrementCrashes();} catch (Exception ignored) {}

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
        Optional<RemoveRequest> optReq = removeRequests.stream().filter(r -> r.requestsIds.contains(msg.id)).findFirst();
        if (!optReq.isPresent()) {
            parent = database;
            Messages.TopologyMessage topMsg = new Messages.TopologyMessage(null, new ArrayList<>(Collections.singletonList(getSelf())), database);
            printFormatted("%s is dead X(. Targeting %s ", dest.path().name(), parent.path().name());
            parent.tell(topMsg, getSelf());
            sendWithTimeout(msg, parent, Configuration.TIMEOUT);
        }
        else {
            removeRequests.remove(optReq.get());
        }
    }

    //endregion

    private synchronized boolean shouldCrash(CacheProtocolStage stage) {
        try {
            if (isManaged) {
                return crashSynchronizationContext.canCrash(stage) && random.nextDouble() < Configuration.P_CRASH;
            }
            else {
                CrashMessage crash = pendingCrashes.peek();
                if (crash != null && crash.stage == stage) {
                    pendingCrashes.poll();
                    return true;
                }
                return false;
            }
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Emulate a crash and set up a notifier to recover after a given time
     **/
    void crash(CacheProtocolStage context) {
        getContext().become(crashed());
        cache.clear();
        activeRequests.clear();
        removeRequests.clear();
        clearTimeoutsMessages();
        try {crashSynchronizationContext.incrementCrashes();} catch (Exception ignored) {}
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

    public static class CrashMessage implements Serializable {
        public final CacheProtocolStage stage;
        public final int recoverTime;

        public CrashMessage(CacheProtocolStage stage) {
            this(stage, new Random(System.nanoTime()).nextInt(Configuration.RECOVERY_MAX_TIME - Configuration.RECOVERY_MIN_TIME) - Configuration.RECOVERY_MIN_TIME);
        }

        public CrashMessage(CacheProtocolStage stage, int recoverTime) {
            this.stage = stage;
            this.recoverTime = recoverTime;
        }
    }

}
