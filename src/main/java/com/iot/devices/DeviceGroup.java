package com.iot.devices;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

import java.util.HashMap;
import java.util.Map;

public class DeviceGroup extends AbstractBehavior<DeviceGroup.Command> {

    public interface Command {
    }

    // device终止消息, 无须回复
    private class DeviceTerminated implements Command {
        public final ActorRef<Device.Command> device;
        public final String groupId;
        public final String deviceId;

        DeviceTerminated(ActorRef<Device.Command> device, String groupId, String deviceId) {
            this.device = device;
            this.groupId = groupId;
            this.deviceId = deviceId;
        }
    }


    private final String groupId;
    // 该Group Actor所管理的DeviceId:Actor的Mapping
    private final Map<String, ActorRef<Device.Command>> deviceIdToActor = new HashMap<>();

    public static Behavior<Command> create(String groupId) {
        return Behaviors.setup(context -> new DeviceGroup(context, groupId));
    }

    public DeviceGroup(ActorContext<Command> context, String groupId) {
        super(context);
        this.groupId = groupId;
        context.getLog().info("DeviceGroup {} started", groupId);
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(DeviceManager.RequestTrackDevice.class, this::onTrackDevice)
                .onMessage(DeviceTerminated.class, this::onTerminated)
                .onMessage(DeviceManager.RequestDeviceList.class, r -> r.groupId.equals(groupId), this::onDeviceList)
                .onSignal(PostStop.class, signal -> onPostStop())
                .build();
    }

    private Behavior<Command> onDeviceList(DeviceManager.RequestDeviceList r) {
        r.replyTo.tell(new DeviceManager.ReplyDeviceList(r.requestId, deviceIdToActor.keySet()));
        return this;
    }

    private Behavior<Command> onTerminated(DeviceTerminated t) {
        getContext().getLog().info("Device actor for {} has been terminated", t.deviceId);
        deviceIdToActor.remove(t.deviceId);
        return this;
    }

    // Group收到注册Device的信息, 处理并回复给Sender
    private Behavior<Command> onTrackDevice(DeviceManager.RequestTrackDevice trackMsg) {
        if (this.groupId.equals(trackMsg.groupId)) {
            ActorRef<Device.Command> deviceActor = deviceIdToActor.get(trackMsg.deviceId);
            if (deviceActor == null) {
                getContext().getLog().info("Creating device actor for {}", trackMsg.deviceId);
                deviceActor = getContext().spawn(Device.create(groupId, trackMsg.deviceId), "device-" + trackMsg.deviceId);
                getContext().watchWith(deviceActor, new DeviceTerminated(deviceActor, groupId, trackMsg.deviceId));  // 当Device stop时, 会往Group发DeviceTerminated
                deviceIdToActor.put(trackMsg.deviceId, deviceActor);
            }
            trackMsg.replyTo.tell(new DeviceManager.DeviceRegistered(deviceActor));
        }
        else {
            getContext().getLog().
                    warn("Ignoring TrackDevice request for {}. This actor is responsible for {}.", trackMsg.groupId, this.groupId);
        }
        return this;
    }

    private DeviceGroup onPostStop() {
        getContext().getLog().info("DeviceGroup {} stopped", groupId);
        return this;
    }

}
