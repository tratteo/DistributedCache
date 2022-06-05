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

        CrashSynchronizationContext crashSynchronizationContext = new CrashSynchronizationContext();

        List<ActorRef> l1Caches = new ArrayList<>();
        List<ActorRef> l2Caches = new ArrayList<>();
        List<ActorRef> clients = new ArrayList<>();
        for (int i = 0; i < Configuration.N_CLIENTS; i++) {
            clients.add(system.actorOf(ClientActor.props(), "Client_" + i));
        }
        // Create caches
        for (int i = 0; i < Configuration.N_L1; i++) {
            ActorRef l1 = system.actorOf(CacheActor.props(crashSynchronizationContext), "L1_" + i);
            l1Caches.add(l1);
            List<ActorRef> scopedCaches = new ArrayList<>();
            Messages.ClientsMessage clientsMessage = new Messages.ClientsMessage(clients);
            for (int j = 0; j < Configuration.N_L2_L1; j++) {
                ActorRef l2 = system.actorOf(CacheActor.props(crashSynchronizationContext), "L2_" + i + "." + j);
                // Notify the L2 cache about its topology
                l2.tell(new Messages.TopologyMessage(l1, null, database), ActorRef.noSender());
                l2.tell(clientsMessage, ActorRef.noSender());
                scopedCaches.add(l2);
            }
            // Notify the L1 cache about its topology
            l1.tell(new Messages.TopologyMessage(database, scopedCaches, database), ActorRef.noSender());
            l2Caches.addAll(scopedCaches);
        }

        // Notify the database about the L1 caches
        database.tell(new Messages.TopologyMessage(null, l1Caches, database), ActorRef.noSender());

        // Give clients a reference to all the L2 caches
        Messages.TopologyMessage clientsTopology = new Messages.TopologyMessage(null, l2Caches, null);
        for (ActorRef c : clients) {
            c.tell(clientsTopology, ActorRef.noSender());
        }

        System.out.println("\nTOPOLOGY CREATED\n");
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
