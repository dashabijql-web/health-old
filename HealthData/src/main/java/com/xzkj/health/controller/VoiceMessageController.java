package com.xzkj.health.controller;

import com.xzkj.health.common.Result;
import com.xzkj.health.service.VoiceMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 语音广播接口（BP28 协议）
 */
@RestController
@RequestMapping("/api/device/voice")
public class VoiceMessageController {

    @Autowired
    private VoiceMessageService voiceMessageService;

    /**
     * 获取语音模板列表
     * GET /api/device/voice/templates
     */
    @GetMapping("/templates")
    public Result<List<Map<String, String>>> getTemplates() {
        return Result.ok("获取成功", voiceMessageService.getTemplates());
    }

    /**
     * 向手表推送语音广播
     * POST /api/device/voice/send
     * { "imei": "...", "templateId": "evacuate" }
     */
    @PostMapping("/send")
    public Result<Boolean> sendVoice(@RequestBody(required = false) Map<String, String> body) {
        if (body == null || body.isEmpty()) {
            return Result.error("请求体不能为空");
        }
        String imei       = body.get("imei");
        String templateId = body.get("templateId");

        if (imei == null || imei.isBlank()) {
            return Result.error("参数错误: imei 不能为空");
        }
        if (templateId == null || templateId.isBlank()) {
            return Result.error("参数错误: templateId 不能为空");
        }

        boolean started = voiceMessageService.sendVoiceMessage(imei, templateId);
        if (started) {
            return Result.ok("语音广播已开始推送", true);
        } else {
            return Result.error("设备不在线，无法推送语音");
        }
    }
}
