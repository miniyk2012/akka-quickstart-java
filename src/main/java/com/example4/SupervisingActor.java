package com.example4;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

public class SupervisingActor extends AbstractBehavior<String> {
    public SupervisingActor(ActorContext<String> context) {
        super(context);
        child = context.spawn(
                Behaviors.supervise(SupervisedActor.create()).onFailure(SupervisorStrategy.restart()),
                "supervised-actor");
    }
    static Behavior<String> create() {
        return Behaviors.setup(SupervisingActor::new);
    }

    private final ActorRef<String> child;

    @Override
    public Receive<String> createReceive() {
        return newReceiveBuilder().onMessageEquals("failChild", this::onFailChild).build();
    }

    private Behavior<String> onFailChild() {
        child.tell("fail");
        return this;
    }
}
