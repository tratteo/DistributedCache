package it.unitn.ds1.actors;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import it.unitn.ds1.Configuration;
import it.unitn.ds1.Messages;

import java.io.Serializable;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Random;

public class DatabaseActor extends AgentActor {
    private List<ActorRef> l1Caches;
    private Dictionary<Integer, Integer> databaseKeys;

    public DatabaseActor() {
        populateDatabase();
    }

    static public Props props() {
        return Props.create(DatabaseActor.class, DatabaseActor::new);
    }

    /**
     * Populate the database with some dummy totally random elements :)
     **/
    private void populateDatabase() {
        databaseKeys = new Hashtable<>();
        Random random = new Random();
        for (int i = 0; i < Configuration.DATABASE_KEYS; i++) {
            databaseKeys.put(i, random.nextInt(1000));
        }
    }

    //region Message Handlers

    private void onTopologyMessage(Messages.TopologyMessage message) {
        this.l1Caches = message.children;
    }

    private void onWriteMessage(Messages.WriteMessage msg) {
        //Update the database
        databaseKeys.put(msg.key, msg.value);
        System.out.format("[%s] | %s %n", getSelf().path().name(), msg);
        System.out.flush();
        getSender().tell(new Messages.AckMessage(msg.id), getSelf());
        //If the write operation is critical, then it is necessary to ensure that before updating no cache holds old values of item
        //so, we have to remove the item with old value from every cache before refill
        if (msg.isCritical) {
            Serializable removeMsg = new Messages.RemoveMessage(msg.id, msg.key);
            for (ActorRef l1cache : l1Caches) {
                l1cache.tell(removeMsg, getSelf());
            }
        }

        Serializable refillMsg = new Messages.RefillMessage(msg.id, msg.key, msg.value);
        //Send the update to all L1 caches
        for (ActorRef l1cache : l1Caches) {
            l1cache.tell(refillMsg, ActorRef.noSender());
        }

        getSender().tell(new Messages.OperationResultMessage(msg.id, Messages.OperationResultMessage.Operation.Write, msg.key, databaseKeys.get(msg.key)), getSelf());
    }

    private void onReadMessage(Messages.ReadMessage msg) {
        //operation is the same in both normal and critical version
        System.out.format("[%s] | %s %n", getSelf().path().name(), msg.toString());
        System.out.flush();
        getSender().tell(new Messages.AckMessage(msg.id), getSelf());
        getSender().tell(new Messages.OperationResultMessage(msg.id, Messages.OperationResultMessage.Operation.Read, msg.key, databaseKeys.get(msg.key)), getSelf());
    }

    @Override
    public void onTimeout(Messages.IdentifiableMessage msg, ActorRef dest) {
        //        System.out.println("Timeout. Send the refill message again.");
        //
        //        //send the update to all L1 caches
        //        for (ActorRef l1cache : l1Caches) {
        //            //check if the l2cache is crashed or not
        //            //if crashed or timeout, add the msg on the active req and retry after some milliseconds
        //            l1cache.tell(msg, ActorRef.noSender());
        //        }
        //        //setTimeout(Configuration.TIMEOUT, msg);

    }

    //endregion
    @Override
    public ReceiveBuilder receiveBuilderFactory() {
        return receiveBuilder().match(Messages.TopologyMessage.class, this::onTopologyMessage).match(Messages.ReadMessage.class, this::onReadMessage).match(Messages.WriteMessage.class, this::onWriteMessage);
    }
}
