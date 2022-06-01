package it.unitn.ds1;

import akka.actor.ActorRef;
import scala.concurrent.duration.Duration;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class Messages {
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

    public static class OperationResultMessage implements Serializable {
        public final int key;
        public final UUID id;
        public final Operation operation;
        public final int value;

        public OperationResultMessage(UUID id, Operation operation, int key, int value) {
            this.key = key;
            this.operation = operation;
            this.id = id;
            this.value = value;
        }

        @Override
        public String toString() {
            return String.format("[%s] %s -> {%d, %d}", id, operation.toString(), key, value);
        }

        public enum Operation {
            Read, Write
        }
    }

    public static class WriteMessage implements Serializable {
        public final int key;
        public final UUID id;
        public final int value;
        public final boolean isCritical;

        public WriteMessage(UUID id, int key, int value, boolean isCritical) {
            this.key = key;
            this.id = id;
            this.value = value;
            this.isCritical = isCritical;
        }

        @Override
        public String toString() {
            return String.format("[%s] W | CRITICAL: %s -> {%d, %d}", id, isCritical, key, value);
        }
    }

    public static class ReadMessage implements Serializable {
        public final int key;
        public final UUID id;

        public final boolean isCritical;

        public ReadMessage(UUID id, int key, boolean isCritical) {
            this.key = key;
            this.id = id;
            this.isCritical = isCritical;
        }

        @Override
        public String toString() {
            return String.format("[%s] R | CRITICAL: %s -> %d", id, isCritical, key);
        }
    }

    //necessary to propagate the update towards all caches
    public static class RefillMessage implements Serializable {
        public final int key;
        public final UUID id;
        public final int value;


        public RefillMessage(UUID id, int key, int value) {
            this.key = key;
            this.id = id;
            this.value = value;
        }

        @Override
        public String toString() {
            return String.format("[%s] REFILL -> {%d, %d}", id, key, value);
        }
    }

    public static class Timeout implements Serializable {
        public Serializable msg;

        public Timeout(Serializable msg){
            this.msg = msg;
        }

    }

    public static class RecoveryMessage implements Serializable {}

    //necessary for critical write, as it is necessary to ensure that before updating no cache holds old values of item
    public static class RemoveMessage implements Serializable {
        public final int key;
        public final UUID id;
        public final int value;


        public RemoveMessage(UUID id, int key, int value) {
            this.key = key;
            this.id = id;
            this.value = value;
        }

        @Override
        public String toString() {
            return String.format("[%s] REMOVE -> {%d, %d}", id, key, value);
        }
    }


}
