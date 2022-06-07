package it.unitn.ds1;

import akka.actor.ActorSystem;
import it.unitn.ds1.test.Instruction;
import it.unitn.ds1.test.SystemInstance;
import it.unitn.ds1.test.TestConfiguration;
import it.unitn.ds1.test.Tester;
import javafx.util.Pair;

import java.io.IOException;
import java.util.Queue;


public class CacheManager {

    public static void main(String[] args) throws InterruptedException {
        executeConfiguration(TestConfiguration.Random);
    }

    private static void executeConfiguration(TestConfiguration configuration) throws InterruptedException {

        System.out.println("\nExecuting configuration " + configuration + "\n");
        System.out.flush();

        //Create ActorSystem and build the environment
        final ActorSystem system = ActorSystem.create("system");
        Pair<SystemInstance, Queue<Instruction>> instance = Tester.buildEnvironment(system, configuration);

        // Execute instructions
        for (Instruction instruction : instance.getValue()) {
            Thread.sleep(instruction.delay);
            instruction.execute();
        }

        inputContinue();
        system.terminate();
    }

    private static void inputContinue() {
        try {
            System.out.println("\n>>> Press ENTER to exit <<<");
            System.in.read();
        } catch (IOException ignored) {
        }
    }
}
