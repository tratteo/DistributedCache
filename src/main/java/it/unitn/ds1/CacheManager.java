package it.unitn.ds1;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;

import java.io.IOException;


public class CacheManager {
  final static int N_CLIENTS = 2;
  final static int N_L1 = 2;
  final static int N_L2 = 2;

  public static void main(String[] args) {

    // Create the actor system
    final ActorSystem system = ActorSystem.create("vssystem");


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
