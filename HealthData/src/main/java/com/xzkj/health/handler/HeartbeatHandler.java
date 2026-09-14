package com.xzkj.health.handler;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║              心跳检测处理器（新手必读）                               ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * 【为什么需要心跳检测？】
 *
 * 智能手表通过 TCP 长连接与服务器保持通信。
 * 但在实际网络环境中，连接可能因以下原因"无声死亡"：
 *   - 网络中断（WiFi 掉线、4G 信号丢失）
 *   - 设备端崩溃或关机（没有发送 TCP FIN/RST 包）
 *   - NAT 设备（路由器）超时关闭了连接映射
 *   - 防火墙静默丢弃了数据包
 *
 * 这种情况下，服务器端仍然认为连接存在（因为没有收到断开通知），
 * 但实际上这是一个"僵尸连接"——占用端口和内存资源，却无法通信。
 *
 * 心跳检测的作用：
 *   1. 定期检测连接是否还活着
 *   2. 及时关闭"僵尸连接"，释放资源
 *   3. 通过定期发送数据包，防止 NAT 超时断开
 *
 * 【IdleStateHandler 与 HeartbeatHandler 的协作】
 *
 * NettyServerConfig 中，Pipeline 中先加了 IdleStateHandler，再加 HeartbeatHandler：
 *
 *   IdleStateHandler(300, 60, 0, SECONDS)
 *     读超时(readerIdle)：300秒内没有收到任何数据 → 触发 READER_IDLE 事件
 *     写超时(writerIdle)：60秒内没有发送任何数据 → 触发 WRITER_IDLE 事件
 *
 *   HeartbeatHandler 监听 IdleStateEvent：
 *     READER_IDLE → 设备太久没发数据，关闭连接（设备可能已断线）
 *     WRITER_IDLE → 服务器主动发心跳包（保持连接活跃）
 *
 * 【ChannelInboundHandlerAdapter 说明】
 *
 * ChannelInboundHandlerAdapter 是处理入站事件的适配器基类。
 * 入站事件包括：连接建立、数据到达、连接断开、自定义事件（如 IdleStateEvent）等。
 * 继承它后，只需覆盖关心的方法，其他方法自动向下传递。
 *
 * 【@ChannelHandler.Sharable 说明】
 *
 * 本 Handler 没有状态（不保存每个连接的数据），
 * 可以被所有连接共享同一个实例，所以加 @Sharable。
 * 配合 @Component + @Autowired，在 NettyServerConfig 中注入单例。
 *
 * 【TCP KeepAlive vs 应用层心跳】
 *
 * TCP 协议本身有 KeepAlive 机制（在 NettyServerConfig 中已设置 SO_KEEPALIVE）。
 * 但 TCP KeepAlive：
 *   - 默认超时时间很长（Linux 默认2小时）
 *   - 无法携带自定义业务信息
 *
 * 应用层心跳（本类实现的）：
 *   - 超时时间完全可控（本项目设置读超时300秒）
 *   - 可以携带业务信息（如步数、状态）
 *   - 更快速地检测连接状态
 *
 * 两者配合使用，能更可靠地维护连接。
 */
@Slf4j
@Component
@ChannelHandler.Sharable   // 无状态，可被所有连接共享
public class HeartbeatHandler extends ChannelInboundHandlerAdapter {

    /**
     * 处理用户触发的事件（包括 IdleStateEvent）
     *
     * Netty 中，IdleStateHandler 检测到空闲状态后，
     * 会调用 pipeline 中下一个 Handler 的 userEventTriggered() 方法，
     * 并传入 IdleStateEvent 对象。
     *
     * 我们在这里判断事件类型（读空闲/写空闲），做出相应处理。
     *
     * @param ctx Netty 上下文（可操作当前连接：发送数据、关闭连接等）
     * @param evt 触发的事件对象（可能是 IdleStateEvent 或其他事件）
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent e = (IdleStateEvent) evt;

            if (e.state() == IdleState.READER_IDLE) {
                // ─── 读空闲：设备超过 300 秒没有发送任何数据 ───────────────
                // 判断：设备可能已经离线（关机、网络断开等）
                // 处理：主动关闭连接，释放服务器资源
                // 后果：DeviceManagerService 的 unregisterDevice() 会被 channelInactive 触发，
                //       自动从在线设备列表中移除该设备
                log.warn("设备 {} 心跳超时（{}秒无数据），关闭连接",
                        ctx.channel().remoteAddress(), 300);
                ctx.close();  // 关闭当前连接

            } else if (e.state() == IdleState.WRITER_IDLE) {
                // ─── 写空闲：服务器超过 60 秒没有发送任何数据 ───────────────
                // 判断：需要发送心跳包保持连接，防止 NAT 超时
                // 处理：向设备发送心跳响应包
                //
                // IWBP03# 是协议规定的服务器心跳包格式
                // 注意：这里是 "IWBP03#"（无星号），不是 "IW*BP03*#"
                // 这是协议的特殊规定（心跳包简化格式）
                log.debug("发送服务器心跳到 {}", ctx.channel().remoteAddress());
                String heartbeat = "IWBP03#";
                ctx.writeAndFlush(heartbeat);  // writeAndFlush 立即发送（不等待更多数据）
            }
            // WRITER_IDLE 和 READER_IDLE 之外还有 ALL_IDLE（读写都空闲）
            // 本项目 NettyServerConfig 中 allIdleTime=0，表示不启用 ALL_IDLE 检测
        }
        // 如果不是 IdleStateEvent，让事件继续传播给下一个 Handler
        // （这里没有调用 super.userEventTriggered，意味着 IdleStateEvent 不再传播）
    }

    /**
     * 心跳处理器异常处理
     *
     * 如果处理心跳事件过程中发生异常，关闭连接。
     * 常见原因：发送心跳时连接已经断开，writeAndFlush 抛出 IO 异常。
     *
     * Connection reset / Broken pipe 是正常的客户端断线，只记录 DEBUG 级别。
     * 其他未知异常记录 WARN 级别。
     *
     * @param ctx   Netty 上下文
     * @param cause 异常原因
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        String remoteAddr = ctx.channel().remoteAddress().toString();
        String causeMsg = cause.getMessage();

        // 常见的网络断线异常（客户端突然断开连接）- 正常情况，不需要打印堆栈
        if (cause instanceof java.net.SocketException &&
            (causeMsg != null && (causeMsg.contains("Connection reset") ||
                                  causeMsg.contains("Broken pipe") ||
                                  causeMsg.contains("Connection timed out")))) {
            log.debug("客户端断开连接: {} - {}", remoteAddr, causeMsg);
        }
        // 其他异常：可能是 bug，记录详细信息
        else {
            log.warn("心跳处理器异常: {} - {}", remoteAddr, causeMsg);
        }

        ctx.close();
    }
}
