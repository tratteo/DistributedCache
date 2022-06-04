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
    private ArrayList<ActorRef> l2Caches;


    public ClientActor() {
        super();
    }

    static public Props props() {
        return Props.create(ClientActor.class, ClientActor::new);
    }

    @Override
    public void preStart() {
        // Schedule an internal operation notifier at startup
        Cancellable cancellable = getContext().system().scheduler().scheduleOnce(
                //Duration.create(random.nextInt(5000) + 300, TimeUnit.MILLISECONDS),
                Duration.create(1000, TimeUnit.MILLISECONDS), getSelf(), new OperationNotifyMessage(), getContext().system().dispatcher(), ActorRef.noSender());
    }

    /**
     * @return A random L2 cache from the ones registered in the topology
     **/
    private ActorRef getRandomL2Cache() {
        return l2Caches.get(random.nextInt(l2Caches.size()));
    }

    // region Message Handlers

    private void onOperationMessageResult(Messages.OperationResultMessage msg) {
        System.out.format("[%s] | %s %n%n", getSelf().path().name(), msg.toString());
        System.out.flush();
        // Schedule new operation
        getContext().system().scheduler().scheduleOnce(Duration.create(1000, TimeUnit.MILLISECONDS), getSelf(), new OperationNotifyMessage(), getContext().system().dispatcher(), ActorRef.noSender());
    }

    private void onOperationNotifyMessage(OperationNotifyMessage message) {
        if (l2Caches != null && l2Caches.size() > 0) {
            performTotallyRandomOperation();
        }
    }

    @Override
    public void onTimeout(Messages.IdentifiableMessage msg, ActorRef dest) {
        l2Caches.remove(dest);
        ActorRef newCache = getRandomL2Cache();
        System.out.format("[%s] | Removed %s as it seems dead X(, targeting new cache %s %n", getSelf().path().name(), dest.path().name(), newCache.path().name());
        sendWithTimeout(msg, newCache);

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
        System.out.format("[%s] | Requesting %s %n", getSelf().path().name(), message);
        System.out.flush();
        sendWithTimeout(message, cache);
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
        if (!l2Caches.contains(cache)) {
            l2Caches.add(cache);
            System.out.format("[%s] cache %s recovered! :D %n", getSelf().path().name(), cache.path().name());
            System.out.flush();
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
