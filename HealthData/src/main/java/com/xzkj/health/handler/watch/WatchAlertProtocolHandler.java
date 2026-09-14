package com.xzkj.health.handler.watch;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WatchAlertProtocolHandler implements WatchProtocolHandler {

    @Override
    public void handle(WatchMessageHandlerContext context) {
        String imei = context.imei();
        String alertData = context.param(0);

        log.warn("报警数据: IMEI={}, 数据={}", imei, alertData);

        String alertType = "未知";
        if (context.paramCount() > 6) {
            alertType = parseAlertType(context.param(6));
        }

        context.dataService().saveAlert(imei, alertType, alertData);
        context.sendSimpleAck();
    }

    private String parseAlertType(String alertCode) {
        if (alertCode == null) {
            return "未知";
        }

        switch (alertCode) {
            case "01":
                return "SOS报警";
            case "02":
                return "低电报警";
            case "03":
                return "脱落报警";
            case "04":
                return "佩戴提醒";
            case "05":
            case "06":
                return "跌倒报警";
            case "08":
                return "房颤报警";
            case "13":
            case "14":
            case "15":
                return "拆卸报警";
            case "20":
                return "红外报警";
            default:
                return "未知报警(" + alertCode + ")";
        }
    }
}
