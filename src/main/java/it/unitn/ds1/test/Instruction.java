package it.unitn.ds1.test;

import akka.actor.ActorRef;

import java.io.Serializable;

public class Instruction {
    public final ActorRef actor;
    public final Serializable messageInstruction;
    public final int delay;

    public Instruction(ActorRef actor, Serializable messageInstruction) {
        this(actor, messageInstruction, 0);
    }

    public Instruction(ActorRef actor, Serializable messageInstruction, int delay) {
        this.actor = actor;
        this.delay = delay;
        this.messageInstruction = messageInstruction;
    }

    public void execute() {
        actor.tell(messageInstruction, ActorRef.noSender());
    }
}
