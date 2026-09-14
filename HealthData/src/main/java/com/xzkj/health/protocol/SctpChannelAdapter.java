package com.xzkj.health.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.sctp.SctpMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║          SCTP ↔ ByteBuf 双向适配器（新手必读）                       ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * 【为什么需要这个适配器？】
 *
 * TCP 是字节流协议，数据以 ByteBuf（字节缓冲区）的形式在 Netty 管道中流转。
 * SCTP 是消息导向协议，数据以 SctpMessage（带边界的消息）的形式在管道中流转。
 *
 * 现有的所有 Handler 都是针对 TCP 设计的（操作 ByteBuf / String）：
 *   WatchProtocolDecoder → 接收 ByteBuf
 *   WatchProtocolEncoder → 输出 ByteBuf
 *   WatchDataHandler → writeAndFlush(String 或 ByteBuf)
 *
 * 如果直接把这些 Handler 放进 SCTP 的 Pipeline，会发生类型不匹配：
 *   SCTP 发来 SctpMessage → WatchProtocolDecoder 期望 ByteBuf → 无法处理！
 *   WatchDataHandler 写出 String → SCTP 信道期望 SctpMessage → 发送失败！
 *
 * SctpChannelAdapter 就是这个"翻译层"，插入在 SCTP 信道和业务 Handler 之间：
 *   入站：SctpMessage  →（解包）→ ByteBuf    → 送给 WatchProtocolDecoder
 *   出站：ByteBuf/String ←（打包）← SctpMessage ← 送给 SCTP 信道
 *
 * 有了它，所有业务 Handler（Decoder、DataHandler、Encoder）都不需要修改，
 * 直接复用 TCP 版本的代码。
 *
 * 【SCTP 协议特性说明】
 *
 * SCTP（Stream Control Transmission Protocol，流控制传输协议）与 TCP 的主要区别：
 *
 *   消息边界保护：
 *     TCP：字节流，没有消息边界（需要自定义分隔符或长度头来区分消息）
 *     SCTP：每次 send/recv 操作对应一个完整的消息（天然有边界）
 *     → 解码器不需要处理粘包拆包，但为了兼容性，我们仍使用现有的 # 边界检测
 *
 *   多流支持（Multi-streaming）：
 *     一个 SCTP 连接可以有多个独立的消息流（stream），避免队头阻塞
 *     本项目只使用 stream 0（单流，与协议兼容），未来可扩展
 *
 *   多宿主支持（Multi-homing）：
 *     设备可以同时绑定多个 IP 地址（如 LTE + Wi-Fi），网络切换时连接不中断
 *     这是本项目选用 SCTP 的主要原因之一（手表在 LTE ↔ Wi-Fi 切换时保持连接）
 *
 * 【SctpMessage 结构说明】
 *
 * SctpMessage 有三个关键字段：
 *   streamIdentifier：流 ID（0 = 默认流，用于单流场景）
 *   protocolIdentifier：上层协议 ID（0 = 未指定，通常用于私有协议）
 *   payloadBuffer：实际数据（ByteBuf，与 TCP 的 ByteBuf 格式完全相同）
 *
 * 本适配器创建出站 SctpMessage 时：
 *   new SctpMessage(0, 0, buffer) → stream=0, protocol=0, 数据=buffer
 *
 * 【ChannelDuplexHandler 说明】
 *
 * ChannelDuplexHandler 同时实现了入站和出站的处理：
 *   继承 ChannelInboundHandlerAdapter（处理从网络到应用的数据）
 *   继承 ChannelOutboundHandlerAdapter（处理从应用到网络的数据）
 *
 * 相比 ChannelInboundHandlerAdapter（只能处理入站），
 * ChannelDuplexHandler 可以同时拦截两个方向，是双向适配器的正确基类。
 *
 * 【@Sharable 说明】
 *
 * 本类没有连接级别的状态（无成员变量随连接变化），所有操作都是无状态的转换，
 * 因此可以安全地作为 Spring Bean 单例被所有 SCTP 连接的 Pipeline 共享。
 *
 * 【在 SCTP Pipeline 中的位置】
 *
 *   设备 → SctpMessage
 *       ↓
 *   [SctpChannelAdapter]  ← 本类（入站：SctpMessage→ByteBuf，出站：ByteBuf→SctpMessage）
 *       ↓↑
 *   [HeartbeatHandler]    （不感知 SCTP，操作普通入站事件）
 *       ↓↑
 *   [WatchProtocolDecoder] （不感知 SCTP，操作 ByteBuf）
 *       ↓↑
 *   [WatchDataHandler]     （不感知 SCTP，写出 String/ByteBuf）
 *       ↓↑
 *   [WatchProtocolEncoder] （不感知 SCTP，将 WatchMessage 转 ByteBuf）
 *
 *   最终出站的 ByteBuf/String 都会被 SctpChannelAdapter 包装成 SctpMessage 发给设备
 */
@Slf4j
@Component
@ChannelHandler.Sharable
public class SctpChannelAdapter extends ChannelDuplexHandler {

    // ─── 入站方向：SctpMessage → ByteBuf ──────────────────────────────

