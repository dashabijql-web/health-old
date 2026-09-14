package com.xzkj.health.service.watch;

import com.xzkj.health.protocol.WatchMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WatchRawPacketServiceTest {

    @Test
    void capturesAndFiltersRecentPackets() {
        WatchRawPacketService service = new WatchRawPacketService();

        service.capture(message("AP00", "861265063894429", "IWAP00861265063894429#"), "861265063894429", "/10.8.138.184:2369");
        service.capture(message("APHP", null, "IWAPHP,0,0,0,95,0.0,0.0,,#"), "861265063894429", "/10.8.138.184:2369");
        service.capture(message("AP03", null, "IWAP03,02000010000008,00000,00#"), "123456789012345", "/10.8.138.185:2000");

        WatchRawPacketService.RawPacketPage page = service.query("861265063894429", "APHP", 10);

        assertEquals(3, page.totalBuffered());
        assertEquals(1, page.returnedCount());
        assertEquals("APHP", page.list().get(0).protocolCode());
        assertEquals("861265063894429", page.list().get(0).imei());
        assertTrue(page.list().get(0).rawMessage().startsWith("IWAPHP"));
    }

    @Test
    void capturesOutgoingPacketsAndFiltersByDirection() {
        WatchRawPacketService service = new WatchRawPacketService();

        service.capture(message("AP00", "861265063894429", "IWAP00861265063894429#"), "861265063894429", "/10.8.138.184:2369");
        service.captureOutgoing("IWBPXL,861265063894429,123456#", "861265063894429", "/10.8.138.184:2369");

        WatchRawPacketService.RawPacketPage page = service.query("861265063894429", "BPXL", "TX", 10);

        assertEquals(2, page.totalBuffered());
        assertEquals(1, page.returnedCount());
        assertEquals("TX", page.list().get(0).direction());
        assertEquals("BPXL", page.list().get(0).protocolCode());
        assertEquals(2, page.list().get(0).paramCount());
        assertEquals("861265063894429", page.list().get(0).params().get(0));
    }

    private WatchMessage message(String protocolCode, String imei, String raw) {
        WatchMessage message = new WatchMessage();
        message.setProtocolCode(protocolCode);
        message.setImei(imei);
        message.setRawMessage(raw);
        message.setParams(raw.contains(",") ? raw.substring(raw.indexOf(',') + 1, raw.length() - 1).split(",") : new String[0]);
        return message;
    }
}
