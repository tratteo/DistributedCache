package it.unitn.ds1.actors;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.Props;
import it.unitn.ds1.Configuration;
import it.unitn.ds1.Messages;
import scala.concurrent.duration.Duration;

import java.io.Serializable;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class ClientActor extends AbstractActor {
    private List<ActorRef> l2Caches;
    private Random random;

    public ClientActor(List<ActorRef> l2Caches) {
        this.l2Caches = l2Caches;
        random = new Random();
    }

    static public Props props(List<ActorRef> l2Caches) {
        return Props.create(ClientActor.class, () -> new ClientActor(l2Caches));
    }

    @Override
    public void preStart() throws Exception {
        // Schedule an internal operation notifier at startup
        Cancellable cancellable = getContext().system().scheduler().scheduleOnce(
                //Duration.create(random.nextInt(5000) + 300, TimeUnit.MILLISECONDS),
                Duration.create(5000, TimeUnit.MILLISECONDS), getSelf(), new OperationNotifyMessage(), getContext().system().dispatcher(), ActorRef.noSender());
    }

    /**
     * @return A random L2 cache from the ones registered in the topology
     **/
    private ActorRef getRandomL2Cache() {
        return l2Caches.get(random.nextInt(l2Caches.size()));
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder().match(OperationNotifyMessage.class, this::onOperationNotifyMessage).match(Messages.OperationResultMessage.class, this::onOperationMessageResult).build();

    }

    /**
     * Got result for a specified request
     **/
    private void onOperationMessageResult(Messages.OperationResultMessage msg) {
        System.out.format("[%s] | %s %n", getSelf().path().name(), msg.toString());
        System.out.flush();
    }

    /**
     * Called when it is time to perform a new operation
     **/
    private void onOperationNotifyMessage(OperationNotifyMessage message) {
        performTotallyRandomOperation();
        Cancellable cancellable = getContext().system().scheduler().scheduleOnce(Duration.create(5000, TimeUnit.MILLISECONDS), getSelf(), new OperationNotifyMessage(), getContext().system().dispatcher(), ActorRef.noSender());
    }

    private void performTotallyRandomOperation() {
        if (random.nextDouble() < 0.25) {
            //TODO perform write
        }
        else {
            ActorRef cache = getRandomL2Cache();
            cache.tell(new Messages.ReadMessage(UUID.randomUUID(), random.nextInt(Configuration.DATABASE_KEYS), false), getSelf());
        }
    }

    /**
     * Message used to notify ourselves that it is time to perform a new totally random operation
     **/
    private static class OperationNotifyMessage implements Serializable {
        public OperationNotifyMessage() {
        }
    }
}
