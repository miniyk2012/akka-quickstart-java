package com.example3;

import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

public class StartStopActor2 extends AbstractBehavior<String> {
    static Behavior<String> create() {
        return Behaviors.setup(StartStopActor2::new);
    }

    private StartStopActor2(ActorContext<String> context) {
        super(context);
        System.out.println("second started");
    }

    @Override
    public Receive<String> createReceive() {
        // parent actor一旦要stop, 会优先让子actor stop
        // actor的postStop()钩子都将在它们的父actor postStop()调用之前被调用
        return newReceiveBuilder().onSignal(PostStop.class, signal -> onPostStop()).build();
    }

    private Behavior<String> onPostStop() {
        System.out.println("second stopped");
        return this;
    }
}
