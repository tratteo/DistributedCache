package it.unitn.ds1;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import it.unitn.ds1.actors.CacheActor;
import it.unitn.ds1.actors.ClientActor;
import it.unitn.ds1.actors.DatabaseActor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class CacheManager {

    public static void main(String[] args) {

        // Create the actor system
        final ActorSystem system = ActorSystem.create("dcsystem");

        // Create the database actor
        ActorRef database = system.actorOf(DatabaseActor.props(), "Database");

        List<ActorRef> l1Caches = new ArrayList<>();
        List<ActorRef> l2Caches = new ArrayList<>();

        // Create caches
        for (int i = 0; i < Configuration.N_L1; i++) {
            ActorRef l1 = system.actorOf(CacheActor.props(), "L1_" + i);
            l1Caches.add(l1);
            List<ActorRef> scopedCaches = new ArrayList<>();
            for (int j = 0; j < Configuration.N_L2_L1; j++) {
                ActorRef l2 = system.actorOf(CacheActor.props(), "L2_" + i + "." + j);
                // Notify the L2 cache about its topology
                l2.tell(new Messages.TopologyMessage(l1, null, database), ActorRef.noSender());
                scopedCaches.add(l2);
            }
            // Notify the L1 cache about its topology
            l1.tell(new Messages.TopologyMessage(database, scopedCaches, database), ActorRef.noSender());
            l2Caches.addAll(scopedCaches);
        }

        // Notify the database about the L1 caches
        database.tell(new Messages.TopologyMessage(null, l1Caches, database), ActorRef.noSender());

        // Create clients with a reference to all the L2 caches
        List<ActorRef> clients = new ArrayList<>();
        for (int i = 0; i < Configuration.N_CLIENTS; i++) {
            clients.add(system.actorOf(ClientActor.props(l2Caches, database), "Client_" + i));
        }
        System.out.println("Topology created");
        System.out.flush();
        // Wait for termination
        inputContinue();
        system.terminate();
    }

    public static void inputContinue() {
        try {
            System.out.println(">>> Press ENTER to continue <<<");
            System.in.read();
        } catch (IOException ignored) {
        }
    }
}