    /**
     * 入站数据转换：将 SctpMessage 解包为 ByteBuf
     *
     * 【调用时机】
     * 当 SCTP 信道接收到设备发来的数据时，Netty 将其封装为 SctpMessage，
     * 然后从 Pipeline 头部向后传播，本方法首先拦截。
     *
     * 【为什么要 retain()？】
     * Netty 的 ByteBuf 基于引用计数（Reference Counting）管理内存：
     *   - SctpMessage 创建时，内部 ByteBuf 引用计数 = 1
     *   - fireChannelRead(buf) 将 buf 传给下一个 Handler
     *   - 下一个 Handler 处理完后，Netty 会自动调用 release()，引用计数 -1 = 0 → 释放内存
     *   - 但 SctpMessage 本身释放时也会 release() 一次内部 ByteBuf
     *   - 如果不 retain()，就会出现"双重释放"（引用计数归零后再次释放），导致崩溃
     *
     * content().retain() 将引用计数从 1 增加到 2：
     *   - SctpMessage 释放时：引用计数 2→1（不释放内存）
     *   - 下一个 Handler 释放时：引用计数 1→0（真正释放内存）
     *
     * 这与 SimpleChannelInboundHandler 的自动 release 机制配合工作正确。
     *
     * @param ctx Netty 上下文
     * @param msg 从 SCTP 信道收到的消息（期望是 SctpMessage，也可能是其他类型）
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof SctpMessage) {
            SctpMessage sctpMsg = (SctpMessage) msg;

            // 提取 SctpMessage 内部的 ByteBuf，并增加引用计数（防止双重释放）
            ByteBuf payload = sctpMsg.content().retain();

            log.trace("SCTP 入站: streamId={}, protocolId={}, 字节数={}",
                    sctpMsg.streamIdentifier(),
                    sctpMsg.protocolIdentifier(),
                    payload.readableBytes());

            // 将 ByteBuf 向下传播，由 WatchProtocolDecoder 继续处理（解析协议）
            ctx.fireChannelRead(payload);

            // SctpMessage 本身不需要手动 release，SimpleChannelInboundHandler 会处理
            // 但为安全起见，我们调用 release() 减少 SctpMessage wrapper 的引用
            sctpMsg.release();

        } else {
            // 非 SctpMessage 类型（理论上不会出现），直接传递给下一个 Handler
            log.warn("SCTP 入站收到非 SctpMessage 类型: {}", msg.getClass().getSimpleName());
            ctx.fireChannelRead(msg);
        }
    }

    // ─── 出站方向：ByteBuf / String → SctpMessage ─────────────────────

    /**
     * 出站数据转换：将 ByteBuf 或 String 打包为 SctpMessage
     *
     * 【调用时机】
     * 当 Pipeline 中的任何 Handler（WatchDataHandler、HeartbeatHandler 等）
     * 调用 ctx.writeAndFlush(data) 向设备发送数据时，本方法拦截出站写操作。
     *
     * 【为什么要处理 String 类型？】
     * WatchDataHandler 中有许多地方直接写出 String（如 "IWBP49#"）：
     *   ctx.writeAndFlush("IWBP49#")
     *
     * 对于 TCP 信道，Netty 的 NioSocketChannel 无法直接发送 String（会报错），
     * 但由于错误被静默吞掉，所以 TCP 版本的 String 响应实际上没有发出去。
     *
     * 对于 SCTP 信道，本适配器捕获 String，转换为 ByteBuf → SctpMessage，
     * 确保所有响应都能正确发送给设备。这是 SCTP 版本的一个改进。
     *
     * 【SctpMessage 参数说明】
     * new SctpMessage(streamId, protocolId, buffer)
     *   streamId = 0：使用 SCTP 默认流（单流模式，与手表协议兼容）
     *   protocolId = 0：用户自定义协议（不使用 IANA 注册的协议号）
     *   buffer：实际数据内容（ASCII 编码的协议字符串）
     *
     * @param ctx     Netty 上下文
     * @param msg     要发送的消息（ByteBuf、String，或其他类型）
     * @param promise 异步完成回调（写入完成/失败时通知）
     */
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof ByteBuf) {
            // ByteBuf → SctpMessage（直接包装，无需内容转换）
            ByteBuf buf = (ByteBuf) msg;
            log.trace("SCTP 出站 ByteBuf: 字节数={}", buf.readableBytes());
            ctx.write(new SctpMessage(0, 0, buf), promise);

        } else if (msg instanceof String) {
            // String → ASCII 字节 → ByteBuf → SctpMessage
            // 注意：TCP 版本的 String 写出实际上失败了（NIO channel 不支持 String）
            //       SCTP 版本在这里正确处理了 String，这是相对 TCP 的改进
            String text = (String) msg;
            ByteBuf buf = ctx.alloc().buffer(text.length());
            buf.writeBytes(text.getBytes(StandardCharsets.US_ASCII));
            log.trace("SCTP 出站 String: 内容={}", text);
            ctx.write(new SctpMessage(0, 0, buf), promise);

        } else {
            // 其他类型（如 WatchMessage 经 WatchProtocolEncoder 编码后已转为 ByteBuf）
            // 直接传递，让 Netty 处理（会先经过上游 Encoder 再回到这里）
            log.trace("SCTP 出站未知类型: {}", msg.getClass().getSimpleName());
            ctx.write(msg, promise);
        }
    }
}
