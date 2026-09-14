package com.xzkj.health.handler.watch;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WatchLocationProtocolHandler implements WatchProtocolHandler {

    @Override
    public void handle(WatchMessageHandlerContext context) {
        switch (context.protocolCode()) {
            case "AP01":
            case "AP91":
                handleGpsLocation(context);
                return;
            case "AP02":
            case "AP92":
                handleBaseStationLocation(context);
                return;
            default:
                throw new IllegalArgumentException("Unsupported location protocol: " + context.protocolCode());
        }
    }

    private void handleGpsLocation(WatchMessageHandlerContext context) {
        String imei = context.imei();
        String locationData = context.param(0);
        if (locationData == null || locationData.length() < 20) {
            log.warn("无效的GPS数据: {}", locationData);
            context.sendSimpleAck();
            return;
        }

        log.info("GPS定位: IMEI={}, 数据长度={}", imei, locationData.length());

        try {
            GpsData gpsData = parseGpsData(locationData);
            if (gpsData != null && gpsData.valid()) {
                context.dataService().saveGpsLocation(imei, gpsData);
                log.info("GPS数据解析成功: 纬度={}, 经度={}, 时间={}",
                        gpsData.latitude(), gpsData.longitude(), gpsData.date());
            } else {
                log.warn("GPS数据无效（未定位）: {}", locationData);
            }

            if (context.paramCount() > 1) {
                parseCellData(context.param(1));
            }
        } catch (Exception e) {
            log.error("解析GPS数据失败: {}", locationData, e);
        }

        context.sendSimpleAck();
    }

    private void handleBaseStationLocation(WatchMessageHandlerContext context) {
        String imei = context.imei();
        log.info("基站定位: IMEI={}", imei);
        context.dataService().saveBaseStationLocation(imei, context.message().getParams());
        context.sendSimpleAck();
    }

    private GpsData parseGpsData(String data) {
        if (data == null || data.length() < 30) {
            return null;
        }

        try {
            String date = data.substring(0, 6);
            boolean valid = "A".equals(data.substring(6, 7));

            int latNIndex = data.indexOf('N', 7);
            if (latNIndex <= 7) {
                return null;
            }
            double latitude = parseDMMtoDD(data.substring(7, latNIndex));

            int lngEIndex = data.indexOf('E', latNIndex + 1);
            if (lngEIndex <= latNIndex + 1) {
                return null;
            }
            double longitude = parseDMMtoDD(data.substring(latNIndex + 1, lngEIndex));

            int speedEnd = lngEIndex + 1;
            while (speedEnd < data.length() && Character.isDigit(data.charAt(speedEnd))) {
                speedEnd++;
            }
            String speedStr = data.substring(lngEIndex + 1, speedEnd);
            double speed = Double.parseDouble(speedStr);

            String time = null;
            if (speedEnd + 6 <= data.length()) {
                time = data.substring(speedEnd, speedEnd + 6);
                speedEnd += 6;
            }

            int dirEnd = speedEnd;
            while (dirEnd < data.length()
                    && (Character.isDigit(data.charAt(dirEnd)) || data.charAt(dirEnd) == '.')) {
                dirEnd++;
            }
            double direction = 0;
            if (dirEnd > speedEnd) {
                direction = Double.parseDouble(data.substring(speedEnd, dirEnd));
            }

            String status = dirEnd < data.length() ? data.substring(dirEnd) : null;
            return new GpsData(date, valid, latitude, longitude, speed, time, direction, status);
        } catch (Exception e) {
            log.error("解析GPS数据异常: {}", data, e);
            return null;
        }
    }

    private double parseDMMtoDD(String dmm) {
        try {
            int dotIndex = dmm.indexOf('.');
            if (dotIndex < 2) {
                return 0;
            }

            String degreesStr = dmm.substring(0, dotIndex - 2);
            String minutesStr = dmm.substring(dotIndex - 2);
            double degrees = Double.parseDouble(degreesStr);
            double minutes = Double.parseDouble(minutesStr);
            return degrees + (minutes / 60.0);
        } catch (Exception e) {
            log.error("转换坐标失败: {}", dmm, e);
            return 0;
        }
    }

    private void parseCellData(String cellData) {
        if (cellData == null || cellData.isEmpty()) {
            return;
        }

        try {
            String[] parts = cellData.split(",");
            if (parts.length >= 4) {
                log.debug("基站信息: MCC={}, MNC={}, LAC={}, CID={}",
                        parts[0], parts[1], parts[2], parts[3]);
            }
        } catch (Exception e) {
            log.error("解析基站数据失败: {}", cellData, e);
        }
    }

    private record GpsData(String date,
                           boolean valid,
                           double latitude,
                           double longitude,
                           double speed,
                           String time,
                           double direction,
                           String status) {
    }
}
