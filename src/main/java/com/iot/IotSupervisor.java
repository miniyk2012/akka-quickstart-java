package com.iot;

import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

// Void说明不需要接收任何消息
public class IotSupervisor extends AbstractBehavior<Void> {
    public static Behavior<Void> create() {
        return Behaviors.setup(IotSupervisor::new);
    }

    private IotSupervisor(ActorContext<Void> context) {
        super(context);
        context.getLog().info("IoT Application started");
    }

    // No need to handle any messages
    @Override
    public Receive<Void> createReceive() {
        return newReceiveBuilder().onSignal(PostStop.class, signal -> onPostStop()).build();
    }

    private IotSupervisor onPostStop() {
        getContext().getLog().info("IoT Application stopped");
        return this;
    }
}
