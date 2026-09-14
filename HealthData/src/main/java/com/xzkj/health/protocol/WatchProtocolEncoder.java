package com.xzkj.health.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║           智能手表协议编码器（新手必读）                              ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * 【编码器的作用（与解码器相反）】
 *
 * 解码器：字节流 → Java 对象（入站，设备发给服务器）
 * 编码器：Java 对象 → 字节流（出站，服务器发给设备）
 *
 * 数据流向：
 *   WatchDataHandler 发送 WatchMessage 对象
 *       ↓
 *   [WatchProtocolEncoder] 将对象编码为 ASCII 字节
 *       ↓
 *   TCP 字节流发送给智能手表设备
 *
 * 【MessageToByteEncoder<WatchMessage> 说明】
 *
 * Netty 的 MessageToByteEncoder 是编码器基类，
 * 泛型参数 WatchMessage 表示：只处理 WatchMessage 类型的消息，
 * 其他类型的消息会直接跳过（传给下一个 Handler）。
 *
 * 我们只需实现 encode() 方法：
 *   - 接收 WatchMessage 对象
 *   - 将其转换为字节写入 ByteBuf
 *   - Netty 自动将 ByteBuf 发送出去
 *
 * 【@ChannelHandler.Sharable 注解（重要！）】
 *
 * Netty 的 Handler 默认不能被多个 Channel 共享。
 * 加 @ChannelHandler.Sharable 后，同一个实例可以被注册到多个 Channel 的 Pipeline。
 *
 * 为什么解码器（WatchProtocolDecoder）不加但编码器可以加？
 *   - 解码器 ByteToMessageDecoder 有内部状态（累积缓冲区，每个连接独立），不能共享
 *   - 编码器 MessageToByteEncoder 是无状态的（encode 只是转换格式），可以共享
 *
 * 标记为 @Sharable 后，NettyServerConfig 可以 @Autowired 注入单例使用，
 * 避免每次建立连接都 new 一个编码器对象。
 *
 * 【编码策略】
 *
 * 如果 WatchMessage.rawMessage 已经设置（直接构建的响应字符串），
 * 就直接用 rawMessage，不再重新构建。
 *
 * 如果 rawMessage 为空，根据 protocolCode 和 params 重新组装：
     *   "IW" + protocolCode + "," + param1 + "," + param2 + "#"
 *
 * 最终转为 ASCII 字节写入 ByteBuf（协议规定所有字符必须是 ASCII）。
 */
@Slf4j
@Component
@ChannelHandler.Sharable   // 声明此 Handler 可被多个 Channel 共享（无状态）
public class WatchProtocolEncoder extends MessageToByteEncoder<WatchMessage> {

    /**
     * 将 WatchMessage 对象编码为字节写入缓冲区
     *
     * 【ByteBuf out 说明】
     *
     * out 是 Netty 分配好的输出字节缓冲区，我们只需要往里写字节。
     * 写完后，Netty 框架会自动将 out 的内容通过 TCP 发送给对方。
     *
     * 【ASCII 编码说明】
     *
     * StandardCharsets.US_ASCII 编码：
     *   - 协议规定所有字符都是 ASCII（0-127），包括 IW、数字、逗号、#等
     *   - ASCII 兼容 UTF-8（ASCII 字符的 UTF-8 编码与 ASCII 相同）
     *   - 使用 ASCII 更明确，也更安全（避免多字节字符的歧义）
     *
     * @param ctx Netty 上下文
     * @param msg 要编码的 WatchMessage 对象
     * @param out 输出字节缓冲区（直接写入即可）
     */
    @Override
    protected void encode(ChannelHandlerContext ctx, WatchMessage msg, ByteBuf out) {
        try {
            String response;

            if (msg.getRawMessage() != null && !msg.getRawMessage().isEmpty()) {
                // 策略1：已经有完整的消息字符串，直接使用
                // 这适用于 WatchDataHandler 中直接赋值 rawMessage 的场景
                response = msg.getRawMessage();
            } else {
                // 策略2：根据 protocolCode 和 params 动态构建
                String protocolCode = msg.getProtocolCode();
                if (protocolCode == null) {
                    protocolCode = "BP00";  // 默认响应协议号（防止 null 导致异常）
                }

                StringBuilder sb = new StringBuilder();
                sb.append("IW").append(protocolCode);

                // 拼接参数（多个参数用逗号分隔）
                if (msg.getParams() != null && msg.getParams().length > 0) {
                    sb.append(",");
                    for (int i = 0; i < msg.getParams().length; i++) {
                        if (i > 0) sb.append(",");
                        sb.append(msg.getParams()[i]);
                    }
                }

                sb.append("#");  // 结束符
                response = sb.toString();
            }

            log.debug("发送响应: {}", response);

            // 将字符串转为 ASCII 字节写入缓冲区
            byte[] bytes = response.getBytes(StandardCharsets.US_ASCII);
            out.writeBytes(bytes);

        } catch (Exception e) {
            log.error("编码消息失败", e);
            // 编码失败时发送通用错误响应，防止对方等待超时
            String errorResponse = "IWBPER,encode-error#";
            out.writeBytes(errorResponse.getBytes(StandardCharsets.US_ASCII));
        }
    }

    /**
     * 编码器异常处理
     * 出现异常时关闭连接，避免连接一直占用资源
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("编码器异常", cause);
        ctx.close();
    }
}
