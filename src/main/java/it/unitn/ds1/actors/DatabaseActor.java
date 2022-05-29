package it.unitn.ds1.actors;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import it.unitn.ds1.Configuration;
import it.unitn.ds1.Messages;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Random;

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
        return receiveBuilder().match(Messages.TopologyMessage.class, this::onTopologyMessage).match(Messages.ReadMessage.class, this::onReadMessage).match(Messages.WriteMessage.class, this::onWriteMessage).build();
    }

    /**
     * Process the write request
     **/
    private void onWriteMessage(Messages.WriteMessage msg) {
        //update our database
        database.put(msg.key, msg.value);
        System.out.format("[%s] | %s %n", getSelf().path().name(), msg.toString());
        System.out.flush();

        //send the update to all L1 caches
        for(ActorRef l1cache: l1Caches){
            l1cache.tell(new Messages.RefillMessage(msg.id, msg.key, msg.value), ActorRef.noSender());

        }

        //send back the message to the sender
        getSender().tell(new Messages.OperationResultMessage(msg.id, Messages.OperationResultMessage.Operation.Read, msg.key, database.get(msg.key)), getSelf());



    }

    /**
     * Process the read request
     **/
    private void onReadMessage(Messages.ReadMessage msg) {
        System.out.format("[%s] | %s %n", getSelf().path().name(), msg.toString());
        System.out.flush();
        getSender().tell(new Messages.OperationResultMessage(msg.id, Messages.OperationResultMessage.Operation.Read, msg.key, database.get(msg.key)), getSelf());
    }
}
