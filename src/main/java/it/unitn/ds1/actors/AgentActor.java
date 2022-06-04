package it.unitn.ds1.actors;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.japi.pf.ReceiveBuilder;
import it.unitn.ds1.Configuration;
import it.unitn.ds1.Messages;
import scala.concurrent.duration.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Base class for an agent actor of the system
 **/
public abstract class AgentActor extends AbstractActor {
    protected final Random random;
    private final List<UUID> timeoutMessages;

    public AgentActor() {
        this.random = new Random();
        timeoutMessages = new ArrayList<>();
    }

    /**
     * Send a message and add it to the timeout queue
     **/
    protected void sendWithTimeout(Messages.IdentifiableMessage msg, ActorRef dest) {
        if (!timeoutMessages.contains(msg.id)) {
            timeoutMessages.add(msg.id);
        }
        dest.tell(msg, getSelf());
        getContext().system().scheduler().scheduleOnce(Duration.create(Configuration.TIMEOUT, TimeUnit.MILLISECONDS), getSelf(), new Messages.TimeoutMessage(msg, dest), getContext().system().dispatcher(), getSelf());
    }


    private void onTimeoutMessage(Messages.TimeoutMessage msg) {
        if (timeoutMessages.contains(msg.msg.id)) {
            onTimeout(msg.msg, msg.dest);
        }
    }


    protected abstract void onTimeout(Messages.IdentifiableMessage msg, ActorRef dest);

    protected void clearTimeoutsMessages() {
        timeoutMessages.clear();
    }

    public abstract ReceiveBuilder receiveBuilderFactory();

    private void onAckMessage(Messages.AckMessage msg) {
        // If an ack has been received, that means that the target of the request was active, reset timeout
        timeoutMessages.remove(msg.id);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilderFactory().match(Messages.AckMessage.class, this::onAckMessage).match(Messages.TimeoutMessage.class, this::onTimeoutMessage).build();
    }
}
