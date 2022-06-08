package it.unitn.ds1.common;

import akka.actor.ActorRef;

import java.util.ArrayList;
import java.util.UUID;

public class RemoveRequest {
    public final int key;
    public final int value;
    public final ActorRef issuer;
    public final ArrayList<UUID> requestsIds;
    public final UUID originalRequestId;

    public RemoveRequest(int key, int value, UUID originalRequestId, ActorRef issuer, ArrayList<UUID> requestsIds) {
        this.key = key;
        this.value = value;
        this.issuer = issuer;
        this.originalRequestId = originalRequestId;
        this.requestsIds = requestsIds;
    }
}
