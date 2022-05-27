package it.unitn.ds1;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;

import java.util.*;

public class CacheActor extends AbstractActor {
  public enum Type{
    L1,
    L2
  }

  private ActorRef parent;
  private List<ActorRef> children;
  private Hashtable<Integer, Integer> cache;
  private  Type type;
  public CacheActor(Type type){
    this.type = type;
    cache = new Hashtable<>();
  }

  static public Props props(Type type) {
    return Props.create(CacheActor.class, ()->new CacheActor(type));
  }

  private void onTopologyMessage(Messages.TopologyMessage message){
    System.out.println(getSelf().path().name()+" received topology");
    this.parent = message.parent;
    this.children = message.children;
  }

  // Here we define the mapping between the received message types
  // and our actor methods
  @Override
  public Receive createReceive() {
    return receiveBuilder()
            .match(Messages.TopologyMessage.class, this::onTopologyMessage)
            .match(Messages.ReadMessage.class, this::onReadMessage)
            .match(Messages.OperationResultMessage.class, this::onOperationResultMessage)
            .build();

  }

  private void onOperationResultMessage(Messages.OperationResultMessage msg) {
      msg.client.tell(msg, getSelf());
      cache.put(msg.key, msg.value);
      System.out.println(getSelf().path().name()+" updated key "+msg.key);
  }

  private void onReadMessage(Messages.ReadMessage msg) {
    if(cache.containsKey(msg.key)){
      Messages.OperationResultMessage res = new Messages.OperationResultMessage(msg.key, getSender(), cache.get(msg.key));
      getSender().tell(res, getSelf());
      System.out.println(getSelf().path().name()+" cache hit for key "+msg.key);
    }
    else {
      parent.tell(msg, getSelf());
      System.out.println(getSelf().path().name()+" cache miss for key "+msg.key);
    }

  }
}
