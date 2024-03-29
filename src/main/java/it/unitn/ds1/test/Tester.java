package it.unitn.ds1.test;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.japi.Pair;
import it.unitn.ds1.actors.CacheActor;
import it.unitn.ds1.actors.ClientActor;
import it.unitn.ds1.actors.DatabaseActor;
import it.unitn.ds1.common.Configuration;
import it.unitn.ds1.common.CrashSynchronizationContext;
import it.unitn.ds1.common.Messages;
import it.unitn.ds1.enums.CacheProtocolStage;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Tester {
    private static Queue<Instruction> getInstructionSet(TestConfiguration configuration, SystemInstance topology) {
        ArrayDeque<Instruction> instructions = new ArrayDeque<>();
        Random random = new Random(System.nanoTime());
        switch (configuration) {
            case Random: {
            }
            break;
            case TopologyUpdates: {
                ActorRef client = topology.clients.get(random.nextInt(topology.clients.size()));
                SystemInstance.TopologyActor l2Cache0 = topology.l2Caches.get(0);
                SystemInstance.TopologyActor l2Cache1 = topology.l2Caches.get(1);

                instructions.add(new Instruction(client, ClientActor.OperationNotifyMessage.Read(l2Cache0.self, false)));
                instructions.add(new Instruction(l2Cache0.parent, new CacheActor.CrashMessage(CacheProtocolStage.Read, Configuration.CLIENT_TIMEOUT)));
                instructions.add(new Instruction(client, ClientActor.OperationNotifyMessage.Read(l2Cache0.self, false), 0));
                instructions.add(new Instruction(client, ClientActor.OperationNotifyMessage.Read(l2Cache0.self, false), Configuration.CLIENT_TIMEOUT + 500));

                int key = DatabaseActor.getRandomKey();

                instructions.add(new Instruction(client, ClientActor.OperationNotifyMessage.Read(l2Cache1.self, false, key)));
                instructions.add(new Instruction(l2Cache0.self, new CacheActor.CrashMessage(CacheProtocolStage.Read, Configuration.CLIENT_TIMEOUT * 2), Configuration.TIMEOUT));
                instructions.add(new Instruction(client, ClientActor.OperationNotifyMessage.Read(l2Cache0.self, false, key)));
                instructions.add(new Instruction(client, ClientActor.OperationNotifyMessage.Read(l2Cache0.self, false, key), Configuration.CLIENT_TIMEOUT * 3));

            }
            break;
            case CriticalRead: {
                ActorRef client = topology.clients.get(random.nextInt(topology.clients.size()));
                SystemInstance.TopologyActor l2Cache = topology.l2Caches.get(random.nextInt(topology.l2Caches.size()));
                instructions.add(new Instruction(client, ClientActor.OperationNotifyMessage.Read(l2Cache.self, false)));
                instructions.add(new Instruction(client, ClientActor.OperationNotifyMessage.Read(l2Cache.self, true), 500));
                instructions.add(new Instruction(l2Cache.parent, new CacheActor.CrashMessage(CacheProtocolStage.Result, Configuration.CLIENT_TIMEOUT)));
            }
            break;
            case CriticalWriteFailure: {
                // Done
                ActorRef client = topology.clients.get(random.nextInt(topology.clients.size()));
                SystemInstance.TopologyActor l1 = topology.l1Caches.get(0);
                SystemInstance.TopologyActor l2Cache = topology.l2Caches.get(0);
                SystemInstance.TopologyActor l2CacheRead = topology.l2Caches.get(1);
                int key = DatabaseActor.getRandomKey();
                int value = DatabaseActor.getRandomValue();
                instructions.add(new Instruction(client, ClientActor.OperationNotifyMessage.Read(l2Cache.self, true, key)));
                instructions.add(new Instruction(l1.self, new CacheActor.CrashMessage(CacheProtocolStage.Remove, Configuration.TIMEOUT * 5)));
                instructions.add(new Instruction(client, ClientActor.OperationNotifyMessage.Write(l2Cache.self, true, key, value)));
                instructions.add(new Instruction(client, ClientActor.OperationNotifyMessage.Read(l2CacheRead.self, false, key), Configuration.TIMEOUT * 2));
            }
            break;
            case CriticalWriteSuccess: {
                // Done
                ActorRef client = topology.clients.get(0);
                ActorRef client1 = topology.clients.get(1);
                SystemInstance.TopologyActor l2Cache = topology.l2Caches.get(0);
                SystemInstance.TopologyActor l2CacheRead = topology.l2Caches.get(1);
                int key = DatabaseActor.getRandomKey();
                instructions.add(new Instruction(client1, ClientActor.OperationNotifyMessage.Read(l2CacheRead.self, true, key)));
                instructions.add(new Instruction(client1, ClientActor.OperationNotifyMessage.Write(l2CacheRead.self, false, key, DatabaseActor.getRandomValue())));
                instructions.add(new Instruction(client, ClientActor.OperationNotifyMessage.Write(l2Cache.self, true, key, DatabaseActor.getRandomValue()), 1000));
                instructions.add(new Instruction(client1, ClientActor.OperationNotifyMessage.Read(l2CacheRead.self, false, key), 500));
            }
            break;
            case EventualConsistency: {
                // Done
                ActorRef client = topology.clients.get(1);
                SystemInstance.TopologyActor firstL1 = topology.l1Caches.get(0);
                ActorRef l2 = firstL1.children.get(0);
                int key = DatabaseActor.getRandomKey();
                // client write with crash
                instructions.add(new Instruction(client, ClientActor.OperationNotifyMessage.Read(l2, false, key)));
                instructions.add(new Instruction(firstL1.self, new CacheActor.CrashMessage(CacheProtocolStage.Refill, Configuration.EVICT_TIME)));
                instructions.add(new Instruction(client, ClientActor.OperationNotifyMessage.Write(l2, false, key, DatabaseActor.getRandomValue()), 500));
                instructions.add(new Instruction(client, ClientActor.OperationNotifyMessage.Read(l2, false, key), 500));
                instructions.add(new Instruction(client, ClientActor.OperationNotifyMessage.Read(l2, false, key), (int) (Configuration.EVICT_TIME * 1.5)));
            }
            break;
            case MultipleCrashes: {
                // Done
                ActorRef client = topology.clients.get(0);
                SystemInstance.TopologyActor l2 = topology.l2Caches.get(0);

                int key = DatabaseActor.getRandomKey();
                instructions.add(new Instruction(l2.self, new CacheActor.CrashMessage(CacheProtocolStage.Read, Configuration.CLIENT_TIMEOUT * 5)));
                instructions.add(new Instruction(client, ClientActor.OperationNotifyMessage.Read(l2.self, false, key)));

                instructions.add(new Instruction(client, ClientActor.OperationNotifyMessage.Write(l2.self, false, key, DatabaseActor.getRandomValue())));
                instructions.add(new Instruction(l2.parent, new CacheActor.CrashMessage(CacheProtocolStage.Refill, Configuration.CLIENT_TIMEOUT * 5)));

                instructions.add(new Instruction(client, ClientActor.OperationNotifyMessage.Read(l2.self, false), 1000));
            }
            break;
            case SequentialConsistency: {
                topology.database.self.tell(new DatabaseActor.DropDatabaseMessage(), ActorRef.noSender());
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                int operationsNumber = 1000;
                List<Integer> databaseValues = IntStream.rangeClosed(1, operationsNumber + 1).boxed().collect(Collectors.toList());
                //Collections.shuffle(databaseValues);

                for (int i = 0; i < operationsNumber; i++) {
                    ActorRef randomClient = topology.clients.get(random.nextInt(topology.clients.size()));
                    instructions.add(new Instruction(randomClient, getRandomCriticalClientOperation(random, topology, DatabaseActor.getRandomKey(), databaseValues.get(i))));
                }
            }
            break;
        }
        return instructions;
    }

    private static ClientActor.OperationNotifyMessage getRandomCriticalClientOperation(Random random, SystemInstance system, int key, int value) {
        ActorRef randomCache = system.l2Caches.get(random.nextInt(system.l2Caches.size())).self;
        boolean write = random.nextDouble() < Configuration.P_WRITE;
        if (write) {
            return ClientActor.OperationNotifyMessage.Write(randomCache, true, key, value).ExecuteDelay(random.nextInt(2000));
        }
        else {
            //.ExecuteDelay(random.nextInt(5000))
            return ClientActor.OperationNotifyMessage.Read(randomCache, false, key).ExecuteDelay(random.nextInt(2000));
        }
    }

    private static SystemTopologyDescriptor getTopologyDescriptor(TestConfiguration configuration) {
        switch (configuration) {
            case TopologyUpdates:
                return new SystemTopologyDescriptor(1, 1, 2);
            case MultipleCrashes:
                return new SystemTopologyDescriptor(1, 1, 2);
            case SequentialConsistency:
                return new SystemTopologyDescriptor(3, 3, 2);
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
