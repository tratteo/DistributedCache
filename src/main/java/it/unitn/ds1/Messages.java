package it.unitn.ds1;

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
}
