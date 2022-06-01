package it.unitn.ds1.actors;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.util.Timeout;
import it.unitn.ds1.Configuration;
import it.unitn.ds1.Messages;
import scala.concurrent.duration.Duration;

import java.io.Serializable;
import java.util.Hashtable;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class CacheActor extends AbstractActor {
    private final Hashtable<Integer, Integer> cache;
    private final Random random;
    private ActorRef database;
    private ActorRef parent;
    private List<ActorRef> children;
    private Hashtable<UUID, ActorRef> activeRequests;
    //private boolean crashed;

    public CacheActor() {
        cache = new Hashtable<>();
        activeRequests = new Hashtable<>();
        random = new Random();
        //crashed = false;
    }

    static public Props props() {
        return Props.create(CacheActor.class, CacheActor::new);
    }

    private void onTopologyMessage(Messages.TopologyMessage message) {
        this.parent = message.parent;
        this.children = message.children;
        this.database = message.database;
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(Messages.TopologyMessage.class, this::onTopologyMessage)
                .match(Messages.ReadMessage.class, this::onReadMessage)
                .match(Messages.WriteMessage.class, this::onWriteMessage)
                .match(Messages.OperationResultMessage.class, this::onOperationResultMessage)
                .match(Messages.RefillMessage.class, this::onRefillMessage)
                .match(Messages.Timeout.class, this::onTimeout)
                .match(Messages.RemoveMessage.class, this::onRemoveMessage)
                .build();
    }

    private void onWriteMessage(Messages.WriteMessage msg) {
        //Send the message to the parent
        ActorRef issuer = getSender();

        if (random.nextDouble() < 0.1){
            crash();
            return;
        }

        activeRequests.put(msg.id, issuer);
        System.out.format("WRITE Request from [%s] | %s %n", getSelf().path().name(), msg.toString());

        //check if L1cache is crashed, or not
        //if crashed or timeout, send the message to database
        parent.tell(msg, getSelf());
        /*if (parent != database) {
            setTimeout(Configuration.TIMEOUT, msg);
        }*/
        System.out.flush();
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

        if (random.nextDouble() < 0.1){
            crash();
            return;
        }

        //Check if the operation is critical or not
        if (msg.isCritical){
            //if it is critical, then send the message regardless of cached item
            activeRequests.put(msg.id, issuer);
            System.out.format("[%s] CRITICAL READ | %s %n", getSelf().path().name(), msg.toString());
            System.out.flush();
            parent.tell(msg, getSelf());

        }else{
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
                System.out.format("[%s] MISS | %s %n", getSelf().path().name(), msg.toString());
                System.out.flush();
                //check if L1cache is crashed, or not
                //if crashed or timeout, send the message to database

                parent.tell(msg, getSelf());
            /*if (parent != database) {
                setTimeout(Configuration.TIMEOUT, msg);
            }*/
                //System.out.flush();
            }
        }




    }

    private void onRefillMessage(Messages.RefillMessage msg){
        // Update our cache
        cache.put(msg.key, msg.value);
        System.out.format("[%s] | %s %n", getSelf().path().name(), msg.toString());
        System.out.flush();

        //check if the cache has children or not, i.e. if it is a L1 or L2 cache
        if (children != null){
            for(ActorRef l2cache: children){
                //check if the l2cache is crashed or not
                //if crashed or timeout, add the msg on the active req and retry after some milliseconds
                l2cache.tell(msg, ActorRef.noSender());
                //setTimeout(Configuration.TIMEOUT);
            }
        }


    }

    private void onRemoveMessage(Messages.RemoveMessage msg){
        // remove the item from cache
        //System.out.println("BEFORE REMOVE:"+msg.key+" "+msg.value+" "+cache.containsKey(msg.key));
        cache.remove(msg.key);
        //System.out.println("AFTER REMOVE:" + cache.containsKey(msg.key));
        System.out.format("[%s] | %s %n", getSelf().path().name(), msg.toString());
        System.out.flush();

        //check if the cache has children or not, i.e. if it is a L1 or L2 cache
        if (children != null){
            for(ActorRef l1cache: children){
                //check if the l2cache is crashed or not
                //if crashed or timeout, add the msg on the active req and retry after some milliseconds
                l1cache.tell(msg, ActorRef.noSender());
            }
        }

    }


    // emulate a crash and a recovery in a given time
    void crash() {
        getContext().become(crashed());
        //crashed = true;
        cache.clear();
        System.out.format("[%s] Crash!", getSelf().path().name());
        System.out.flush();

        // setting a timer to "recover"
        getContext().system().scheduler().scheduleOnce(
                Duration.create(Configuration.RECOVERY_TIME, TimeUnit.MILLISECONDS),
                getSelf(),
                new Messages.RecoveryMessage(), // message sent to myself
                getContext().system().dispatcher(), getSelf()
        );
    }


    final Receive crashed() {
        return receiveBuilder()
                .match(Messages.RecoveryMessage.class, this::onRecovery)
                //.match(ChatMsg.class, this::onCrashedChatMsg)
                //.matchAny(msg -> {})
                .matchAny(msg -> System.out.println(getSelf().path().name() + " ignoring " + msg.getClass().getSimpleName() + " (crashed)"))
                .build();
    }

    private void onRecovery(Messages.RecoveryMessage msg) {
        getContext().become(createReceive());
        //crashed = false;
        System.out.format("[%s] Recovered!", getSelf().path().name());
        System.out.flush();
    }

    void setTimeout(int time, Serializable msg) {
        getContext().system().scheduler().scheduleOnce(
                Duration.create(time, TimeUnit.MILLISECONDS),
                getSelf(),
                new Messages.Timeout(msg), // the message to send
                getContext().system().dispatcher(),
                getSelf()
        );
    }

    public void onTimeout(Messages.Timeout msg) {
        System.out.println("Timeout. Send request to the database.");
        database.tell(msg.msg, getSelf());
    }

}
