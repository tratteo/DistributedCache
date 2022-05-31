package it.unitn.ds1.actors;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import it.unitn.ds1.Configuration;
import it.unitn.ds1.Messages;
import scala.concurrent.duration.Duration;

import java.io.Serializable;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class DatabaseActor extends AbstractActor {
    private List<ActorRef> l1Caches;
    private Dictionary<Integer, Integer> database;

    public DatabaseActor() {
        populateDatabase();
    }

    static public Props props() {
        return Props.create(DatabaseActor.class, DatabaseActor::new);
    }

    /**
     * Populate the database with some dummy elements
     **/
    private void populateDatabase() {
        database = new Hashtable<>();
        Random random = new Random();
        for (int i = 0; i < Configuration.DATABASE_KEYS; i++) {
            database.put(i, random.nextInt());
        }
    }

    /**
     * Update the list of all the L1 caches
     **/
    private void onTopologyMessage(Messages.TopologyMessage message) {
        this.l1Caches = message.children;
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder().match(Messages.TopologyMessage.class, this::onTopologyMessage).match(Messages.ReadMessage.class, this::onReadMessage).match(Messages.WriteMessage.class, this::onWriteMessage).match(Messages.Timeout.class, this::onTimeout).build();
    }

    /**
     * Process the write request
     **/
    private void onWriteMessage(Messages.WriteMessage msg) {
        //update our database
        database.put(msg.key, msg.value);
        System.out.format("[%s] | %s %n", getSelf().path().name(), msg.toString());
        System.out.flush();

        //if the write operation is critical, then it is necessary to ensure that before updating no cache holds old values of item
        //so, we have to remove the item with old value from every cache before refill
        if (msg.isCritical){
            Serializable removeMsg = new Messages.RemoveMessage(msg.id, msg.key, msg.value);
            for(ActorRef l1cache: l1Caches){
                //check if the l2cache is crashed or not
                //if crashed or timeout, add the msg on the active req and retry after some milliseconds
                l1cache.tell(removeMsg, ActorRef.noSender());
                //setTimeout(Configuration.TIMEOUT, removeMsg);
            }
        }

        Serializable refillMsg = new Messages.RefillMessage(msg.id, msg.key, msg.value);
        //send the update to all L1 caches
        for(ActorRef l1cache: l1Caches){
            //check if the l2cache is crashed or not
            //if crashed or timeout, add the msg on the active req and retry after some milliseconds
            l1cache.tell(refillMsg, ActorRef.noSender());
        }

        //send back the message to the sender
        getSender().tell(new Messages.OperationResultMessage(msg.id, Messages.OperationResultMessage.Operation.Write, msg.key, database.get(msg.key)), getSelf());
        //setTimeout(Configuration.TIMEOUT, refillMsg);
    }

    /**
     * Process the read request
     **/
    private void onReadMessage(Messages.ReadMessage msg) {
        //operation is the same in both normal and critical version
        System.out.format("[%s] | %s %n", getSelf().path().name(), msg.toString());
        System.out.flush();
        getSender().tell(new Messages.OperationResultMessage(msg.id, Messages.OperationResultMessage.Operation.Read, msg.key, database.get(msg.key)), getSelf());
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
        System.out.println("Timeout. Send the refill message again.");

        //send the update to all L1 caches
        for(ActorRef l1cache: l1Caches){
            //check if the l2cache is crashed or not
            //if crashed or timeout, add the msg on the active req and retry after some milliseconds
            l1cache.tell(msg.msg, ActorRef.noSender());
        }
        //setTimeout(Configuration.TIMEOUT, msg);

    }
}
