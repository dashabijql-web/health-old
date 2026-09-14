package com.xzkj.health.handler.watch;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WatchDownlinkResponseProtocolHandler implements WatchProtocolHandler {

    @Override
    public void handle(WatchMessageHandlerContext context) {
        String imei = context.imei();
        String protocolCode = context.protocolCode();

        log.info("设备响应: 协议号={}, IMEI={}", protocolCode, imei);
        context.dataService().saveDownlinkResponse(imei, protocolCode, context.message().getRawMessage());
    }
}
