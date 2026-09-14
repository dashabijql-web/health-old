package com.xzkj.health.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║           智能手表协议解码器（新手必读）                              ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * 【解码器的作用】
 *
 * 网络传输的本质是字节流（0101010...）。
 * 这个类的职责是：将设备发来的原始字节流，解析成有意义的 WatchMessage 对象。
 *
 * 数据流向：
 *   设备 → TCP字节流 → [WatchProtocolDecoder] → WatchMessage对象 → WatchDataHandler
 *
 * 【TCP 粘包/拆包问题（非常重要的网络编程概念）】
 *
 * 很多新手认为"发一次就收一次"，实际上 TCP 不保证这一点！
 *
 * 假设设备连续发了两条消息：
 *   消息A："IW*AP49*72#"
 *   消息B："IW*AP50*36.7,90#"
 *
 * 实际收到的字节流可能是：
 *
 *   情况1（粘包）：一次收到两条拼在一起
 *   "IW*AP49*72#IW*AP50*36.7,90#"
 *
 *   情况2（拆包）：一条消息分两次收到
 *   第一次："IW*AP49"
 *   第二次："*72#"
 *
 *   情况3（混合）：更复杂的组合
 *   "IW*AP49*72#IW*AP5"  然后  "0*36.7,90#"
 *
 * 解决方案：使用"分隔符协议"
 * 本协议用 '#' 作为消息结束符，收到 '#' 才算一条完整消息。
 * 每次 decode() 被调用时，在字节缓冲区中找 '#'：
 *   - 找到了 → 提取这段数据，解析为 WatchMessage，放入 out 列表
 *   - 没找到 → 说明消息还没到齐，return（等待更多数据）
 *
 * 【ByteToMessageDecoder 继承说明】
 *
 * Netty 提供了 ByteToMessageDecoder 基类，专门解决粘包/拆包问题：
 *   - 它内部维护一个累积缓冲区（cumulation）
 *   - 每次收到数据就追加到缓冲区
 *   - 调用我们实现的 decode() 方法尝试解析
 *   - 解析出一个完整消息后，Netty 自动调整缓冲区游标
 *   - 一次收到多条消息时，decode() 会被循环调用直到缓冲区没有完整消息
 *
 * 【重要：为什么不能加 @ChannelHandler.Sharable？】
 *
 * ByteToMessageDecoder 内部有"状态"（累积缓冲区），
 * 每个连接的数据是独立的，不能共享同一个 decoder 实例。
 * 所以在 NettyServerConfig 中用 new WatchProtocolDecoder() 为每个连接创建独立实例，
 * 而不是 @Autowired 注入（那样只有一个实例会被所有连接共享，数据会混乱）。
 *
 * 【@Component 说明】
 *
 * 虽然加了 @Component，但实际使用时（在 NettyServerConfig 中）
 * 是用 new WatchProtocolDecoder() 创建，不是 @Autowired 注入。
 * @Component 这里意义不大，但保留不影响功能。
 */
@Slf4j
@Component
public class WatchProtocolDecoder extends ByteToMessageDecoder {

    /**
     * 最大帧长度限制（字节）
     * 防止恶意设备发送超长数据导致服务器内存溢出（OOM 攻击）
     * 8192 字节 = 8KB，远超正常协议数据大小（通常 < 200 字节）
     */
    private static final int MAX_FRAME_LENGTH = 8192;

    /**
     * 消息结束符
     * 协议规定：每条消息以 '#' 结尾
     * byte 类型的 '#' = ASCII 值 35
     */
    private static final byte END_MARKER = '#';

    /**
     * Netty 框架调用此方法尝试从字节缓冲区中解析消息
     *
     * 【ByteBuf 说明】
     *
     * ByteBuf 是 Netty 的字节缓冲区，类似于 Java NIO 的 ByteBuffer 但更强大。
     * 它有两个重要指针：
     *   readerIndex：下一个可读字节的位置
     *   writerIndex：下一个可写字节的位置
     *   readableBytes() = writerIndex - readerIndex
     *
     * 读取操作（readBytes、readByte 等）会移动 readerIndex
     * 获取操作（getByte 等）不移动 readerIndex（只看，不消费）
     *
     * 【List<Object> out 说明】
     *
     * 解析出完整的 WatchMessage 后，调用 out.add(message) 传递给下一个 Handler。
     * 如果缓冲区中有多条消息，循环调用 out.add 多次，Netty 会分别分发给下一个 Handler。
     *
     * @param ctx Netty 上下文（可获取 Channel、发送响应等）
     * @param in  接收到的字节缓冲区（可能包含不完整或多条消息）
     * @param out 解析出的消息列表，传递给下一个 ChannelHandler
     */
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        // 步骤1：检查是否有足够数据（最短消息 "IW*A#" = 5个字节）
        if (in.readableBytes() < 4) {
            return;  // 数据太少，等待更多字节到来
        }

