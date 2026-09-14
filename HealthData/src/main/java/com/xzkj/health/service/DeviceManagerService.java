package com.xzkj.health.service;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.sctp.SctpMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.xzkj.health.service.watch.WatchRawPacketService;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║              设备连接管理服务（新手必读）                             ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * 【职责】
 *
 * 管理所有在线智能手表设备的网络连接（Channel），同时支持 TCP 和 SCTP 协议。
 * 提供设备注册、注销、在线状态查询、向指定设备发送消息等功能。
 *
 * 【版本更新说明（TCP → TCP+SCTP）】
 *
 * v1.0（TCP Only）：只管理 TCP 连接，registerDevice(imei, channel)
 * v2.0（TCP+SCTP）：新增 ConnectionProtocol 枚举，
 *                   registerDevice(imei, channel, protocol) 同时记录协议类型
 *                   发送消息时根据协议类型选择正确的发送方式
 *
 * 【为什么需要这个服务？】
 *
 * 智能手表连接服务器后，服务器如果想主动推送数据给某台设备，
 * 需要知道这台设备对应的网络连接对象（Channel）。
 *
 * 例如：管理员在 Web 界面点"立即测量心率"按钮，
 * 后端需要找到对应手表的 Channel，然后向它发送 BPXL 指令。
 *
 * DeviceManagerService 就是 IMEI 和 Channel 的映射表，充当"设备通讯录"。
 *
 * 【数据结构设计】
 *
 * 三组 ConcurrentHashMap 构成完整的设备状态管理：
 *
 *   deviceChannels    (IMEI → Channel)：
 *     通过设备 IMEI 找到对应连接，用于主动发送指令
 *
 *   channelDevices    (Channel → IMEI)：
 *     通过连接找到对应设备 IMEI，用于连接断开时 O(1) 时间内清理
 *
 *   channelProtocols  (Channel → ConnectionProtocol)：【新增】
 *     记录每个连接使用的协议类型（TCP 或 SCTP）
 *     在向设备发送消息时，根据协议类型选择正确的发送格式
 *
 * 【发送消息的协议差异】
 *
 * TCP 信道：channel.writeAndFlush(ByteBuf)
 *   TCP 的 NioSocketChannel 直接接受 ByteBuf，将字节发给设备
 *
 * SCTP 信道：channel.writeAndFlush(SctpMessage)
 *   SCTP 的 SctpChannel 需要 SctpMessage（包含 ByteBuf + 流ID + 协议ID）
 *   channel.writeAndFlush(String 或 ByteBuf) 在 SCTP 下会通过 SctpChannelAdapter 转换
 *   但从 DeviceManagerService 直接 channel.writeAndFlush() 走的是 channel 级别，
 *   会从 Pipeline 尾部向头部经过 SctpChannelAdapter，自动完成转换。
 *   因此 sendToDevice 的实现对 TCP 和 SCTP 都能正确工作。
 *
 * 【ConcurrentHashMap 说明（线程安全）】
 *
 * 多个 Netty Worker 线程可能同时调用 registerDevice / unregisterDevice，
 * ConcurrentHashMap 提供线程安全的并发访问（内部分段锁，比全局锁高效）。
 */
@Service
@Slf4j
public class DeviceManagerService {

    /**
     * 设备连接协议类型枚举
     *
     * 用于区分设备是通过 TCP 还是 SCTP 连接的。
     * 在以下场景中使用：
     *   1. 日志中标注协议类型，方便排查问题
     *   2. 统计各协议连接数量（监控面板使用）
     *   3. 未来扩展：根据协议类型路由特定指令
     */
    public enum ConnectionProtocol {
        /** TCP 协议（端口 9000），全平台支持，传统字节流 */
        TCP,
        /** SCTP 协议（端口 9001），仅 Linux，消息导向，支持多宿主 */
        SCTP
    }

    // ─── 设备状态映射表（三组双/单向映射）──────────────────────────────

    /**
     * IMEI → Channel 映射（正向映射）
     * 通过设备 IMEI 找到对应的网络连接
     * 用于服务器主动向设备发送指令
     */
    private final Map<String, Channel> deviceChannels = new ConcurrentHashMap<>();

    /**
     * Channel → IMEI 映射（反向映射）
     * 通过网络连接找到对应的设备 IMEI
     * 用于连接断开时快速查找并清理对应的 IMEI 记录（O(1) 时间）
     *
     * 为什么需要反向映射？
     *   设备断线时，Netty 只告诉我们哪个 Channel 断开了（channelInactive 回调）。
     *   如果只有正向映射，就要遍历所有 IMEI 去查找对应的 Channel（效率低）。
     *   有了反向映射，可以 O(1) 时间内找到 IMEI，然后从正向映射中删除。
     */
    private final Map<Channel, String> channelDevices = new ConcurrentHashMap<>();

    /**
     * Channel → ConnectionProtocol 映射（协议类型映射）【v2.0 新增】
     * 记录每个连接使用的通信协议（TCP 或 SCTP）
     * 随 channelDevices 同步维护（注册时添加，注销时删除）
     */
    private final Map<Channel, ConnectionProtocol> channelProtocols = new ConcurrentHashMap<>();

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private WatchRawPacketService rawPacketService;

