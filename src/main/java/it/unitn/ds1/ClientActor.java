package it.unitn.ds1;
import akka.actor.*;
import scala.concurrent.duration.Duration;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class ClientActor extends AbstractActor {
  private List<ActorRef> l2Caches;

  public ClientActor(List<ActorRef> l2Caches){
    this.l2Caches = l2Caches;
  }

  // Here we define the mapping between the received message types
  // and our actor methods
  @Override
  public Receive createReceive() {
    return receiveBuilder()
            .build();

  }
}
