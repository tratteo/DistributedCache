package it.unitn.ds1;
import akka.actor.*;
import scala.concurrent.duration.Duration;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class ClientActor extends AbstractActor {
  private List<ActorRef> l2Caches;
  private Random random;
  public ClientActor(List<ActorRef> l2Caches){
    this.l2Caches = l2Caches;
    random = new Random();
  }

  @Override
  public void preStart() throws Exception {
    // Read scheduler
    Cancellable cancellable = getContext().system().scheduler().scheduleOnce(
            Duration.create(random.nextInt(5000) + 300, TimeUnit.MILLISECONDS),
            getSelf(),
            new OperationMessage(),
            getContext().system().dispatcher(),
            ActorRef.noSender()
    );
  }

  private ActorRef getRandomL2Cache(){
    return l2Caches.get(random.nextInt(l2Caches.size()));
  }


  static public Props props(List<ActorRef> l2Caches) {
    return Props.create(ClientActor.class, ()->new ClientActor(l2Caches));
  }

    public static class OperationMessage implements Serializable {
        public OperationMessage() {
        }
    }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
            .match(OperationMessage.class, this::onReadOperationMessage)
            .build();

  }

    private void onReadOperationMessage(OperationMessage message) {

        ActorRef cache = getRandomL2Cache();
        cache.tell(new Messages.ReadMessage(random.nextInt(DatabaseActor.KEYS), getSelf(), false), getSelf());

      Cancellable cancellable = getContext().system().scheduler().scheduleOnce(
              Duration.create(random.nextInt(5000)+300, TimeUnit.MILLISECONDS),
              getSelf(),
              new OperationMessage(),
              getContext().system().dispatcher(),
              ActorRef.noSender()
      );
    }
}
