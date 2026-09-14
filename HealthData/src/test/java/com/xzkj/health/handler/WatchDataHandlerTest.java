package com.xzkj.health.handler;

import com.xzkj.health.protocol.WatchMessage;
import com.xzkj.health.service.DataProcessService;
import com.xzkj.health.service.DeviceManagerService;
import com.xzkj.health.service.DeviceManagerService.ConnectionProtocol;
import com.xzkj.health.service.watch.WatchRawPacketService;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WatchDataHandlerTest {

    private static String readAsciiOutbound(EmbeddedChannel channel) {
        Object outbound = channel.readOutbound();
        assertNotNull(outbound);
        ByteBuf buffer = (ByteBuf) outbound;
        try {
            return buffer.toString(StandardCharsets.US_ASCII);
        } finally {
            buffer.release();
        }
    }

    @Test
    void loginMessagesRegisterDeviceAndPersistLogin() {
        DeviceManagerService deviceManager = mock(DeviceManagerService.class);
        DataProcessService dataService = mock(DataProcessService.class);
        WatchRawPacketService rawPacketService = mock(WatchRawPacketService.class);
        EmbeddedChannel channel = new EmbeddedChannel(new WatchDataHandler(deviceManager, dataService, rawPacketService));

        WatchMessage message = new WatchMessage();
        message.setProtocolCode("AP00");
        message.setParams(new String[]{"123456789012345"});
        message.setRawMessage("IW*AP00*123456789012345#");

        channel.writeInbound(message);

        verify(deviceManager).registerDevice(
                eq("123456789012345"),
                same(channel),
                eq(ConnectionProtocol.TCP));
        verify(dataService).saveDeviceLogin(eq("123456789012345"), anyString());
        verify(rawPacketService).capture(eq(message), eq("123456789012345"), anyString());
        String response = readAsciiOutbound(channel);
        assertTrue(response.startsWith("IWBP00,"));
        assertTrue(response.endsWith(",8#"));

        channel.finishAndReleaseAll();
    }

    @Test
    void heartRateMessagesDispatchToHealthHandler() {
        DeviceManagerService deviceManager = mock(DeviceManagerService.class);
        DataProcessService dataService = mock(DataProcessService.class);
        WatchRawPacketService rawPacketService = mock(WatchRawPacketService.class);
        EmbeddedChannel channel = new EmbeddedChannel(new WatchDataHandler(deviceManager, dataService, rawPacketService));

        WatchMessage message = new WatchMessage();
        message.setProtocolCode("AP49");
        message.setImei("123456789012345");
        message.setParams(new String[]{"68"});
        message.setRawMessage("IW*AP49*68#");

        channel.writeInbound(message);

        verify(deviceManager).registerDevice(
                eq("123456789012345"),
                same(channel),
                eq(ConnectionProtocol.TCP));
        verify(dataService).saveHeartRate("123456789012345", "68");
        assertEquals("IWBP49#", readAsciiOutbound(channel));

        channel.finishAndReleaseAll();
    }

    @Test
    void healthAllMessagesReuseRegisteredImeiAndReplyOverTcp() {
        DeviceManagerService deviceManager = mock(DeviceManagerService.class);
        DataProcessService dataService = mock(DataProcessService.class);
        WatchRawPacketService rawPacketService = mock(WatchRawPacketService.class);
        EmbeddedChannel channel = new EmbeddedChannel(new WatchDataHandler(deviceManager, dataService, rawPacketService));
        when(deviceManager.getImeiByChannel(same(channel))).thenReturn("123456789012345");

        WatchMessage message = new WatchMessage();
        message.setProtocolCode("APHP");
        message.setParams(new String[]{"77", "118", "76", "98", "5.1", "36.8", "", "", "", "", "", "", ""});
        message.setRawMessage("IW*APHP*77,118,76,98,5.1,36.8,,,,,,,#");

        channel.writeInbound(message);

        verify(deviceManager).registerDevice(
                eq("123456789012345"),
                same(channel),
                eq(ConnectionProtocol.TCP));
        verify(dataService).saveHealthData(message, "77", "118", "76", "98", "5.1", "36.8");
        assertEquals("IWBPHP#", readAsciiOutbound(channel));

        channel.finishAndReleaseAll();
    }

    @Test
    void unknownProtocolsFallBackToSimpleAckWithoutTouchingDataService() {
        DeviceManagerService deviceManager = mock(DeviceManagerService.class);
        DataProcessService dataService = mock(DataProcessService.class);
        WatchRawPacketService rawPacketService = mock(WatchRawPacketService.class);
        EmbeddedChannel channel = new EmbeddedChannel(new WatchDataHandler(deviceManager, dataService, rawPacketService));

        WatchMessage message = new WatchMessage();
        message.setProtocolCode("APZZ");
        message.setImei("123456789012345");
        message.setParams(new String[]{"noop"});
        message.setRawMessage("IW*APZZ*noop#");

        channel.writeInbound(message);

        verify(deviceManager).registerDevice(
                eq("123456789012345"),
                same(channel),
                eq(ConnectionProtocol.TCP));
        verifyNoInteractions(dataService);
        assertEquals("IWBPZZ#", readAsciiOutbound(channel));

        channel.finishAndReleaseAll();
    }
}
