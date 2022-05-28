package it.unitn.ds1.actors;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import it.unitn.ds1.Messages;

import java.util.Hashtable;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class CacheActor extends AbstractActor {
    private final Hashtable<Integer, Integer> cache;
    private final Random random;
    private ActorRef parent;
    private List<ActorRef> children;
    private Hashtable<UUID, ActorRef> activeRequests;

    public CacheActor() {
        cache = new Hashtable<>();
        activeRequests = new Hashtable<>();
        random = new Random();
    }

    static public Props props() {
        return Props.create(CacheActor.class, CacheActor::new);
    }

    private void onTopologyMessage(Messages.TopologyMessage message) {
        this.parent = message.parent;
        this.children = message.children;
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(Messages.TopologyMessage.class, this::onTopologyMessage)
                .match(Messages.ReadMessage.class, this::onReadMessage)
                .match(Messages.WriteMessage.class, this::onWriteMessage)
                .match(Messages.OperationResultMessage.class, this::onOperationResultMessage)
                .build();
    }

    private void onWriteMessage(Messages.WriteMessage msg) {
        //TODO
    }

    private void onOperationResultMessage(Messages.OperationResultMessage msg) {
        // Update our cache
        cache.put(msg.key, msg.value);
        // Forward back the message to the request issuer
        if (activeRequests.containsKey(msg.id)) {
            ActorRef issuer = activeRequests.get(msg.id);
            issuer.tell(msg, getSelf());
            activeRequests.remove(msg.id);
        }
    }

    private void onReadMessage(Messages.ReadMessage msg) {
        ActorRef issuer = getSender();
        // Cache hit, respond immediately to the requester (sender), client or cache
        if (cache.containsKey(msg.key)) {
            Messages.OperationResultMessage res = new Messages.OperationResultMessage(msg.id, Messages.OperationResultMessage.Operation.Read, msg.key, cache.get(msg.key));
            issuer.tell(res, getSelf());
            System.out.format("[%s] HIT | %s %n", getSelf().path().name(), msg.toString());
            System.out.flush();
        }
        // Cache miss, add the request to the active requests and forward it to our parent. Keeping a list of all the pending requests,
        // allows the cache to respond to the issuer of the request on the result is received
        else {
            activeRequests.put(msg.id, issuer);
            parent.tell(msg, getSelf());
            System.out.format("[%s] MISS | %s %n", getSelf().path().name(), msg.toString());
            System.out.flush();
        }

    }

}
