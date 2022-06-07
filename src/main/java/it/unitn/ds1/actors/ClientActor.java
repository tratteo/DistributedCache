package it.unitn.ds1.actors;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import it.unitn.ds1.utils.Configuration;
import it.unitn.ds1.utils.Messages;
import it.unitn.ds1.utils.enums.Operation;
import scala.concurrent.duration.Duration;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class ClientActor extends AgentActor {
    private final ArrayList<ActorRef> latestRecovers;
    private ArrayList<ActorRef> l2Caches;

    public ClientActor(boolean isManaged) {
        super(isManaged);
        latestRecovers = new ArrayList<>();
    }

    static public Props props(boolean isManaged) {
        return Props.create(ClientActor.class, () -> new ClientActor(isManaged));
    }

    @Override
    protected void onAck(UUID ackId) {
    }


    private void onOperationMessageResult(Messages.OperationResultMessage msg) {
        printFormatted("%s%n", msg);
        if (!isManaged) {
            getContext()
                    .system()
                    .scheduler()
                    .scheduleOnce(Duration.create(random.nextInt(Configuration.CLIENT_REQUEST_MAX_TIME - Configuration.CLIENT_REQUEST_MIN_TIME) + Configuration.CLIENT_REQUEST_MIN_TIME, TimeUnit.MILLISECONDS), getSelf(),
                                  getRandomOperation(), getContext().system().dispatcher(), ActorRef.noSender());
        }
    }

    // region Message Handlers
    private OperationNotifyMessage getRandomOperation() {
        boolean isCritical = random.nextDouble() < Configuration.P_CRITICAL;
        Operation operation = random.nextDouble() < Configuration.P_WRITE ? Operation.Write : Operation.Read;
        ActorRef cache = l2Caches.get(random.nextInt(l2Caches.size()));
        return new OperationNotifyMessage(operation, cache, isCritical);
    }

    private void onOperationNotifyMessage(OperationNotifyMessage message) {
        performOperation(message.cacheActor, message.operation, message.isCritical);
    }

    @Override
    public void onTimeout(Messages.IdentifiableMessage msg, ActorRef dest) {
        if (!latestRecovers.contains(dest)) {
            l2Caches.remove(dest);
        }
        latestRecovers.clear();
        ActorRef newCache = l2Caches.get(random.nextInt(l2Caches.size()));
        printFormatted("%s is dead X(. Requesting %s to %s", dest.path().name(), msg, newCache.path().name());
        sendWithTimeout(msg, newCache, Configuration.CLIENT_TIMEOUT);
    }

    public void performOperation(ActorRef cacheActor, Operation operation, boolean isCritical) {
        UUID requestId = UUID.randomUUID();
        Messages.IdentifiableMessage message = null;
        switch (operation) {
            case Read:
                message = new Messages.ReadMessage(requestId, random.nextInt(Configuration.DATABASE_KEYS), isCritical);
                break;
            case Write:
                message = new Messages.WriteMessage(requestId, random.nextInt(Configuration.DATABASE_KEYS), random.nextInt(1000), isCritical);
                break;
        }
        System.out.println();
        printFormatted("Requesting %s to %s", message, cacheActor.path().name());
        sendWithTimeout(message, cacheActor, Configuration.CLIENT_TIMEOUT);
    }

    //endregion


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
        if (!isManaged) {
            getContext().system().scheduler().scheduleOnce(
                    //Duration.create(random.nextInt(5000) + 300, TimeUnit.MILLISECONDS),
                    Duration.create(Configuration.CLIENT_REQUEST_MAX_TIME + Configuration.CLIENT_REQUEST_MIN_TIME, TimeUnit.MILLISECONDS), getSelf(), getRandomOperation(), getContext().system().dispatcher(), ActorRef.noSender());
        }
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
    public static class OperationNotifyMessage implements Serializable {
        public final Operation operation;
        public final ActorRef cacheActor;
        public final boolean isCritical;

        public OperationNotifyMessage(Operation operation, ActorRef cacheActor, boolean isCritical) {
            this.operation = operation;
            this.cacheActor = cacheActor;
            this.isCritical = isCritical;
        }
    }
}
