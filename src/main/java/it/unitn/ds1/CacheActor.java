package it.unitn.ds1;

import akka.actor.AbstractActor;
import akka.actor.Actor;
import akka.actor.ActorRef;
import akka.actor.Props;
import scala.concurrent.duration.Duration;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class CacheActor extends AbstractActor {
  public enum Type{
    L1,
    L2
  }

  private ActorRef parent;
  private List<ActorRef> children;
  private  Type type;
  public CacheActor(Type type, ActorRef parent, List<ActorRef> children){
    this.type = type;
    this.parent = parent;
    this.children= children;
  }
  // Here we define the mapping between the received message types
  // and our actor methods
  @Override
  public Receive createReceive() {
    return receiveBuilder()
            .build();
  }
}
