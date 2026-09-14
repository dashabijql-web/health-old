package com.xzkj.health.handler.watch;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WatchHeartbeatProtocolHandler implements WatchProtocolHandler {

    @Override
    public void handle(WatchMessageHandlerContext context) {
        String imei = context.imei();
        String status = context.param(0);
        String steps = context.paramCount() > 1 ? context.param(1) : "0";
        String rollovers = context.paramCount() > 2 ? context.param(2) : "0";
        String calories = context.paramCount() > 3 ? context.param(3) : "0";

        log.debug("心跳包: IMEI={}, 状态={}, 步数={}, 翻滚={}, 卡路里={}",
                imei, status, steps, rollovers, calories);

        context.dataService().saveHeartbeat(imei, status, steps, rollovers, calories);
        context.writeAscii("IWBP03#");
    }
}
