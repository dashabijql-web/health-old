package com.xzkj.health.service;

import java.util.List;
import java.util.Map;

/**
 * 语音广播服务（BP28 协议）
 * 将预录语音以 1024字节/包 流式发送到手表
 */
public interface VoiceMessageService {

    /**
     * 获取可用语音模板列表
     */
    List<Map<String, String>> getTemplates();

    /**
     * 向指定手表发送语音广播（异步，立即返回）
     *
     * @param imei       目标设备 IMEI
     * @param templateId 语音模板 ID
     * @return false=设备不在线；true=已开始推送
     */
    boolean sendVoiceMessage(String imei, String templateId);
}
