package com.iot.devices;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

import java.util.Set;

public class DeviceManager extends AbstractBehavior<DeviceManager.Command> {


    public interface Command {
    }

    // 注册消息
    public static final class RequestTrackDevice implements DeviceManager.Command, DeviceGroup.Command {
        public final String groupId;
        public final String deviceId;
        public final ActorRef<DeviceRegistered> replyTo;

        public RequestTrackDevice(String groupId, String deviceId, ActorRef<DeviceRegistered> replyTo) {
            this.groupId = groupId;
            this.deviceId = deviceId;
            this.replyTo = replyTo;
        }
    }

    // 回复注册消息
    public static final class DeviceRegistered {
        // 注册的设备Actor引用
        public final ActorRef<Device.Command> device;

        public DeviceRegistered(ActorRef<Device.Command> device) {
            this.device = device;
        }
    }

    // 询问某个Group有多少Device
    public static final class RequestDeviceList implements DeviceManager.Command, DeviceGroup.Command {
        final long requestId;
        final String groupId;
        final ActorRef<ReplyDeviceList> replyTo;

        public RequestDeviceList(long requestId, String groupId, ActorRef<ReplyDeviceList> replyTo) {
            this.requestId = requestId;
            this.groupId = groupId;
            this.replyTo = replyTo;
        }
    }

    // 回复某个Group有多少Device
    public static final class ReplyDeviceList {
        final long requestId;
        final Set<String> ids;  // deviceId列表

        public ReplyDeviceList(long requestId, Set<String> ids) {
            this.requestId = requestId;
            this.ids = ids;
        }
    }


    public DeviceManager(ActorContext<Command> context) {
        super(context);
    }

    @Override
    public Receive<Command> createReceive() {
        return null;
    }

}
