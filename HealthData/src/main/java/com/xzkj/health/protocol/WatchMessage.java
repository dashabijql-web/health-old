package com.xzkj.health.protocol;

import io.netty.channel.Channel;
import lombok.Data;

import java.util.Arrays;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║           智能手表协议消息实体类（新手必读）                          ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * 【协议格式说明】
 *
 * 智能手表与服务器之间使用自定义文本协议进行通信，格式如下：
 *
 *   IW * 协议号 * 参数1,参数2,...#
 *   ↑   ↑  ↑    ↑      ↑         ↑
 *   固定  分  协议  分   参数列表   结束符
 *   前缀  隔  号   隔   逗号分隔
 *
 * 示例：
 *   IW*AP00*353456789012345#        → 设备登录，IMEI=353456789012345
 *   IW*AP49*72#                     → 上报心率 72 bpm
 *   IW*AP50*36.7,90#                → 上报体温 36.7°C，电量 90%
 *   IW*APHP*72,98,8000,36.7,...#    → 综合健康数据
 *   IWBP00,20240115143000,8#        → 服务器响应登录，带服务器时间
 *   IWBP03#                         → 服务器心跳包（简短格式，无星号）
 *
 * 【协议号命名规则】
 *
 * AP（设备 → 服务器，上行，Ascend/Device to Platform）：
 *   AP00：设备登录认证
 *   AP01/AP91：GPS 定位数据
 *   AP02/AP92：基站定位数据
 *   AP03：心跳包（带步数等状态信息）
 *   AP10：报警数据（SOS、跌倒等）
 *   AP49：心率数据
 *   AP50：体温数据
 *   APHT：心率+血压数据
 *   APHP：综合健康数据包（心率、血氧、步数、体温等）
 *
 * BP（服务器 → 设备，下行，Backend/Platform to Device）：
 *   BP00：登录响应（携带服务器时间校时）
 *   BP03：服务器心跳响应
 *   BP12：设置 SOS 号码
 *   BP31：关机指令
 *   BPER：错误响应
 *
 * 【上行 vs 下行的关系】
 *   APxx 的响应通常是 BPxx（同编号，如 AP00 的响应是 BP00）
 *   BPxx 指令的确认通常是 APxx（设备反馈已收到）
 *
 * 【IMEI（International Mobile Equipment Identity）】
 *
 * 每台智能手表有一个唯一的 15 位数字 IMEI 码
 * 类似于手机的序列号，用于唯一标识一台设备
 * 例如：353456789012345
 *
 * 【这个类的作用】
 *
 * WatchMessage 是协议解析的结果对象（DTO，Data Transfer Object）：
 *   - WatchProtocolDecoder 读取字节流，解析出 WatchMessage 对象
 *   - WatchDataHandler 接收 WatchMessage，根据 protocolCode 分发业务处理
 *   - WatchProtocolEncoder 将 WatchMessage 编码为字节流发给设备
 *
 * 【✅ 修改说明】
 *
 * 新增 channel 字段用于存储消息来源的连接，以便在协议本身不包含 IMEI 时
 * （如 AP50、APHP 等），可以通过 Channel 查找对应的 IMEI。
 */
@Data  // Lombok: 自动生成 getter/setter/toString/equals/hashCode
public class WatchMessage {

    // ─── 原始协议数据 ─────────────────────────────────────────────

    /**
     * 原始完整消息字符串
     * 例如："IW*AP49*72#"
     * 用于日志记录和调试
     */
    private String rawMessage;

    /**
     * 协议号（从 IW* 之后到第二个 * 之间的部分）
     * 例如："AP00"、"APHP"、"BP00"
     * WatchDataHandler 根据此字段决定如何处理消息
     */
    private String protocolCode;

    /**
     * 参数数组（最后一个 * 之后、# 之前的内容，按逗号分割）
     * 例如 "IW*AP50*36.7,90#" 解析后 params = ["36.7", "90"]
     * 例如 "IW*AP49*72#" 解析后 params = ["72"]
     */
    private String[] params;

    // ─── 解析后的通用字段 ─────────────────────────────────────────

    /**
     * 设备 IMEI 号（15 位纯数字）
     * 由 extractImei() 方法从参数中提取
     * 不同协议号中 IMEI 的位置不同（有的在 params[0]，有的在 params[1]）
     */
    private String imei;

    /**
     * 命令类型前缀
     * "AP" = 上行消息（设备 → 服务器）
     * "BP" = 下行消息（服务器 → 设备）
     * 从 protocolCode 的前两位提取
     */
    private String cmdType;

