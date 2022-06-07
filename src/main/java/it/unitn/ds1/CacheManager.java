package it.unitn.ds1;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import it.unitn.ds1.actors.CacheActor;
import it.unitn.ds1.actors.ClientActor;
import it.unitn.ds1.actors.DatabaseActor;
import it.unitn.ds1.instructions.Instruction;
import it.unitn.ds1.utils.Configuration;
import it.unitn.ds1.utils.CrashSynchronizationContext;
import it.unitn.ds1.utils.Messages;
import it.unitn.ds1.utils.enums.SystemConfiguration;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;


public class CacheManager {

    public static void main(String[] args) throws InterruptedException {
        ActorSystem system = executeConfiguration(SystemConfiguration.Random);
        inputContinue();
        system.terminate();
    }

    private static ActorSystem executeConfiguration(SystemConfiguration configuration) throws InterruptedException {
        Queue<Instruction> systemInstructions = getInstructionSet(configuration);
        boolean managedSystem = systemInstructions.size() > 0;
        System.out.println("\nExecuting configuration " + configuration + "\n");
        System.out.flush();
        // Create the actor system
        final ActorSystem system = ActorSystem.create("dcsystem");
        // Create the database actor
        ActorRef database = system.actorOf(DatabaseActor.props(), "Database");
        CrashSynchronizationContext crashSynchronizationContext = new CrashSynchronizationContext();

        List<ActorRef> l1Caches = new ArrayList<>();
        List<ActorRef> l2Caches = new ArrayList<>();
        List<ActorRef> clients = new ArrayList<>();
        for (int i = 0; i < Configuration.N_CLIENTS; i++) {
            clients.add(system.actorOf(ClientActor.props(managedSystem), "Client_" + i));
        }
        // Create caches
        for (int i = 0; i < Configuration.N_L1; i++) {
            ActorRef l1 = system.actorOf(CacheActor.props(managedSystem, crashSynchronizationContext), "L1_" + i);
            l1Caches.add(l1);
            List<ActorRef> scopedCaches = new ArrayList<>();
            Messages.ClientsMessage clientsMessage = new Messages.ClientsMessage(clients);
            for (int j = 0; j < Configuration.N_L2_L1; j++) {
                ActorRef l2 = system.actorOf(CacheActor.props(managedSystem, crashSynchronizationContext), "L2_" + i + "." + j);
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

        // Execute instructions
        for (Instruction instruction : systemInstructions) {
            Thread.sleep(instruction.delay);
            instruction.execute();
        }
        return system;
    }

    private static Queue<Instruction> getInstructionSet(SystemConfiguration configuration) {
        switch (configuration) {
            case Random:
                return new ArrayDeque<>();
        }
        return new ArrayDeque<>();
    }

    private static void inputContinue() {
        try {
            System.out.println(">>> Press ENTER to continue <<<");
            System.in.read();
        } catch (IOException ignored) {
        }
    }
}
