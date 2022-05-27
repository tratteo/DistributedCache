package it.unitn.ds1;
import akka.actor.Actor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import com.typesafe.config.ConfigException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class CacheManager {
  final static int N_CLIENTS = 2;
  final static int N_L1 = 2;
  final static int N_L2 = 2;

  public static void main(String[] args) {

    // Create the actor system
    final ActorSystem system = ActorSystem.create("vssystem");


    ActorRef database = system.actorOf(DatabaseActor.props(),"database");
    List<ActorRef> l1Caches = new ArrayList<>();
    List<ActorRef> l2Caches = new ArrayList<>();
    for(int i = 0; i < N_L1; i++){
      ActorRef l1 = system.actorOf(CacheActor.props(CacheActor.Type.L1),"L1_"+i );
      l1Caches.add(l1);
      List<ActorRef> scopedCaches = new ArrayList<>();
      for (int j= 0; j < N_L2; j++){
        ActorRef l2 = system.actorOf(CacheActor.props(CacheActor.Type.L2),"L2_"+i+"."+j);
        l2.tell(new Messages.TopologyMessage(l1, null), ActorRef.noSender());
        scopedCaches.add(l2);
      }
      l1.tell(new Messages.TopologyMessage(database, scopedCaches), ActorRef.noSender());
      l2Caches.addAll(scopedCaches);
    }
    database.tell(new Messages.TopologyMessage(null, l1Caches), ActorRef.noSender());

    List<ActorRef> clients = new ArrayList<>();
    for(int i = 0; i < N_CLIENTS; i++){
      clients.add(system.actorOf(ClientActor.props(l2Caches)));
    }

    // system shutdown
    system.terminate();
  }

  public static void inputContinue() {
    try {
      System.out.println(">>> Press ENTER to continue <<<");
      System.in.read();
    }
    catch (IOException ignored) {}
  }
}
