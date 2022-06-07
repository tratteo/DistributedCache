package it.unitn.ds1.test;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import it.unitn.ds1.actors.CacheActor;
import it.unitn.ds1.actors.ClientActor;
import it.unitn.ds1.actors.DatabaseActor;
import it.unitn.ds1.utils.CrashSynchronizationContext;
import it.unitn.ds1.utils.Messages;
import it.unitn.ds1.utils.enums.CacheProtocolStage;
import javafx.util.Pair;

import java.util.*;
import java.util.stream.Collectors;

public class Tester {
    private static Queue<Instruction> getInstructionSet(TestConfiguration configuration, SystemInstance topology) {
        ArrayDeque<Instruction> instructions = new ArrayDeque<>();
        Random random = new Random(System.nanoTime());
        switch (configuration) {
            case Random: {
            }
            break;
            case CriticalRead: {
                ActorRef client = topology.clients.get(random.nextInt(topology.clients.size()));
                SystemInstance.TopologyActor l2Cache = topology.l2Caches.get(random.nextInt(topology.l2Caches.size()));
                instructions.add(new Instruction(client, ClientActor.OperationNotifyMessage.Read(l2Cache.self, true)));
                instructions.add(new Instruction(l2Cache.parent, new CacheActor.CrashMessage(CacheProtocolStage.Read)));
            }
            break;
            case CriticalWriteFailure: {
                ActorRef client = topology.clients.get(random.nextInt(topology.clients.size()));
                SystemInstance.TopologyActor l2Cache = topology.l2Caches.get(0);
                SystemInstance.TopologyActor l2CacheRead = topology.l2Caches.get(1);
                int key = DatabaseActor.getRandomKey();
                int value = DatabaseActor.getRandomValue();
                instructions.add(new Instruction(client, ClientActor.OperationNotifyMessage.Write(l2Cache.self, true, key, value)));
                instructions.add(new Instruction(l2Cache.parent, new CacheActor.CrashMessage(CacheProtocolStage.Remove)));
                instructions.add(new Instruction(client, ClientActor.OperationNotifyMessage.Read(l2CacheRead.self, false, key), 4000));
            }
            break;

            case CriticalWriteSuccess: {
                ActorRef client = topology.clients.get(0);
                ActorRef client1 = topology.clients.get(1);
                SystemInstance.TopologyActor l2Cache = topology.l2Caches.get(0);
                SystemInstance.TopologyActor l2CacheRead = topology.l2Caches.get(1);
                int key = DatabaseActor.getRandomKey();
                int value = DatabaseActor.getRandomValue();
                instructions.add(new Instruction(client, ClientActor.OperationNotifyMessage.Write(l2Cache.self, true, key, value)));
                instructions.add(new Instruction(client1, ClientActor.OperationNotifyMessage.Read(l2CacheRead.self, false, key), 500));
            }
            break;
            case Read: {
                // TODO create instruction set for a generic read with a crash
            }
            break;
            case Write: {
                // TODO create instruction set for a generic write with a crash
            }
            break;
        }
        return instructions;
    }

    private static SystemTopologyDescriptor getTopologyDescriptor(TestConfiguration configuration) {
        switch (configuration) {
            case Random:
                return new SystemTopologyDescriptor(2, 2, 2);
            case CriticalRead:
                return new SystemTopologyDescriptor(2, 2, 2);
            case CriticalWriteFailure:
                return new SystemTopologyDescriptor(2, 2, 2);
            case CriticalWriteSuccess:
                return new SystemTopologyDescriptor(2, 2, 2);
            case Read:
                return new SystemTopologyDescriptor(2, 2, 2);
            case Write:
                return new SystemTopologyDescriptor(2, 2, 2);
            default:
                return new SystemTopologyDescriptor(2, 2, 2);
        }
    }

    public static Pair<SystemInstance, Queue<Instruction>> buildEnvironment(ActorSystem system, TestConfiguration configuration) {

        boolean isManaged = !configuration.equals(TestConfiguration.Random);

        SystemTopologyDescriptor topologyDescriptor = getTopologyDescriptor(configuration);

        ActorRef database = system.actorOf(DatabaseActor.props(), "Database");
        CrashSynchronizationContext crashSynchronizationContext = new CrashSynchronizationContext();

        List<ActorRef> clients = new ArrayList<>();
        List<SystemInstance.TopologyActor> l1CachesActors = new ArrayList<>();
        List<SystemInstance.TopologyActor> l2CachesActors = new ArrayList<>();
        for (int i = 0; i < topologyDescriptor.n_clients; i++) {
            clients.add(system.actorOf(ClientActor.props(isManaged), "Client_" + i));
        }

        // Create caches
        for (int i = 0; i < topologyDescriptor.n_l1; i++) {
            ActorRef l1 = system.actorOf(CacheActor.props(isManaged, crashSynchronizationContext), "L1_" + i);
            List<ActorRef> scopedCaches = new ArrayList<>();
            Messages.ClientsMessage clientsMessage = new Messages.ClientsMessage(clients);
            for (int j = 0; j < topologyDescriptor.n_l2_l1; j++) {
                ActorRef l2 = system.actorOf(CacheActor.props(isManaged, crashSynchronizationContext), "L2_" + i + "." + j);
                // Notify the L2 cache about its topology
                l2.tell(new Messages.TopologyMessage(l1, null, database), ActorRef.noSender());
                l2.tell(clientsMessage, ActorRef.noSender());
                l2CachesActors.add(new SystemInstance.TopologyActor(l2, l1, null));
                scopedCaches.add(l2);
            }
            // Notify the L1 cache about its topology
            l1.tell(new Messages.TopologyMessage(database, scopedCaches, database), ActorRef.noSender());
            l1CachesActors.add(new SystemInstance.TopologyActor(l1, database, scopedCaches));
        }

        // Notify the database about the L1 caches
        database.tell(new Messages.TopologyMessage(null, l1CachesActors.stream().map(a -> a.self).collect(Collectors.toList()), database), ActorRef.noSender());

        // Give clients a reference to all the L2 caches
        Messages.TopologyMessage clientsTopology = new Messages.TopologyMessage(null, l2CachesActors.stream().map(a -> a.self).collect(Collectors.toList()), null);
        for (ActorRef c : clients) {
            c.tell(clientsTopology, ActorRef.noSender());
        }

        SystemInstance.TopologyActor databaseTopologyActor = new SystemInstance.TopologyActor(database, null, l1CachesActors.stream().map(a -> a.self).collect(Collectors.toList()));
        SystemInstance instance = new SystemInstance(topologyDescriptor, databaseTopologyActor, clients, l1CachesActors, l2CachesActors);
        Queue<Instruction> instructions = getInstructionSet(configuration, instance);
        return new Pair<>(instance, instructions);
    }

}