    /**
     * 命令编号（protocolCode 去掉前两位）
     * 例如 "AP00" → cmdNumber = "00"
     *      "APHP" → cmdNumber = "HP"
     */
    private String cmdNumber;

    /**
     * 解析后的业务数据对象
     * 根据具体协议，可以存放任何类型的对象
     * 目前暂未使用，为扩展预留
     */
    private Object businessData;

    // ─── ✅ 新增：连接上下文 ─────────────────────────────────────────

    /**
     * ✅ 新增字段：消息来源的 Channel（网络连接）
     *
     * 用途：当协议本身不包含 IMEI 时（如 AP50、APHP），
     *      可以通过 channel 从 DeviceManagerService 中查找对应的 IMEI
     *
     * 注意：使用 transient 关键字，避免序列化时出现问题
     *      （Channel 对象不应该被序列化）
     */
    private transient Channel channel;

    // ─── 工具方法 ─────────────────────────────────────────────────

    /**
     * 判断是否为上行消息（设备 → 服务器）
     *
     * AP 开头的协议号代表设备主动上报的数据
     *
     * @return true 表示上行（设备发给服务器）
     */
    public boolean isUplink() {
        return protocolCode != null && protocolCode.startsWith("AP");
    }

    /**
     * 判断是否为下行消息（服务器 → 设备）
     *
     * BP 开头的协议号代表服务器向设备发送的指令/响应
     *
     * @return true 表示下行（服务器发给设备）
     */
    public boolean isDownlink() {
        return protocolCode != null && protocolCode.startsWith("BP");
    }

    /**
     * 获取指定位置的参数值（安全方式，防止数组越界）
     *
     * 示例：
     *   msg.getParam(0) → 第1个参数
     *   msg.getParam(1) → 第2个参数
     *   msg.getParam(99) → null（不存在则返回 null，不抛异常）
     *
     * @param index 参数索引（从 0 开始）
     * @return 参数值字符串，如果索引越界返回 null
     */
    public String getParam(int index) {
        if (params != null && index >= 0 && index < params.length) {
            return params[index];
        }
        return null;
    }

    /**
     * 获取参数总数量
     *
     * @return 参数个数，没有参数时返回 0
     */
    public int getParamCount() {
        return params != null ? params.length : 0;
    }

    /**
     * 构建响应消息字符串
     *
     * 规则：
     *   APxx 的响应是 BPxx（上行消息的确认）
     *   BPxx 的响应是 APxx（设备对指令的回执）
     *
     * 示例：
     *   msg.protocolCode = "AP49" (心率上报)
     *   msg.buildResponse("OK") → "IWBP49,OK#"
     *   msg.buildResponse()     → "IWBP49#"
     *
     * @param responseParams 响应参数（可变参数，可以不传）
     * @return 完整的响应消息字符串
     */
    public String buildResponse(String... responseParams) {
        StringBuilder sb = new StringBuilder();
        sb.append("IW");

        // 根据当前协议号推算响应协议号
        String responseCode;
        if (protocolCode.startsWith("AP")) {
            // APxx → BPxx（服务器确认设备上报的数据）
            responseCode = "BP" + protocolCode.substring(2);
        } else if (protocolCode.startsWith("BP")) {
            // BPxx → APxx（设备反馈已执行服务器指令）
            responseCode = "AP" + protocolCode.substring(2);
        } else {
            // 未知类型，原样返回
            responseCode = protocolCode;
        }
        sb.append(responseCode);

        // 添加参数部分
        if (responseParams != null && responseParams.length > 0) {
            sb.append(",");
            for (int i = 0; i < responseParams.length; i++) {
                if (i > 0) sb.append(",");
                sb.append(responseParams[i]);
            }
        } else {
            // 没有参数时不再额外补分隔符，保持和协议示例一致
        }

        sb.append("#");  // 结束符
        return sb.toString();
    }

    /**
     * 构建简单确认响应（无参数）
     *
     * 示例：
     *   msg.protocolCode = "AP49"
     *   msg.buildSimpleAck() → "IWBP49#"
     *
     * 用于大多数上行消息的简单确认（设备收到 ACK 后知道服务器已接收数据）
     *
     * @return 简单确认响应字符串
     */
    public String buildSimpleAck() {
        return buildResponse();  // 调用无参版本，生成空参数响应
    }

    /**
     * 调试用的字符串表示
     * 打印关键信息：协议号、IMEI、参数列表
     */
    @Override
    public String toString() {
        return String.format("WatchMessage{protocolCode='%s', imei='%s', params=%s}",
                protocolCode, imei, params != null ? Arrays.toString(params) : "[]");
    }
}
