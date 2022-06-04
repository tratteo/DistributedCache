package it.unitn.ds1;

import akka.actor.ActorRef;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class Messages {
    public static abstract class IdentifiableMessage implements Serializable {
        public final UUID id;

        public IdentifiableMessage(UUID id) {
            this.id = id;
        }

        @Override
        public String toString() {
            return String.format("{id: %s} - ", id);
        }
    }

    public static class AckMessage extends IdentifiableMessage {

        public AckMessage(UUID id) {
            super(id);
        }

        @Override
        public String toString() {
            return super.toString() + "ACK";
        }
    }

    public static class TopologyMessage implements Serializable {
        public final List<ActorRef> children;
        public final ActorRef parent;
        public final ActorRef database;

        public TopologyMessage(ActorRef parent, List<ActorRef> children, ActorRef database) {
            this.children = children != null ? Collections.unmodifiableList(new ArrayList<>(children)) : null;
            this.parent = parent;
            this.database = database;
        }
    }

    public static class ClientsMessage implements Serializable {
        public final List<ActorRef> clients;

        public ClientsMessage(List<ActorRef> clients) {
            this.clients = clients;
        }
    }

    public static class OperationResultMessage extends IdentifiableMessage {
        public final int key;
        public final Operation operation;
        public final int value;

        public OperationResultMessage(UUID id, Operation operation, int key, int value) {
            super(id);
            this.key = key;
            this.operation = operation;
            this.value = value;
        }

        @Override
        public String toString() {
            return super.toString() + String.format("%s result -> {%d, %d}", operation.toString(), key, value);
        }

        public enum Operation {
            Read, Write
        }
    }

    public static class WriteMessage extends IdentifiableMessage {
        public final int key;
        public final int value;
        public final boolean isCritical;

        public WriteMessage(UUID id, int key, int value, boolean isCritical) {
            super(id);
            this.key = key;
            this.value = value;
            this.isCritical = isCritical;
        }

        @Override
        public String toString() {
            return super.toString() + String.format("WRITE, critical: %s -> {%d, %d}", isCritical, key, value);
        }
    }

    public static class ReadMessage extends IdentifiableMessage {
        public final int key;

        public final boolean isCritical;

        public ReadMessage(UUID id, int key, boolean isCritical) {
            super(id);
            this.key = key;
            this.isCritical = isCritical;
        }

        @Override
        public String toString() {
            return super.toString() + String.format("READ, critical: %s -> {%d}", isCritical, key);
        }
    }

    public static class RefillMessage extends IdentifiableMessage {
        public final int key;

        public final int value;


        public RefillMessage(UUID id, int key, int value) {
            super(id);
            this.key = key;
            this.value = value;
        }

        @Override
        public String toString() {
            return super.toString() + String.format("REFILL -> {%d, %d}", key, value);
        }
    }

    public static class TimeoutMessage implements Serializable {
        public final IdentifiableMessage msg;
        public final ActorRef dest;

        public TimeoutMessage(IdentifiableMessage msg, ActorRef dest) {
            this.msg = msg;
            this.dest = dest;
        }

        @Override
        public String toString() {
            return String.format("TIMEOUT %s, of %s", msg.toString(), dest.path().name());
        }
    }

    public static class RecoveryMessage implements Serializable {}

    public static class RemoveMessage extends IdentifiableMessage {
        public final int key;

        public RemoveMessage(UUID id, int key) {
            super(id);
            this.key = key;
        }

        @Override
        public String toString() {
            return super.toString() + String.format("REMOVE -> {%d}", key);
        }
    }


}
