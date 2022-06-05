package it.unitn.ds1.actors;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import com.google.gson.Gson;
import it.unitn.ds1.Configuration;
import it.unitn.ds1.Messages;

import java.io.Serializable;
import java.util.*;

public class DatabaseActor extends AgentActor {
    private final List<ActorRef> l1Caches;
    private Map<Integer, Integer> databaseKeys;

    public DatabaseActor() {
        l1Caches = new ArrayList<>();
        populateDatabase();
    }

    static public Props props() {
        return Props.create(DatabaseActor.class, DatabaseActor::new);
    }

    @Override
    protected void onTimeout(Messages.IdentifiableMessage msg, ActorRef dest) {

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
        for (ActorRef a : message.children) {
            if (!l1Caches.contains(a)) {
                printFormatted("Database adding %s as child", a.path().name());
                l1Caches.add(a);
            }
        }
        if (message.parent != null) {
            if (l1Caches.remove(message.parent)) {
                printFormatted("Database removing %s as child", message.parent.path().name());
            }
        }
    }

    private void onWriteMessage(Messages.WriteMessage msg) {
        //Update the database
        databaseKeys.put(msg.key, msg.value);
        printFormatted("%s", msg);
        databaseSnapshot();
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
        printFormatted("%s", msg);
        getSender().tell(new Messages.AckMessage(msg.id), getSelf());
        getSender().tell(new Messages.OperationResultMessage(msg.id, Messages.OperationResultMessage.Operation.Read, msg.key, databaseKeys.get(msg.key)), getSelf());
    }

    private void databaseSnapshot() {
        Gson gson = new Gson();
        String json = gson.toJson(databaseKeys);
        printFormatted("Snapshot %s", json);
    }

    //endregion
    @Override
    public ReceiveBuilder receiveBuilderFactory() {
        return receiveBuilder().match(Messages.TopologyMessage.class, this::onTopologyMessage).match(Messages.ReadMessage.class, this::onReadMessage).match(Messages.WriteMessage.class, this::onWriteMessage);
    }
}
