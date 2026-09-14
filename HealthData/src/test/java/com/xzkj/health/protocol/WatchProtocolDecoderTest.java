package com.xzkj.health.protocol;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WatchProtocolDecoderTest {

    @Test
    void decodesCompactLoginFrameFromRealWatch() {
        WatchMessage message = decodeOne("IWAP00861265063894429#");

        assertEquals("IWAP00861265063894429#", message.getRawMessage());
        assertEquals("AP00", message.getProtocolCode());
        assertEquals("AP", message.getCmdType());
        assertEquals("00", message.getCmdNumber());
        assertArrayEquals(new String[]{"861265063894429"}, message.getParams());
        assertEquals("861265063894429", message.getImei());
    }

    @Test
    void decodesCompactHealthFrameWithoutChangingParameterOrder() {
        WatchMessage message = decodeOne("IWAPHP,77,118,76,98,5.1,36.8#");

        assertEquals("APHP", message.getProtocolCode());
        assertArrayEquals(new String[]{"77", "118", "76", "98", "5.1", "36.8"}, message.getParams());
        assertNull(message.getImei());
    }

    @Test
    void stripsCompactFrameSeparatorBeforeParams() {
        WatchMessage message = decodeOne("IWAP49,68#");

        assertEquals("AP49", message.getProtocolCode());
        assertArrayEquals(new String[]{"68"}, message.getParams());
    }

    @Test
    void keepsDecodingDocumentedStarFrame() {
        WatchMessage message = decodeOne("IW*AP49*68#");

        assertEquals("AP49", message.getProtocolCode());
        assertArrayEquals(new String[]{"68"}, message.getParams());
    }

    @Test
    void decodesCompactFrameWithoutParams() {
        WatchMessage message = decodeOne("IWBP03#");

        assertEquals("BP03", message.getProtocolCode());
        assertArrayEquals(new String[0], message.getParams());
    }

    private WatchMessage decodeOne(String rawFrame) {
        EmbeddedChannel channel = new EmbeddedChannel(new WatchProtocolDecoder());
        channel.writeInbound(Unpooled.copiedBuffer(rawFrame, StandardCharsets.US_ASCII));
        WatchMessage message = channel.readInbound();
        channel.finishAndReleaseAll();
        return message;
    }
}
