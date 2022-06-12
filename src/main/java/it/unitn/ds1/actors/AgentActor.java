package it.unitn.ds1.actors;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.japi.pf.ReceiveBuilder;
import it.unitn.ds1.common.Configuration;
import it.unitn.ds1.common.Messages;
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
    protected final boolean isManaged;
    private final List<UUID> timeoutMessages;

    public AgentActor(boolean isManaged) {
        this.random = new Random();
        timeoutMessages = new ArrayList<>();
        this.isManaged = isManaged;
    }

    /**
     * Send a message and add it to the timeout queue
     **/
    protected void sendWithTimeout(Messages.IdentifiableMessage msg, ActorRef dest, int timeout) {
        if (!timeoutMessages.contains(msg.id)) {
            timeoutMessages.add(msg.id);
        }
        dest.tell(msg, getSelf());
        getContext().system().scheduler().scheduleOnce(Duration.create(timeout, TimeUnit.MILLISECONDS), getSelf(), new Messages.TimeoutMessage(msg, dest), getContext().system().dispatcher(), getSelf());
    }

    private void onTimeoutMessage(Messages.TimeoutMessage msg) {
        if (timeoutMessages.contains(msg.msg.id)) {
            timeoutMessages.remove(msg.msg.id);
            onTimeout(msg.msg, msg.dest);
        }
    }

    protected void removeTimeoutRequest(UUID id) {
        timeoutMessages.remove(id);
    }

    protected abstract void onAck(UUID ackId);

    protected abstract void onTimeout(Messages.IdentifiableMessage msg, ActorRef dest);

    protected void clearTimeoutsMessages() {
        timeoutMessages.clear();
    }

    public abstract ReceiveBuilder receiveBuilderFactory();

    private void onAckMessage(Messages.AckMessage msg) {
        // If an ack has been received, that means that the target of the request was active, reset timeout
        //printFormatted("ACK! O.O from %s", getSender().path().name());
        timeoutMessages.remove(msg.id);
        onAck(msg.id);
    }

    protected void printFormattedForce(String message, Object... args) {
        System.out.format("%s %s %n", String.format("%-10s |", getSelf().path().name()), String.format(message, args));
        System.out.flush();
    }

    protected void printFormatted(String message, Object... args) {
        if (!Configuration.VERBOSE) return;
        printFormattedForce(message, args);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilderFactory().match(Messages.AckMessage.class, this::onAckMessage).match(Messages.TimeoutMessage.class, this::onTimeoutMessage).build();
    }
}
