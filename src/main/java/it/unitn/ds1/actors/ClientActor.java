package it.unitn.ds1.actors;

import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import it.unitn.ds1.Configuration;
import it.unitn.ds1.Messages;
import scala.concurrent.duration.Duration;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class ClientActor extends AgentActor {

    private final ArrayList<ActorRef> latestRecovers;
    private ArrayList<ActorRef> l2Caches;

    public ClientActor() {
        super();
        latestRecovers = new ArrayList<>();
    }

    static public Props props() {
        return Props.create(ClientActor.class, ClientActor::new);
    }

    @Override
    public void preStart() {
        // Schedule an internal operation notifier at startup
        Cancellable cancellable = getContext().system().scheduler().scheduleOnce(
                //Duration.create(random.nextInt(5000) + 300, TimeUnit.MILLISECONDS),
                Duration.create(Configuration.CLIENT_REQUEST_MAX_TIME + Configuration.CLIENT_REQUEST_MIN_TIME, TimeUnit.MILLISECONDS), getSelf(), new OperationNotifyMessage(), getContext().system().dispatcher(), ActorRef.noSender());
    }

    /**
     * @return A random L2 cache from the ones registered in the topology
     **/
    private ActorRef getRandomL2Cache() {
        return l2Caches.get(random.nextInt(l2Caches.size()));
    }

    // region Message Handlers

    private void onOperationMessageResult(Messages.OperationResultMessage msg) {
        printFormatted("%s%n", msg);
        getContext()
                .system()
                .scheduler()
                .scheduleOnce(Duration.create(random.nextInt(Configuration.CLIENT_REQUEST_MAX_TIME - Configuration.CLIENT_REQUEST_MIN_TIME) + Configuration.CLIENT_REQUEST_MIN_TIME, TimeUnit.MILLISECONDS), getSelf(),
                              new OperationNotifyMessage(), getContext().system().dispatcher(), ActorRef.noSender());
    }

    private void onOperationNotifyMessage(OperationNotifyMessage message) {
        if (l2Caches != null && l2Caches.size() > 0) {
            performTotallyRandomOperation();
        }
    }

    @Override
    public void onTimeout(Messages.IdentifiableMessage msg, ActorRef dest) {
        if (!latestRecovers.contains(dest)) {
            l2Caches.remove(dest);
        }
        latestRecovers.clear();
        ActorRef newCache = getRandomL2Cache();
        printFormatted("Removed %s as it seems dead X(, targeting new cache %s", dest.path().name(), newCache.path().name());
        sendWithTimeout(msg, newCache, Configuration.CLIENT_TIMEOUT);
    }

    //endregion

    private void performTotallyRandomOperation() {
        boolean critical = random.nextDouble() < Configuration.P_CRITICAL;
        UUID requestId = UUID.randomUUID();
        Messages.IdentifiableMessage message;
        ActorRef cache = getRandomL2Cache();
        if (random.nextDouble() < Configuration.P_WRITE) {
            message = new Messages.WriteMessage(requestId, random.nextInt(Configuration.DATABASE_KEYS), random.nextInt(1000), critical);
        }
        else {
            message = new Messages.ReadMessage(requestId, random.nextInt(Configuration.DATABASE_KEYS), critical);
        }
        System.out.println();
        printFormatted("Requesting %s", message);
        sendWithTimeout(message, cache, Configuration.CLIENT_TIMEOUT);
    }

    @Override
    public ReceiveBuilder receiveBuilderFactory() {
        return receiveBuilder()
                .match(Messages.TopologyMessage.class, this::onTopologyMessage)
                .match(Messages.RecoveryMessage.class, this::onCacheRecoveryMessage)
                .match(OperationNotifyMessage.class, this::onOperationNotifyMessage)
                .match(Messages.OperationResultMessage.class, this::onOperationMessageResult);
    }

    private void onTopologyMessage(Messages.TopologyMessage msg) {
        this.l2Caches = new ArrayList<>(msg.children);
    }

    private void onCacheRecoveryMessage(Messages.RecoveryMessage msg) {
        ActorRef cache = getSender();
        latestRecovers.add(cache);
        if (!l2Caches.contains(cache)) {
            l2Caches.add(cache);
            printFormatted("Cache %s recovered! Rebuilding topology :D ", cache.path().name());
        }
    }

    /**
     * Message used to notify ourselves that it is time to perform a new totally random operation :)
     **/
    private static class OperationNotifyMessage implements Serializable {
        public OperationNotifyMessage() {
        }
    }
}
