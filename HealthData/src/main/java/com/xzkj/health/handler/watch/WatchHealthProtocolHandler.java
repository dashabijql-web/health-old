package com.xzkj.health.handler.watch;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WatchHealthProtocolHandler implements WatchProtocolHandler {

    @Override
    public void handle(WatchMessageHandlerContext context) {
        switch (context.protocolCode()) {
            case "AP49":
                handleHeartRate(context);
                return;
            case "AP50":
                handleTemperature(context);
                return;
            case "APHT":
                handleHeartRateBloodPressure(context);
                return;
            case "APHP":
                handleHealthAll(context);
                return;
            case "AP97":
                handleSleep(context);
                return;
            default:
                throw new IllegalArgumentException("Unsupported health protocol: " + context.protocolCode());
        }
    }

    private void handleHeartRate(WatchMessageHandlerContext context) {
        String imei = context.imei();
        String heartRate = context.param(0);

        log.info("心率数据: IMEI={}, 心率={}", imei, heartRate);
        context.dataService().saveHeartRate(imei, heartRate);
        context.writeRaw("IWBP49#");
    }

    private void handleTemperature(WatchMessageHandlerContext context) {
        if (context.paramCount() >= 2) {
            String temperature = context.param(0);
            String battery = context.param(1);
            log.info("温度数据: IMEI={}, 温度={}°C, 电量={}%",
                    context.imei(), temperature, battery);
            context.dataService().saveTemperature(context.message(), temperature, battery);
        }

        context.writeRaw("IWBP50#");
    }

    private void handleHeartRateBloodPressure(WatchMessageHandlerContext context) {
        if (context.paramCount() >= 3) {
            String heartRate = context.param(0);
            String highPressure = context.param(1);
            String lowPressure = context.param(2);

            log.info("心率血压: IMEI={}, 心率={}, 高压={}, 低压={}",
                    context.imei(), heartRate, highPressure, lowPressure);

            context.dataService().saveBloodPressure(
                    context.message(), heartRate, highPressure, lowPressure);
        }

        context.writeRaw("IWBPHT#");
    }

    private void handleHealthAll(WatchMessageHandlerContext context) {
        log.info("综合健康数据: IMEI={}, 参数个数={}", context.imei(), context.paramCount());

        context.dataService().saveHealthData(
                context.message(),
                context.param(0),
                context.param(1),
                context.param(2),
                context.param(3),
                context.param(4),
                context.param(5)
        );

        context.writeRaw("IWBPHP#");
    }

    private void handleSleep(WatchMessageHandlerContext context) {
        log.info("睡眠数据: IMEI={}, 参数个数={}", context.imei(), context.paramCount());

        context.dataService().saveSleep(context.message(), context.param(0), context.param(1));
        context.writeRaw("IWBP97#");
    }
}
