package it.unitn.ds1;

import akka.actor.ActorSystem;
import akka.japi.Pair;
import it.unitn.ds1.test.Instruction;
import it.unitn.ds1.test.SystemInstance;
import it.unitn.ds1.test.TestConfiguration;
import it.unitn.ds1.test.Tester;

import java.io.IOException;
import java.util.Queue;


public class CacheManager {

    public static void main(String[] args) throws InterruptedException {

        // Execute the system with various test configurations. Hover the mouse over the enum name to read information about the configuration

        // Randomly evolves the system
        //executeConfiguration(TestConfiguration.Random);

        executeConfiguration(TestConfiguration.CriticalRead);
        executeConfiguration(TestConfiguration.CriticalWriteFailure);
        executeConfiguration(TestConfiguration.CriticalWriteSuccess);
        executeConfiguration(TestConfiguration.TopologyUpdates);
        executeConfiguration(TestConfiguration.MultipleCrashes);
        executeConfiguration(TestConfiguration.EventualConsistency);
    }

    private static void executeConfiguration(TestConfiguration configuration) throws InterruptedException {

        System.out.println("\nExecuting configuration " + configuration + "\n");
        System.out.flush();

        //Create ActorSystem and build the environment
        final ActorSystem system = ActorSystem.create("system");
        Pair<SystemInstance, Queue<Instruction>> instance = Tester.buildEnvironment(system, configuration);

        // Execute instructions
        for (Instruction instruction : instance.second()) {
            Thread.sleep(instruction.delay);
            instruction.execute();
        }

        inputContinue();
        system.terminate();
    }

    private static void inputContinue() {
        try {
            System.in.read();
        } catch (IOException ignored) {
        }
    }
}
