package com.example2;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;


public class ActorHierarchyExperiments {
    public static void main(String[] args) {
        final ActorRef<String> testSystem = ActorSystem.create(Main.create(), "testSystem");
        testSystem.tell("start");
        System.out.println("Main: " + testSystem);
    }
}