    // ─── 设备注册 / 注销 ──────────────────────────────────────────────

    /**
     * 注册设备连接（记录 IMEI、Channel、协议类型）
     *
     * 当设备发送 AP00 登录包时，WatchDataHandler 调用此方法注册连接。
     * v2.0 更新：新增 protocol 参数，区分 TCP 和 SCTP 连接。
     *
     * 【设备重复连接处理】
     *
     * 如果同一个 IMEI 已经在线（如设备重启，但旧连接还没超时断开），
     * 当前策略：如果旧连接还活跃，直接返回不做处理（防止重复注册导致资源竞争）。
     * 旧连接会通过 IdleStateHandler 的超时机制自然断开。
     *
     * 更激进的策略（可选）：
     *   Channel oldChannel = deviceChannels.get(imei);
     *   if (oldChannel != null && oldChannel.isActive()) { oldChannel.close(); }
     *   这样可以立即断开旧连接，让新连接立刻生效，但风险是可能误断正常连接。
     *
     * 【closeFuture 监听器说明】
     *
     * channel.closeFuture().addListener(...)：
     *   注册一个当此 Channel 关闭时自动调用的回调。
     *   这样当设备断线时，自动调用 unregisterDevice 清理映射表。
     *   WatchDataHandler.channelInactive 也会调用 unregisterDevice，
     *   提供双重清理保障（unregisterDevice 内部是幂等的，多次调用安全）。
     *
     * @param imei     设备的 IMEI 号（15 位数字）
     * @param channel  该设备对应的网络连接对象（TCP 或 SCTP Channel）
     * @param protocol 连接使用的协议类型（ConnectionProtocol.TCP 或 SCTP）
     */
    public void registerDevice(String imei, Channel channel, ConnectionProtocol protocol) {
        if (imei == null || imei.isEmpty() || channel == null) {
            return;
        }

        // 检查该 IMEI 是否已有活跃连接：主动关闭旧连接，允许重连
        // 旧逻辑是静默 return，导致网络抖动后手表重连被拒，数据永远传不上来
        Channel oldChannel = deviceChannels.get(imei);
        if (oldChannel != null && oldChannel != channel && oldChannel.isActive()) {
            log.info("设备 {} 重连，关闭旧连接 {}", imei, oldChannel.id());
            oldChannel.close();
        }

        // ─── 注册三组映射 ──────────────────────────────────────────
        deviceChannels.put(imei, channel);                   // IMEI → Channel
        channelDevices.put(channel, imei);                   // Channel → IMEI
        channelProtocols.put(channel, protocol);             // Channel → 协议类型

        // 注册连接关闭时的自动清理回调（双重保障，channelInactive 也会清理）
        channel.closeFuture().addListener(future -> unregisterDevice(channel));

        log.info("设备注册成功: IMEI={}, 协议={}, 地址={}, 当前在线设备数={}",
                imei, protocol, channel.remoteAddress(), deviceChannels.size());
    }

    /**
     * 注销设备连接（连接断开时清理映射表）
     *
     * 当设备断线时（WatchDataHandler.channelInactive 或 Channel.closeFuture 触发）调用。
     * 从三组映射表中删除该设备的记录，释放内存。
     *
     * 【幂等性说明】
     * 本方法可以被调用多次（closeFuture 和 channelInactive 都会调用），
     * ConcurrentHashMap.remove() 在 key 不存在时直接返回 null，不会报错。
     * 多次调用是安全的（幂等）。
     *
     * @param channel 断线的网络连接对象
     */
    public void unregisterDevice(Channel channel) {
        // 从反向映射取出 IMEI，同时删除记录
        String imei = channelDevices.remove(channel);
        ConnectionProtocol protocol = channelProtocols.remove(channel);  // 同时清理协议类型

        if (imei != null) {
            deviceChannels.remove(imei);  // 删除正向映射
            log.info("设备注销: IMEI={}, 协议={}, 当前在线设备数={}",
                    imei, protocol != null ? protocol : "未知", deviceChannels.size());
            deviceService.updateOfflineStatus(imei);  // 回写数据库离线状态
        }
    }

    // ─── 向设备发送消息 ───────────────────────────────────────────────

