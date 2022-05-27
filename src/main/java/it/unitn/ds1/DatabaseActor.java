package it.unitn.ds1;

import akka.actor.AbstractActor;
import akka.actor.Actor;
import akka.actor.ActorRef;
import akka.actor.Props;
import scala.concurrent.duration.Duration;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class DatabaseActor extends AbstractActor {

  private List<ActorRef> l1Caches;
  private Dictionary<Integer, Integer> database;
  /*-- Actor constructors --------------------------------------------------- */
  public DatabaseActor(List<ActorRef> l1Caches) {
    this.l1Caches = l1Caches;
    populateDatabase();
  }
  private void populateDatabase(){
    database = new Hashtable<>();
    for(int i = 0; i < 10; i++){
      database.put(i, i);
    }
  }

  static public Props props(List<ActorRef> l1Caches) {
    return Props.create(DatabaseActor.class, ()->new DatabaseActor(l1Caches));
  }

//  private int multicast(Serializable m, Set<ActorRef> multicastGroup) {
//    int i = 0;
//    for (ActorRef r: multicastGroup) {
//
//      // check if the node should crash
//      if(m.getClass().getSimpleName().equals(nextCrash.name())) {
//        if (i >= nextCrashAfter) {
//          //System.out.println(getSelf().path().name() + " CRASH after " + i + " " + nextCrash.name());
//          break;
//        }
//      }
//
//      // send m to r (except to self)
//      if (!r.equals(getSelf())) {
//
//        // model a random network/processing delay
//        try { Thread.sleep(rnd.nextInt(10)); }
//        catch (InterruptedException e) { e.printStackTrace(); }
//
//        r.tell(m, getSelf());
//        i++;
//      }
//    }
//
//    return i;
//  }


  // Here we define the mapping between the received message types
  // and our actor methods
  @Override
  public Receive createReceive() {
    return receiveBuilder()
            .build();

  }
}
