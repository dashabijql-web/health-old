package com.xzkj.health.handler.watch;

import com.xzkj.health.protocol.WatchMessage;
import com.xzkj.health.service.DataProcessService;
import com.xzkj.health.service.DeviceManagerService;
import com.xzkj.health.service.watch.WatchRawPacketService;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;

import java.nio.charset.StandardCharsets;

public final class WatchMessageHandlerContext {

    private final ChannelHandlerContext nettyContext;
    private final WatchMessage message;
    private final DeviceManagerService deviceManager;
    private final DataProcessService dataService;
    private final WatchRawPacketService rawPacketService;

    public WatchMessageHandlerContext(ChannelHandlerContext nettyContext,
                                      WatchMessage message,
                                      DeviceManagerService deviceManager,
                                      DataProcessService dataService,
                                      WatchRawPacketService rawPacketService) {
        this.nettyContext = nettyContext;
        this.message = message;
        this.deviceManager = deviceManager;
        this.dataService = dataService;
        this.rawPacketService = rawPacketService;
    }

    public ChannelHandlerContext nettyContext() {
        return nettyContext;
    }

    public Channel channel() {
        return nettyContext.channel();
    }

    public WatchMessage message() {
        return message;
    }

    public String protocolCode() {
        return message.getProtocolCode();
    }

    public String param(int index) {
        return message.getParam(index);
    }

    public int paramCount() {
        return message.getParamCount();
    }

    public String imei() {
        String imei = message.getImei();
        if (imei != null && !imei.isEmpty()) {
            return imei;
        }
        return deviceManager.getImeiByChannel(channel());
    }

    public DeviceManagerService deviceManager() {
        return deviceManager;
    }

    public DataProcessService dataService() {
        return dataService;
    }

    public void sendSimpleAck() {
        writeRaw(message.buildSimpleAck());
    }

    public void sendErrorResponse(String error) {
        String protocolCode = message.getProtocolCode();
        String responseCode = protocolCode != null ? protocolCode.replace("AP", "BP") : "BPER";
        writeRaw("IW" + responseCode + "," + error + "#");
    }

    public void writeAscii(String payload) {
        rawPacketService.captureOutgoing(payload, imei(), String.valueOf(channel().remoteAddress()));
        ByteBuf buffer = nettyContext.alloc().buffer(payload.length());
        buffer.writeBytes(payload.getBytes(StandardCharsets.US_ASCII));
        nettyContext.writeAndFlush(buffer);
    }

    public void writeRaw(Object payload) {
        if (payload instanceof String text) {
            writeAscii(text);
            return;
        }
        nettyContext.writeAndFlush(payload);
    }
}
