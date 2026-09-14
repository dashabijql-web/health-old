package com.xzkj.health.handler;

import com.xzkj.health.handler.watch.WatchAlertProtocolHandler;
import com.xzkj.health.handler.watch.WatchDownlinkResponseProtocolHandler;
import com.xzkj.health.handler.watch.WatchGenericUplinkProtocolHandler;
import com.xzkj.health.handler.watch.WatchHealthProtocolHandler;
import com.xzkj.health.handler.watch.WatchHeartbeatProtocolHandler;
import com.xzkj.health.handler.watch.WatchLocationProtocolHandler;
import com.xzkj.health.handler.watch.WatchLoginProtocolHandler;
import com.xzkj.health.handler.watch.WatchMessageHandlerContext;
import com.xzkj.health.handler.watch.WatchProtocolHandler;
import com.xzkj.health.protocol.WatchMessage;
import com.xzkj.health.service.DataProcessService;
import com.xzkj.health.service.DeviceManagerService;
import com.xzkj.health.service.DeviceManagerService.ConnectionProtocol;
import com.xzkj.health.service.watch.WatchRawPacketService;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.sctp.SctpChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@ChannelHandler.Sharable
public class WatchDataHandler extends SimpleChannelInboundHandler<WatchMessage> {

    private final DeviceManagerService deviceManager;
    private final DataProcessService dataService;
    private final WatchRawPacketService rawPacketService;
    private final Map<String, WatchProtocolHandler> protocolHandlers;

    public WatchDataHandler(DeviceManagerService deviceManager,
                            DataProcessService dataService,
                            WatchRawPacketService rawPacketService) {
        this.deviceManager = deviceManager;
        this.dataService = dataService;
        this.rawPacketService = rawPacketService;
        this.protocolHandlers = buildProtocolHandlers();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WatchMessage msg) {
        WatchMessageHandlerContext context = new WatchMessageHandlerContext(
                ctx, msg, deviceManager, dataService, rawPacketService);
        try {
            msg.setChannel(ctx.channel());
            String protocolCode = msg.getProtocolCode();
            String imei = prepareImei(ctx, msg);

            log.info("收到消息: 协议号={}, IMEI={}, 参数个数={}",
                    protocolCode, imei, msg.getParamCount());
            rawPacketService.capture(msg, imei, String.valueOf(ctx.channel().remoteAddress()));

            if (requiresRegisteredDevice(protocolCode) && isBlank(imei)) {
                log.error("设备未登录就发送数据: 协议={}, ChannelId={}",
                        protocolCode, ctx.channel().id().asShortText());
                context.sendErrorResponse("请先登录");
                return;
            }

            if (!isBlank(imei)) {
                registerDevice(ctx.channel(), imei);
            }

            dispatch(context);
        } catch (Exception e) {
            log.error("处理消息异常: {}", msg.getRawMessage(), e);
            context.sendErrorResponse("处理异常");
        }
    }

    private void dispatch(WatchMessageHandlerContext context) {
        String protocolCode = context.protocolCode();
        WatchProtocolHandler handler = protocolHandlers.get(protocolCode);
        if (handler == null) {
            log.warn("未知协议号: {}", protocolCode);
            context.sendSimpleAck();
            return;
        }
        handler.handle(context);
    }

    private String prepareImei(ChannelHandlerContext ctx, WatchMessage msg) {
        String protocolCode = msg.getProtocolCode();
        String imei = msg.getImei();

        if ("AP00".equals(protocolCode)) {
            String loginImei = msg.getParam(0);
            if (isValidImei(loginImei)) {
                msg.setImei(loginImei);
                imei = loginImei;
            } else {
                log.warn("登录包IMEI无效: {}", loginImei);
            }
        }

        if (isBlank(imei)) {
            imei = deviceManager.getImeiByChannel(ctx.channel());
            if (!isBlank(imei)) {
                msg.setImei(imei);
                log.debug("从Channel获取IMEI: {} (协议: {})", imei, protocolCode);
            }
        }

        return imei;
    }

    private boolean requiresRegisteredDevice(String protocolCode) {
        return !"AP00".equals(protocolCode) && !"AP03".equals(protocolCode);
    }

    private void registerDevice(Channel channel, String imei) {
        deviceManager.registerDevice(imei, channel, resolveProtocol(channel));
    }

    private ConnectionProtocol resolveProtocol(Channel channel) {
        return channel instanceof SctpChannel ? ConnectionProtocol.SCTP : ConnectionProtocol.TCP;
    }

    private boolean isValidImei(String imei) {
        return imei != null && imei.matches("\\d{15}");
    }

    private boolean isBlank(String value) {
        return value == null || value.isEmpty();
    }

    private Map<String, WatchProtocolHandler> buildProtocolHandlers() {
        Map<String, WatchProtocolHandler> handlers = new LinkedHashMap<>();

        register(handlers, new WatchLoginProtocolHandler(), "AP00");
        register(handlers, new WatchLocationProtocolHandler(), "AP01", "AP91", "AP02", "AP92");
        register(handlers, new WatchHeartbeatProtocolHandler(), "AP03");
        register(handlers, new WatchAlertProtocolHandler(), "AP10");
        register(handlers, new WatchHealthProtocolHandler(), "AP49", "AP50", "APHT", "APHP", "AP97");
        register(handlers, new WatchGenericUplinkProtocolHandler(),
                "AP05", "AP07", "AP51", "APTM", "APWT", "AP94", "APHD", "APRR");
        register(handlers, new WatchDownlinkResponseProtocolHandler(),
                "AP12", "AP14", "AP15", "AP16", "AP17", "AP18", "AP20", "AP28",
                "AP31", "AP33", "AP34", "AP40", "AP76", "AP77", "APXL", "APXY",
                "APXZ", "APJZ", "AP84", "AP85", "AP86", "AP87", "AP89", "AP93",
                "AP96", "APXT");

        return Collections.unmodifiableMap(handlers);
    }

    private void register(Map<String, WatchProtocolHandler> handlers,
                          WatchProtocolHandler handler,
                          String... protocolCodes) {
        for (String protocolCode : protocolCodes) {
            handlers.put(protocolCode, handler);
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        log.info("设备连接: {}", ctx.channel().remoteAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.info("设备断开: {}", ctx.channel().remoteAddress());
        deviceManager.unregisterDevice(ctx.channel());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("连接异常: {}", ctx.channel().remoteAddress(), cause);
        ctx.close();
    }
}
