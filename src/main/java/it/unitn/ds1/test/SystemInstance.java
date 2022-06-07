package it.unitn.ds1.test;

import akka.actor.ActorRef;

import java.util.List;

public class SystemInstance {
    public final List<ActorRef> clients;
    public final SystemTopologyDescriptor topologyDescriptor;
    public final List<TopologyActor> l1Caches;
    public final List<TopologyActor> l2Caches;
    public final TopologyActor database;

    public SystemInstance(SystemTopologyDescriptor topologyDescriptor, TopologyActor database, List<ActorRef> clients, List<TopologyActor> l1Caches, List<TopologyActor> l2Caches) {
        this.topologyDescriptor = topologyDescriptor;
        this.database = database;
        this.clients = clients;
        this.l2Caches = l2Caches;
        this.l1Caches = l1Caches;
    }

    public static class TopologyActor {
        public final ActorRef parent;
        public final ActorRef self;
        public final List<ActorRef> children;

        public TopologyActor(ActorRef self, ActorRef parent, List<ActorRef> children) {
            this.self = self;
            this.parent = parent;
            this.children = children;
        }
    }
}
