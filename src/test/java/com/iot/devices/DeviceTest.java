package com.iot.devices;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class DeviceTest {
    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource();

    @Test
    public void testReplyWithEmptyReadingIfNoTemperatureIsKnown() {
        TestProbe<Device.RespondTemperature> probe =
                testKit.createTestProbe(Device.RespondTemperature.class);
        ActorRef<Device.Command> deviceActor = testKit.spawn(Device.create("group", "device"), "undertest");
        deviceActor.tell(new Device.ReadTemperature(42L, probe.getRef()));
        Device.RespondTemperature response = probe.receiveMessage();
        assertEquals(42L, response.requestId);
        assertEquals(Optional.empty(), response.value);
    }

    @Test
    public void testReplyWithLatestTemperatureReading() {
        // 模拟一个发送方Actor
        TestProbe<Device.TemperatureRecorded> recordProbe =
                testKit.createTestProbe(Device.TemperatureRecorded.class);
        // 模拟一个发送方Actor
        TestProbe<Device.RespondTemperature> readProbe =
                testKit.createTestProbe(Device.RespondTemperature.class);
        ActorRef<Device.Command> deviceActor = testKit.spawn(Device.create("group", "device"));
        deviceActor.tell(new Device.RecordTemperature(1L, 24.0, recordProbe.getRef()));
        assertEquals(1L, recordProbe.receiveMessage().requestId);

        deviceActor.tell(new Device.ReadTemperature(2L, readProbe.getRef()));
        Device.RespondTemperature response1 = readProbe.receiveMessage();  // 发一次, 就只能接收一次哟
        assertEquals(2L, response1.requestId);
        assertEquals(Optional.of(24.0), response1.value);

        deviceActor.tell(new Device.RecordTemperature(3L, 55.0, recordProbe.getRef()));
        assertEquals(3L, recordProbe.receiveMessage().requestId);

        deviceActor.tell(new Device.ReadTemperature(4L, readProbe.getRef()));
        Device.RespondTemperature response2 = readProbe.receiveMessage();
        assertEquals(4L, response2.requestId);
        assertEquals(Optional.of(55.0), response2.value);

    }

    @Test
    public void testReplyToRegistrationRequests() {
        TestProbe<DeviceManager.DeviceRegistered> probe = testKit.createTestProbe(DeviceManager.DeviceRegistered.class);
        ActorRef<DeviceGroup.Command> groupActor = testKit.spawn(DeviceGroup.create("group"));

        groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device", probe.getRef()));
        DeviceManager.DeviceRegistered registered1 = probe.receiveMessage();

        // another deviceId
        groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device3", probe.getRef()));
        DeviceManager.DeviceRegistered registered2 = probe.receiveMessage();
        assertNotEquals(registered1.device, registered2.device);

        // Check that the device actors are working
        TestProbe<Device.TemperatureRecorded> recordProbe =
                testKit.createTestProbe(Device.TemperatureRecorded.class);
        registered1.device.tell(new Device.RecordTemperature(0L, 1.0, recordProbe.getRef()));
        assertEquals(0L, recordProbe.receiveMessage().requestId);
        registered2.device.tell(new Device.RecordTemperature(1L, 2.0, recordProbe.getRef()));
        assertEquals(1L, recordProbe.receiveMessage().requestId);

        // 验证新建的2个device是否能读取温度
        TestProbe<Device.RespondTemperature> readProbe =
                testKit.createTestProbe(Device.RespondTemperature.class);

        registered1.device.tell(new Device.ReadTemperature(1L, readProbe.getRef()));
        Device.RespondTemperature response1 = readProbe.receiveMessage();  // 发一次, 就只能接收一次哟
        assertEquals(1L, response1.requestId);
        assertEquals(Optional.of(1.0), response1.value);

        registered2.device.tell(new Device.ReadTemperature(2L, readProbe.getRef()));
        Device.RespondTemperature response2 = readProbe.receiveMessage();  // 发一次, 就只能接收一次哟
        assertEquals(2L, response2.requestId);
        assertEquals(Optional.of(2.0), response2.value);
    }

    @Test
    public void testIgnoreWrongRegistrationRequests() {
        TestProbe<DeviceManager.DeviceRegistered> probe = testKit.createTestProbe(DeviceManager.DeviceRegistered.class);
        ActorRef<DeviceGroup.Command> groupActor = testKit.spawn(DeviceGroup.create("group"));
        // 消息的groupId和groupActor不一致
        groupActor.tell(new DeviceManager.RequestTrackDevice("wrongGroup", "device1", probe.getRef()));
        probe.expectNoMessage();
    }

    @Test
    public void testReturnSameActorForSameDeviceId() {
        TestProbe<DeviceManager.DeviceRegistered> probe = testKit.createTestProbe(DeviceManager.DeviceRegistered.class);
        ActorRef<DeviceGroup.Command> groupActor = testKit.spawn(DeviceGroup.create("group"));

        groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device", probe.getRef()));
        DeviceManager.DeviceRegistered registered1 = probe.receiveMessage();

        // registering same again should be idempotent
        groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device", probe.getRef()));
        DeviceManager.DeviceRegistered registered2 = probe.receiveMessage();
        assertEquals(registered1.device, registered2.device);
    }

    @Test
    public void testListActiveDevices() {
        TestProbe<DeviceManager.DeviceRegistered> registeredProbe = testKit.createTestProbe(DeviceManager.DeviceRegistered.class);
        ActorRef<DeviceGroup.Command> groupActor = testKit.spawn(DeviceGroup.create("group"));
        groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device1", registeredProbe.getRef()));
        registeredProbe.receiveMessage();

        groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device2", registeredProbe.getRef()));
        registeredProbe.receiveMessage();

        TestProbe<DeviceManager.ReplyDeviceList> deviceListProbe = testKit.createTestProbe(DeviceManager.ReplyDeviceList.class);
        groupActor.tell(new DeviceManager.RequestDeviceList(0L, "group", deviceListProbe.getRef()));
        DeviceManager.ReplyDeviceList reply = deviceListProbe.receiveMessage();
        assertEquals(0L, reply.requestId);
        assertEquals(Stream.of("device1", "device2").collect(Collectors.toSet()), reply.ids);
    }

    @Test
    public void testListActiveDevicesAfterOneShutsDown() {
        TestProbe<DeviceManager.DeviceRegistered> registeredProbe = testKit.createTestProbe(DeviceManager.DeviceRegistered.class);
        ActorRef<DeviceGroup.Command> groupActor = testKit.spawn(DeviceGroup.create("group"));

        groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device1", registeredProbe.getRef()));
        DeviceManager.DeviceRegistered registered1 = registeredProbe.receiveMessage();

        groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device2", registeredProbe.getRef()));
        DeviceManager.DeviceRegistered registered2 = registeredProbe.receiveMessage();

        ActorRef<Device.Command> toShutDown = registered2.device;

        TestProbe<DeviceManager.ReplyDeviceList> deviceListProbe = testKit.createTestProbe(DeviceManager.ReplyDeviceList.class);
        groupActor.tell(new DeviceManager.RequestDeviceList(0L, "group", deviceListProbe.getRef()));
        DeviceManager.ReplyDeviceList reply = deviceListProbe.receiveMessage();
        assertEquals(0L, reply.requestId);
        assertEquals(Stream.of("device1", "device2").collect(Collectors.toSet()), reply.ids);

        // 关闭一个device
        toShutDown.tell(Device.Passivate.INSTANCE);
        registeredProbe.expectTerminated(toShutDown, registeredProbe.getRemainingOrDefault());

        // using awaitAssert to retry because it might take longer for the groupActor
        // to see the Terminated, that order is undefined
        registeredProbe.awaitAssert(()->{
            groupActor.tell(new DeviceManager.RequestDeviceList(1L, "group", deviceListProbe.getRef()));
            DeviceManager.ReplyDeviceList r = deviceListProbe.receiveMessage();
            assertEquals(1L, r.requestId);
            assertEquals(Stream.of("device1").collect(Collectors.toSet()), r.ids);
            return null;
        });
    }
}