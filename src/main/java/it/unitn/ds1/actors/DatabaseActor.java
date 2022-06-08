package it.unitn.ds1.actors;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import com.google.gson.Gson;
import it.unitn.ds1.common.Configuration;
import it.unitn.ds1.common.Messages;
import it.unitn.ds1.common.RemoveRequest;
import it.unitn.ds1.enums.Operation;

import java.io.Serializable;
import java.util.*;

public class DatabaseActor extends AgentActor {
    private static final int DATABASE_KEYS = 15;
    private final List<ActorRef> l1Caches;
    private final ArrayList<RemoveRequest> removeRequests;
    private Map<Integer, Integer> databaseKeys;

    public DatabaseActor() {
        super(false);
        l1Caches = new ArrayList<>();
        removeRequests = new ArrayList<>();
        populateDatabase();
    }

    static public Props props() {
        return Props.create(DatabaseActor.class, DatabaseActor::new);
    }

    public static int getRandomKey() {
        return new Random(System.nanoTime()).nextInt(DATABASE_KEYS);
    }

    public static int getRandomValue() {
        return new Random(System.nanoTime()).nextInt(1000);
    }

    @Override
    protected void onAck(UUID ackId) {
        ArrayList<RemoveRequest> toRemove = new ArrayList<>();
        for (RemoveRequest request : removeRequests) {
            request.requestsIds.remove(ackId);
            if (request.requestsIds.size() <= 0) {
                databaseKeys.put(request.key, request.value);
                databaseSnapshot();
                toRemove.add(request);
                printFormatted("REMOVE protocol {%s} completed, propagating result", request.originalRequestId);
                // All acks have been received, send the refill and result
                Serializable refillMsg = new Messages.RefillMessage(request.key, request.value);
                //Send the update to all L1 caches
                for (ActorRef l1cache : l1Caches) {
                    l1cache.tell(refillMsg, getSelf());
                }
                request.issuer.tell(Messages.OperationResultMessage.Success(request.originalRequestId, Operation.Write, request.key, request.value), getSelf());
            }
        }
        removeRequests.removeAll(toRemove);
    }

    @Override
    protected void onTimeout(Messages.IdentifiableMessage msg, ActorRef dest) {
        // If the timeout is on an ack for the remove request, re-send it
        Optional<RemoveRequest> optRequest = removeRequests.stream().filter(r -> r.requestsIds.contains(msg.id)).findFirst();
        // This happened because a cache has crashed during the remove protocol, respond with error
        optRequest.ifPresent(removeRequest -> {
            printFormatted("Timeout for %s, aborting REMOVE protocol", dest.path().name());
            removeRequests.remove(removeRequest);
            removeRequest.issuer.tell(Messages.OperationResultMessage.Error(removeRequest.originalRequestId, Operation.Write, removeRequest.key, String.format("Cache %s timed out during the REMOVE protocol", dest.path().name())),
                                      getSelf());
        });
    }

    /**
     * Populate the database with some dummy totally random elements :)
     **/
    private void populateDatabase() {
        databaseKeys = new Hashtable<>();
        Random random = new Random();
        for (int i = 0; i < DATABASE_KEYS; i++) {
            databaseKeys.put(i, random.nextInt(1000));
        }
    }

    private void onTopologyMessage(Messages.TopologyMessage message) {

        for (ActorRef a : message.children) {
            if (!l1Caches.contains(a)) {
                printFormatted("Topology update :D - adding %s as child", a.path().name());
                l1Caches.add(a);
            }
        }
        if (message.parent != null) {
            if (l1Caches.remove(message.parent)) {
                printFormatted("Topology update :D - removing %s as child", message.parent.path().name());
            }
        }
    }

    //region Message Handlers

    private void onWriteMessage(Messages.WriteMessage msg) {
        printFormatted("%s", msg);
        ActorRef issuer = getSender();
        issuer.tell(new Messages.AckMessage(msg.id), getSelf());
        //If the write operation is critical, then it is necessary to ensure that before updating no cache holds old values of item
        //so, we have to remove the item with old value from every cache before refill
        if (msg.isCritical) {
            ArrayList<UUID> requestsIds = new ArrayList<>();
            printFormatted("Critical WRITE received, initiating REMOVE protocol");
            for (ActorRef l1cache : l1Caches) {
                UUID id = UUID.randomUUID();
                requestsIds.add(id);
                sendWithTimeout(new Messages.RemoveMessage(id, msg.key), l1cache, Configuration.DB_TIMEOUT);
            }
            removeRequests.add(new RemoveRequest(msg.key, msg.value, msg.id, issuer, requestsIds));
        }
        else {
            databaseKeys.put(msg.key, msg.value);
            databaseSnapshot();
            issuer.tell(Messages.OperationResultMessage.Success(msg.id, Operation.Write, msg.key, databaseKeys.get(msg.key)), getSelf());
            Serializable refillMsg = new Messages.RefillMessage(msg.key, msg.value);
            //Send the update to all L1 caches
            for (ActorRef l1cache : l1Caches) {
                l1cache.tell(refillMsg, ActorRef.noSender());
            }
        }
    }

    private void onReadMessage(Messages.ReadMessage msg) {
        //operation is the same in both normal and critical version
        printFormatted("%s", msg);
        getSender().tell(new Messages.AckMessage(msg.id), getSelf());
        getSender().tell(Messages.OperationResultMessage.Success(msg.id, Operation.Read, msg.key, databaseKeys.get(msg.key)), getSelf());
    }

    private void databaseSnapshot() {
        Gson gson = new Gson();
        String json = gson.toJson(databaseKeys);
        printFormatted("Snapshot %s", json);
    }

    @Override
    public ReceiveBuilder receiveBuilderFactory() {
        return receiveBuilder().match(Messages.TopologyMessage.class, this::onTopologyMessage).match(Messages.ReadMessage.class, this::onReadMessage).match(Messages.WriteMessage.class, this::onWriteMessage);
    }

    //endregion

}
