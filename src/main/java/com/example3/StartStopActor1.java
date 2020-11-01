package com.example3;

import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

class StartStopActor1 extends AbstractBehavior<String> {

    static Behavior<String> create() {
        return Behaviors.setup(StartStopActor1::new);
    }

    private StartStopActor1(ActorContext<String> context) {
        super(context);
        System.out.println("first started");
        context.spawn(StartStopActor2.create(), "second");
    }

    @Override
    public Receive<String> createReceive() {
        return newReceiveBuilder()
                .onSignal(PostStop.class, signal -> onPostStop())  // stopped后会发送PostStop信号, 主要是控制不同生命周期的行为
                .onMessageEquals("stop", Behaviors::stopped)
                .build();
    }

    private Behavior<String> onPostStop() {
        System.out.println("first stopped");
        return this;
    }
}
