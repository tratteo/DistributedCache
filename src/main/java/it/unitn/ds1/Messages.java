package it.unitn.ds1;

import akka.actor.Actor;
import akka.actor.ActorRef;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Messages {
    public static class TopologyMessage implements Serializable {
        public final List<ActorRef> children;
        public final ActorRef parent;
        public TopologyMessage(ActorRef parent, List<ActorRef> children) {
            this.children = children != null ? Collections.unmodifiableList(new ArrayList<>(children)) : null;
            this.parent = parent;
        }
    }
    public static class OperationResultMessage implements Serializable {
        public final int key;
        public final int value;
        public final ActorRef client;
        public OperationResultMessage(int key,ActorRef client, int value) {
            this.key = key;
            this.value = value;
            this.client = client;
        }
    }
    public static class WriteMessage implements Serializable {
        public final int key;
        public final int value;
        private final boolean isCritical;
        public  final ActorRef client;
        public WriteMessage(int key, int value,ActorRef client, boolean isCritical) {
            this.key = key;
            this.value = value;
            this.client = client;
            this.isCritical = isCritical;

        }
    }
    public static class ReadMessage implements Serializable {
        public final int key;
        public  final ActorRef client;
        private final boolean isCritical;
        public ReadMessage(int key, ActorRef client, boolean isCritical) {
            this.key = key;
            this.client = client;
            this.isCritical = isCritical;
        }
    }

}