    /**
     * 向指定设备发送文本消息（自动适配 TCP / SCTP 协议）
     *
     * 【发送机制说明】
     *
     * channel.writeAndFlush(message) 从 Pipeline 尾部向头部传播（出站方向）。
     * 对于 TCP 和 SCTP 信道，处理链不同：
     *
     *   TCP  信道 Pipeline：... → WatchDataHandler → ... → HeartbeatHandler → IdleStateHandler → Head（NIO TCP）
     *   SCTP 信道 Pipeline：... → WatchDataHandler → ... → SctpChannelAdapter → HeartbeatHandler → IdleStateHandler → Head（NIO SCTP）
     *
     * 当 sendToDevice 调用 channel.writeAndFlush(String) 时：
     *   - SCTP 信道：String → 经过 SctpChannelAdapter → 包装为 SctpMessage → SCTP 网络发送 ✓
     *   - TCP  信道：String → 没有 StringEncoder → Head 无法处理 String → 写入失败（静默错误）
     *
     * 因此对 TCP 信道我们需要先将 String 转为 ByteBuf 再发送：
     *   channel.writeAndFlush(ByteBuf) → Head 接受 ByteBuf → TCP 网络发送 ✓
     *
     * 本方法已根据协议类型选择正确的发送方式，对调用者完全透明。
     *
     * @param imei    目标设备的 IMEI 号
     * @param message 要发送的文本消息（遵循协议格式，如 "IWBP31,353456789012345#"）
     * @return true = 发送成功，false = 设备不在线
     */
    public boolean sendToDevice(String imei, String message) {
        Channel channel = deviceChannels.get(imei);
        if (channel == null || !channel.isActive()) {
            log.warn("设备不在线，无法发送消息: IMEI={}", imei);
            return false;
        }

        ConnectionProtocol protocol = channelProtocols.getOrDefault(channel, ConnectionProtocol.TCP);

        if (protocol == ConnectionProtocol.SCTP) {
            // SCTP 信道：String → ByteBuf → SctpMessage（通过 Pipeline 中的 SctpChannelAdapter 自动完成）
            // channel.writeAndFlush(String) 从尾部出发，经过 SctpChannelAdapter 被包装为 SctpMessage
            channel.writeAndFlush(message);
        } else {
            // TCP 信道：NioSocketChannel 不接受 String，需要先转为 ByteBuf
            ByteBuf buf = Unpooled.copiedBuffer(message, StandardCharsets.US_ASCII);
            channel.writeAndFlush(buf);
        }

        log.debug("向设备发送消息: IMEI={}, 协议={}, 消息={}", imei, protocol, message);
        rawPacketService.captureOutgoing(message, imei, String.valueOf(channel.remoteAddress()));
        return true;
    }

    /**
     * 向指定设备发送下行指令（自动构建协议格式，自动适配 TCP / SCTP）
     *
     * 根据协议规范，下行指令格式：
     *   IW协议号,IMEI,参数1,参数2,...#
     *
     * 示例：发送立即测量心率指令（带流水号）
     *   sendCommand("353456789012345", "BPXL", "080835")
     *   → 发送："IWBPXL,353456789012345,080835#"
     *
     * 示例：发送无参数指令
     *   sendCommand("353456789012345", "BP31")
     *   → 发送："IWBP31,353456789012345#"
     *
     * @param imei         目标设备 IMEI
     * @param protocolCode 下行协议号（BP 开头，如 "BPXL"、"BPXY"、"BPXZ"）
     * @param params       附加参数（可变参数，可以不传）
     * @return true = 发送成功，false = 设备不在线
     */
    public boolean sendCommand(String imei, String protocolCode, String... params) {
        StringBuilder sb = new StringBuilder();
        sb.append("IW").append(protocolCode).append(",");
        sb.append(imei);  // IMEI 是第一个固定参数

        if (params != null && params.length > 0) {
            for (String param : params) {
                sb.append(",").append(param);
            }
        }

        sb.append("#");
        return sendToDevice(imei, sb.toString());
    }

    // ─── 查询方法 ─────────────────────────────────────────────────────

    /**
     * 获取当前所有在线设备的 IMEI 列表
     *
     * @return IMEI 字符串列表（如果没有设备在线，返回空列表）
     */
    public List<String> getOnlineDevices() {
        return new ArrayList<>(deviceChannels.keySet());
    }

    /**
     * 检查指定设备是否在线（连接活跃）
     *
     * @param imei 设备 IMEI
     * @return true = 在线且连接活跃，false = 不在线
     */
    public boolean isDeviceOnline(String imei) {
        Channel channel = deviceChannels.get(imei);
        return channel != null && channel.isActive();
    }

    /**
     * 获取当前在线设备总数
     *
     * @return 在线设备数
     */
    public int getOnlineCount() {
        return deviceChannels.size();
    }

    /**
     * 获取通过指定协议连接的设备数量
     *
     * @param protocol 协议类型（TCP 或 SCTP）
     * @return 使用该协议的在线设备数
     */
    public long getOnlineCountByProtocol(ConnectionProtocol protocol) {
        return channelProtocols.values().stream()
                .filter(p -> p == protocol)
                .count();
    }

    /**
     * 查询指定设备的连接协议类型
     *
     * @param imei 设备 IMEI
     * @return 协议类型，设备不在线时返回 null
     */
    public ConnectionProtocol getDeviceProtocol(String imei) {
        Channel channel = deviceChannels.get(imei);
        return channel != null ? channelProtocols.get(channel) : null;
    }

    /**
     * 根据 Channel 反查设备 IMEI
     *
     * @param channel 连接对象
     * @return 对应的 IMEI，如果未注册返回 null
     */
    public String getImeiByChannel(Channel channel) {
        return channelDevices.get(channel);
    }
}
