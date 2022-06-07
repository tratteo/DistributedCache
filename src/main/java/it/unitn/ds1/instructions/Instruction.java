package it.unitn.ds1.instructions;

import akka.actor.ActorRef;

import java.io.Serializable;

public class Instruction {
    public final ActorRef actor;
    public final Serializable messageInstruction;
    public final int delay;

    public Instruction(ActorRef actor, Serializable messageInstruction, int delay) {
        this.actor = actor;
        this.messageInstruction = messageInstruction;
        this.delay = delay;
    }

    public void execute() {
        actor.tell(messageInstruction, ActorRef.noSender());
    }
}