        // 步骤2：在缓冲区中查找结束符 '#'
        int endIndex = -1;
        for (int i = in.readerIndex(); i < in.writerIndex(); i++) {
            if (in.getByte(i) == END_MARKER) {  // getByte 不移动 readerIndex
                endIndex = i;
                break;  // 找到第一个 '#' 就停止
            }
        }

        // 步骤3：没找到 '#' → 消息还不完整，等待更多数据
        if (endIndex == -1) {
            if (in.readableBytes() > MAX_FRAME_LENGTH) {
                // 缓冲区已经超过最大限制但还没找到 '#'
                // 可能是恶意数据或协议错误，丢弃并关闭连接
                log.warn("帧过长且未找到结束符，丢弃数据");
                in.clear();    // 清空缓冲区
                ctx.close();   // 关闭连接
            }
            return;  // 等待更多数据
        }

        // 步骤4：计算帧长度（从当前读位置到 '#' 包含 '#'）
        int frameLength = endIndex - in.readerIndex() + 1;

        // 步骤5：帧长度超限检查
        if (frameLength > MAX_FRAME_LENGTH) {
            log.warn("帧长度超过限制: {} > {}", frameLength, MAX_FRAME_LENGTH);
            in.skipBytes(frameLength);  // 跳过这段数据（丢弃）
            sendErrorResponse(ctx, "帧过长");
            return;
        }

        // 步骤6：读取完整帧（readBytes 会移动 readerIndex）
        byte[] frameBytes = new byte[frameLength];
        in.readBytes(frameBytes);  // 从缓冲区读取 frameLength 个字节

        // 用 US-ASCII 编码解析（协议规定纯 ASCII 格式）
        String frame = new String(frameBytes, StandardCharsets.US_ASCII);
        log.debug("收到原始帧: {}", frame);

