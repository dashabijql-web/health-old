package com.xzkj.health.service.impl;

import com.xzkj.health.service.DeviceManagerService;
import com.xzkj.health.service.VoiceMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.Executor;

/**
 * 语音广播服务实现（BP28 协议）
 *
 * 音频规格：8kHz 采样率，8-bit 有符号 PCM，单声道
 * 每包 1024 字节，以十六进制字符串传输
 * 协议格式：IWBP28,{IMEI},{watermark},{total},{seq},{size},{hex}#
 */
@Service
@Slf4j
public class VoiceMessageServiceImpl implements VoiceMessageService {

    @Autowired
    private DeviceManagerService deviceManager;

    /** Spring 托管的异步发包执行器 */
    @Autowired
    @Qualifier("voicePushTaskExecutor")
    private Executor voicePushTaskExecutor;

    /** 语音模板：id → 显示名称 */
    private static final LinkedHashMap<String, String> TEMPLATE_NAMES = new LinkedHashMap<>();
    static {
        TEMPLATE_NAMES.put("safety",   "⚠️ 安全预警，请注意防护");
        TEMPLATE_NAMES.put("evacuate", "🚨 紧急撤离，请立即离开危险区域");
        TEMPLATE_NAMES.put("health",   "💓 健康预警，请立即休息并上报");
        TEMPLATE_NAMES.put("assembly", "📢 紧急集合，请前往指定地点");
        TEMPLATE_NAMES.put("check",    "🔔 安全检查，请配合检查");
    }

    @Override
    public List<Map<String, String>> getTemplates() {
        List<Map<String, String>> list = new ArrayList<>();
        TEMPLATE_NAMES.forEach((id, name) -> {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("name", name);
            list.add(m);
        });
        return list;
    }

    @Override
    public boolean sendVoiceMessage(String imei, String templateId) {
        if (!deviceManager.isDeviceOnline(imei)) {
            log.warn("语音推送失败：设备不在线 imei={}", imei);
            return false;
        }

        byte[] pcm = generatePcm(templateId);
        String watermark = String.format("%06d", System.currentTimeMillis() % 1000000);

        // 异步发包，不阻塞 HTTP 线程
        voicePushTaskExecutor.execute(() -> streamPackets(imei, watermark, pcm));
        log.info("语音推送已触发: imei={}, template={}, watermark={}", imei, templateId, watermark);
        return true;
    }

    // ─────────────────────────────────────────────
    //  内部：分包 + 发送
    // ─────────────────────────────────────────────

    private void streamPackets(String imei, String watermark, byte[] pcm) {
        final int PACKET_SIZE = 1024;
        int totalPackets = (int) Math.ceil((double) pcm.length / PACKET_SIZE);

        for (int i = 0; i < totalPackets; i++) {
            int start = i * PACKET_SIZE;
            int end   = Math.min(start + PACKET_SIZE, pcm.length);
            byte[] chunk = Arrays.copyOfRange(pcm, start, end);

            // 末包补零到 1024 字节
            if (chunk.length < PACKET_SIZE) {
                chunk = Arrays.copyOf(chunk, PACKET_SIZE);
            }

            String hexData = bytesToHex(chunk);
            boolean sent = deviceManager.sendCommand(
                imei, "BP28",
                watermark,
                String.valueOf(totalPackets),
                String.valueOf(i + 1),
                String.valueOf(PACKET_SIZE),
                hexData
            );

            if (!sent) {
                log.warn("BP28 发包中断：imei={}, seq={}/{}", imei, i + 1, totalPackets);
                return;
            }

            // 包间延迟：约 128ms/包（1024字节 @ 8kHz 8-bit = 128ms 音频）
            try { Thread.sleep(100); } catch (InterruptedException e) {
                log.warn("语音推送被中断: imei={}, seq={}/{}", imei, i + 1, totalPackets);
                Thread.currentThread().interrupt();
                return;
            }
        }
        log.info("BP28 推送完成：imei={}, 共{}包, watermark={}", imei, totalPackets, watermark);
    }

    // ─────────────────────────────────────────────
    //  内部：PCM 音频生成（8kHz 8-bit 有符号 PCM）
    // ─────────────────────────────────────────────

    private byte[] generatePcm(String templateId) {
        return switch (templateId) {
            case "evacuate" -> urgentBeeps(880, 3.5);   // 高频急促三连音
            case "safety"   -> alertTone(660, 2.5);     // 中高频警告音
            case "health"   -> alertTone(550, 2.5);     // 中频健康提示
            case "assembly" -> callTone(440, 3.0);      // 集合号角
            case "check"    -> singleBeep(520, 1.5);    // 短提示
            default         -> singleBeep(440, 1.5);
        };
    }

    /** 急促三连音（紧急撤离） */
    private byte[] urgentBeeps(double freq, double totalSeconds) {
        int rate = 8000;
        int total = (int)(rate * totalSeconds);
        byte[] pcm = new byte[total];
        for (int i = 0; i < total; i++) {
            double t = (double) i / rate;
            // 每 0.4s 一个 beep，每 beep 持续 0.25s
            boolean on = (t % 0.4) < 0.25;
            // 加入频率调制使声音更急促
            double modFreq = freq * (1.0 + 0.1 * Math.sin(2 * Math.PI * 8 * t));
            double sample = on ? Math.sin(2 * Math.PI * modFreq * t) * 100 : 0;
            pcm[i] = (byte)(int)sample;
        }
        return pcm;
    }

    /** 警告长音 + 短暂停顿 */
    private byte[] alertTone(double freq, double totalSeconds) {
        int rate = 8000;
        int total = (int)(rate * totalSeconds);
        byte[] pcm = new byte[total];
        for (int i = 0; i < total; i++) {
            double t = (double) i / rate;
            // 每 0.8s 一个音节，持续 0.6s
            boolean on = (t % 0.8) < 0.6;
            double envelope = on ? Math.min(1.0, (t % 0.8) / 0.05) : 0; // 5ms 淡入
            double sample = Math.sin(2 * Math.PI * freq * t) * 100 * envelope;
            pcm[i] = (byte)(int)sample;
        }
        return pcm;
    }

    /** 号角式上升音 */
    private byte[] callTone(double baseFreq, double totalSeconds) {
        int rate = 8000;
        int total = (int)(rate * totalSeconds);
        byte[] pcm = new byte[total];
        for (int i = 0; i < total; i++) {
            double t = (double) i / rate;
            // 每 0.6s 一个音，频率依次升高
            int step = (int)(t / 0.6) % 3;
            double freq = baseFreq * (1.0 + step * 0.25);
            boolean on = (t % 0.6) < 0.45;
            double sample = on ? Math.sin(2 * Math.PI * freq * t) * 90 : 0;
            pcm[i] = (byte)(int)sample;
        }
        return pcm;
    }

    /** 单声提示音 */
    private byte[] singleBeep(double freq, double totalSeconds) {
        int rate = 8000;
        int total = (int)(rate * totalSeconds);
        byte[] pcm = new byte[total];
        for (int i = 0; i < total; i++) {
            double t = (double) i / rate;
            double envelope = Math.sin(Math.PI * t / totalSeconds); // 淡入淡出
            double sample = Math.sin(2 * Math.PI * freq * t) * 100 * envelope;
            pcm[i] = (byte)(int)sample;
        }
        return pcm;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
