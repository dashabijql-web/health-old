package com.xzkj.health.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.xzkj.health.common.Result;
import com.xzkj.health.config.security.DeviceDiagnosticsAuthorizer;
import com.xzkj.health.service.DeviceManagerService;
import com.xzkj.health.service.watch.WatchRawPacketService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/watch")
public class WatchRawPacketController {

    private final WatchRawPacketService rawPacketService;
    private final DeviceManagerService deviceManagerService;
    private final DeviceDiagnosticsAuthorizer diagnosticsAuthorizer;

    public WatchRawPacketController(WatchRawPacketService rawPacketService,
                                    DeviceManagerService deviceManagerService,
                                    DeviceDiagnosticsAuthorizer diagnosticsAuthorizer) {
        this.rawPacketService = rawPacketService;
        this.deviceManagerService = deviceManagerService;
        this.diagnosticsAuthorizer = diagnosticsAuthorizer;
    }

    @GetMapping("/raw-packets")
    public Result<WatchRawPacketService.RawPacketPage> listRawPackets(
            @RequestParam(required = false) String imei,
            @RequestParam(required = false) String protocolCode,
            @RequestParam(required = false) String direction,
            @RequestParam(defaultValue = "200") Integer limit) {
        diagnosticsAuthorizer.checkUser(StpUtil.getLoginIdAsLong());
        return Result.ok("获取成功", rawPacketService.query(imei, protocolCode, direction, limit));
    }

    @PostMapping("/command")
    public Result<WatchCommandResult> sendWatchCommand(@RequestBody WatchCommandRequest request) {
        diagnosticsAuthorizer.checkUser(StpUtil.getLoginIdAsLong());
        if (request == null || isBlank(request.imei()) || isBlank(request.protocolCode())) {
            return Result.error(400, "IMEI 和协议号不能为空");
        }

        String imei = request.imei().trim();
        if (!imei.matches("\\d{15}")) {
            return Result.error(400, "IMEI 格式不正确");
        }

        String protocolCode = request.protocolCode().trim().toUpperCase();
        if (!protocolCode.matches("BP[A-Z0-9]{2}")) {
            return Result.error(400, "只允许发送 BPxx 下行命令");
        }

        List<String> params = request.params() == null ? List.of() : request.params();
        boolean sent = deviceManagerService.sendCommand(imei, protocolCode, params.toArray(String[]::new));
        String rawCommand = buildRawCommand(imei, protocolCode, params);
        if (!sent) {
            return Result.error(503, "设备不在线，命令未发送");
        }

        return Result.ok("发送成功", new WatchCommandResult(true, imei, protocolCode, rawCommand));
    }

    private String buildRawCommand(String imei, String protocolCode, List<String> params) {
        StringBuilder sb = new StringBuilder("IW").append(protocolCode).append(",").append(imei);
        for (String param : params) {
            sb.append(",").append(param == null ? "" : param);
        }
        return sb.append("#").toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public record WatchCommandRequest(String imei, String protocolCode, List<String> params) {
    }

    public record WatchCommandResult(boolean sent, String imei, String protocolCode, String rawCommand) {
    }
}