        // 步骤7：将文本帧解析为 WatchMessage 对象
        try {
            WatchMessage message = parseFrame(frame);
            out.add(message);  // 添加到输出列表，传给下一个 Handler
        } catch (Exception e) {
            log.error("解析消息失败: {}", frame, e);
            sendErrorResponse(ctx, "格式错误");
        }
    }

    /**
     * 将文本帧解析为 WatchMessage 对象
     *
     * 协议格式：
     *   1. 文档格式：IW*协议号*参数1,参数2,...#
     *   2. 实表格式：IW协议号参数1,参数2,...#
     *
     * 解析步骤：
     *   1. 验证前缀 "IW" 和后缀 "#"
     *   2. 优先按带星号格式解析
     *   3. 如果没有星号，按实表上报的无星号格式解析
     *   4. 验证协议号格式（AP/BP 开头 + 数字或字母）
     *   5. 按逗号分割参数字符串
     *   6. 从特定位置提取 IMEI
     *
     * @param frame 完整的文本帧（含 IW* 前缀和 # 结尾）
     * @return 解析好的 WatchMessage 对象
     * @throws IllegalArgumentException 如果格式不符合协议规范
     */
    private WatchMessage parseFrame(String frame) throws IllegalArgumentException {
        // 去除可能的空白字符（如 \r\n）
        frame = frame.trim();

        if (!frame.startsWith("IW") || !frame.endsWith("#")) {
            throw new IllegalArgumentException("无效的协议格式，必须以'IW'开头，以'#'结尾");
        }

        ParsedFrame parsedFrame = frame.startsWith("IW*")
                ? parseStarFrame(frame)
                : parseCompactFrame(frame);

        String protocolCode = parsedFrame.protocolCode();  // 协议号：AP49
        String paramsStr = parsedFrame.paramsStr();        // 参数串：72 或 36.7,90

        // 验证协议号格式（必须 AP/BP 开头，后跟大写字母或数字）
        if (!isValidProtocolCode(protocolCode)) {
            throw new IllegalArgumentException("无效的协议号: " + protocolCode);
        }

        // 构建 WatchMessage 对象
        WatchMessage message = new WatchMessage();
        message.setRawMessage(frame);
        message.setProtocolCode(protocolCode);

        // 从协议号解析 cmdType 和 cmdNumber
        if (protocolCode.length() >= 2) {
            message.setCmdType(protocolCode.substring(0, 2));   // "AP" 或 "BP"
            if (protocolCode.length() > 2) {
                message.setCmdNumber(protocolCode.substring(2)); // "49"、"HP" 等
            }
        }

        // 解析参数：按逗号分割
        if (paramsStr != null && !paramsStr.isEmpty()) {
            String[] params = paramsStr.split(",");
            message.setParams(params);
            // 尝试从参数中提取 IMEI（位置因协议号不同而异）
            extractImei(message, protocolCode, params);
        } else {
            message.setParams(new String[0]);  // 无参数时设为空数组
        }

        log.debug("解析消息成功: {}", message);
        return message;
    }

    private ParsedFrame parseStarFrame(String frame) {
        // 去掉 "IW*"（前3个字符）和 "#"（最后1个字符）
        // 例如："IW*AP49*72#" → "AP49*72"
        String content = frame.substring(3, frame.length() - 1);

        // 按第一个 "*" 分割（最多分2份，防止参数中含 * 被多次分割）
        // 例如："AP49*72" → ["AP49", "72"]
        String[] parts = content.split("\\*", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("缺少协议号或参数部分，必须包含'*'分隔符");
        }
        return new ParsedFrame(parts[0], parts[1]);
    }

    private ParsedFrame parseCompactFrame(String frame) {
        // 实表上报格式没有星号，例如："IWAP00861265063894429#"。
        // 当前协议族的命令码均为 4 位（AP00/APHP/BP03/BPER），因此去掉 IW/# 后前 4 位为协议号。
        String content = frame.substring(2, frame.length() - 1);
        if (content.length() < 4) {
            throw new IllegalArgumentException("无星号协议内容过短");
        }
        String protocolCode = content.substring(0, 4);
        String paramsStr = content.substring(4);
        if (paramsStr.startsWith(",")) {
            paramsStr = paramsStr.substring(1);
        }
        return new ParsedFrame(protocolCode, paramsStr);
    }

    private record ParsedFrame(String protocolCode, String paramsStr) {
    }

    /**
     * 验证协议号格式是否合法
     *
     * 合法格式：AP/BP 开头，后跟至少1位大写字母或数字
     * 合法示例：AP00、AP49、APHP、BP12、BPXL
     * 非法示例：XX00（非 AP/BP 前缀）、AP（太短）、ap00（小写）
     *
     * @param protocolCode 待验证的协议号字符串
     * @return true = 合法，false = 不合法
     */
    private boolean isValidProtocolCode(String protocolCode) {
        if (protocolCode == null || protocolCode.length() < 3) {
            return false;
        }
        String prefix = protocolCode.substring(0, 2);
        if (!"AP".equals(prefix) && !"BP".equals(prefix)) {
            return false;
        }
        // 后缀必须是大写字母或数字（如 "00"、"HP"、"XL" 等）
        String suffix = protocolCode.substring(2);
        return suffix.matches("[A-Z0-9]+");
    }

    /**
     * 从参数列表中提取 IMEI 号
     *
     * 注意：不同协议号中 IMEI 的位置不同！
     *   AP00（设备登录）：IMEI 在 params[0]（第1个参数）
     *   大多数 BP 指令：IMEI 在 params[1]（第2个参数，第1个通常是空或其他数据）
     *   其他协议：尝试在所有参数中找15位纯数字
     *
     * @param message      要填充 IMEI 的消息对象
     * @param protocolCode 协议号
     * @param params       参数数组
     */
    private void extractImei(WatchMessage message, String protocolCode, String[] params) {
        if (params == null || params.length == 0) {
            return;
        }

        String imei = null;

        switch (protocolCode) {
            case "AP00": // 登录包：IW*AP00*353456789012345# → IMEI 在第1个参数
                imei = params.length > 0 ? params[0] : null;
                break;

            // 以下 BP 指令（下行），IMEI 在第2个参数（index=1）
            case "BP12": case "BP14": case "BP15": case "BP16":
            case "BP17": case "BP18": case "BP20": case "BP31":
            case "BP33": case "BP34": case "BP40": case "BP76":
            case "BP77": case "BPXL": case "BPXY": case "BPXZ":
            case "BPJZ": case "BP84": case "BP85": case "BP86":
            case "BP87": case "BP89": case "BP93": case "BP96":
            case "BPXT":
                imei = params.length > 1 ? params[1] : null;
                break;

            default:
                // 未知协议：遍历所有参数，找第一个15位纯数字串
                for (String param : params) {
                    if (param != null && param.matches("\\d{15}")) {
                        imei = param;
                        break;
                    }
                }
        }

        // 验证提取到的 IMEI：必须是15位纯数字
        if (imei != null && imei.matches("\\d{15}")) {
            message.setImei(imei);
        }
    }

    /**
     * 向设备发送错误响应
     *
     * 当数据格式不合法时，向设备回复错误信息
     * 使用 BPER 协议号（BP Error）
     *
     * @param ctx   Netty 上下文
     * @param error 错误描述（简短，ASCII 字符）
     */
    private void sendErrorResponse(ChannelHandlerContext ctx, String error) {
        try {
            String response = "IWBPER," + error + "#";
            ctx.writeAndFlush(ctx.alloc().buffer()
                    .writeBytes(response.getBytes(StandardCharsets.US_ASCII)));
            log.warn("发送错误响应: {}", response);
        } catch (Exception e) {
            log.error("发送错误响应失败", e);
        }
    }

    /**
     * 解码器异常处理
     * 发生异常时关闭连接（防止连接一直处于异常状态）
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("解码器异常", cause);
        ctx.close();
    }
}
