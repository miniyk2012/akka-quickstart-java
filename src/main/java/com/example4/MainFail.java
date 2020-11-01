package com.example4;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;

public class MainFail {
    public static void main(String[] args) {
        ActorRef<String> first = ActorSystem.create(SupervisingActor.create(), "supervising-actor");
        first.tell("failChild");
    }
}
