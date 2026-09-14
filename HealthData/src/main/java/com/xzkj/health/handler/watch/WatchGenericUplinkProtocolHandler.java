package com.xzkj.health.handler.watch;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WatchGenericUplinkProtocolHandler implements WatchProtocolHandler {

    @Override
    public void handle(WatchMessageHandlerContext context) {
        String imei = context.imei();
        String protocolCode = context.protocolCode();

        log.info("通用上行数据: 协议号={}, IMEI={}", protocolCode, imei);
        context.dataService().saveUplinkData(imei, protocolCode, context.message().getRawMessage());
        context.sendSimpleAck();
    }
}
