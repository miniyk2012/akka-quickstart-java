package com.iot.devices;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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
    // 终止Group的消息, 无须回复
    private static class DeviceGroupTerminated implements DeviceManager.Command {
        public final String groupId;

        DeviceGroupTerminated(String groupId) {
            this.groupId = groupId;
        }
    }

    // 根据groupId请求devices的温度
    public static final class RequestAllTemperatures implements DeviceGroupQuery.Command, DeviceGroup.Command, Command {

        final long requestId;
        final String groupId;
        final ActorRef<RespondAllTemperatures> replyTo;

        public RequestAllTemperatures(
                long requestId, String groupId, ActorRef<RespondAllTemperatures> replyTo) {
            this.requestId = requestId;
            this.groupId = groupId;
            this.replyTo = replyTo;
        }
    }

    // 返回devices温度信息
    public static final class RespondAllTemperatures {
        final long requestId;
        final Map<String, TemperatureReading> temperatures;

        public RespondAllTemperatures(long requestId, Map<String, TemperatureReading> temperatures) {
            this.requestId = requestId;
            this.temperatures = temperatures;
        }
    }

    public interface TemperatureReading {}
    public static final class Temperature implements TemperatureReading {
        public final double value;

        public Temperature(double value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Temperature that = (Temperature) o;

            return Double.compare(that.value, value) == 0;
        }

        @Override
        public int hashCode() {
            long temp = Double.doubleToLongBits(value);
            return (int) (temp ^ (temp >>> 32));
        }

        @Override
        public String toString() {
            return "Temperature{" + "value=" + value + '}';
        }
    }

    public enum TemperatureNotAvailable implements TemperatureReading {
        INSTANCE
    }

    public enum DeviceNotAvailable implements TemperatureReading {
        INSTANCE
    }

    public enum DeviceTimedOut implements TemperatureReading {
        INSTANCE
    }

    public static Behavior<Command> create() {
        return Behaviors.setup(DeviceManager::new);
    }

    // 保存所有Group Actor
    private final Map<String, ActorRef<DeviceGroup.Command>> groupIdToActor = new HashMap<>();

    public DeviceManager(ActorContext<Command> context) {
        super(context);
        context.getLog().info("DeviceManager started");
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(RequestTrackDevice.class, this::onTrackDevice)
                .onMessage(RequestDeviceList.class, this::onRequestDeviceList)
                .onMessage(DeviceGroupTerminated.class, this::onTerminated)
                .onSignal(PostStop.class, signal -> onPostStop())
                .build();
    }

    private Behavior<Command> onPostStop() {
        getContext().getLog().info("DeviceManager stopped");
        return this;
    }

    private Behavior<Command> onTerminated(DeviceGroupTerminated t) {
        getContext().getLog().info("Device group actor for {} has been terminated", t.groupId);
        groupIdToActor.remove(t.groupId);
        return this;
    }

    private Behavior<Command> onRequestDeviceList(RequestDeviceList request) {
        ActorRef<DeviceGroup.Command> ref = groupIdToActor.get(request.groupId);
        if (ref != null) {
            ref.tell(request);  // 透传给Group, Group会直接回复给request.replyTo
        } else {
            request.replyTo.tell(new ReplyDeviceList(request.requestId, Collections.emptySet()));
        }
        return this;
    }

    private Behavior<Command> onTrackDevice(RequestTrackDevice trackMsg) {
        String groupId = trackMsg.groupId;
        ActorRef<DeviceGroup.Command> ref = groupIdToActor.get(groupId);
        if (ref != null) {
            ref.tell(trackMsg);
        }else {
            getContext().getLog().info("Creating device group actor for {}", groupId);
            ActorRef<DeviceGroup.Command> groupActor =
                    getContext().spawn(DeviceGroup.create(groupId), "group-" + groupId);
            getContext().watchWith(groupActor, new DeviceGroupTerminated(groupId));
            groupActor.tell(trackMsg);
            groupIdToActor.put(groupId, groupActor);
        }
        return this;
    }

}
