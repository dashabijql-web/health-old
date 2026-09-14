package com.xzkj.health.config;

import com.xzkj.health.handler.HeartbeatHandler;
import com.xzkj.health.handler.WatchDataHandler;
import com.xzkj.health.protocol.SctpChannelAdapter;
import com.xzkj.health.protocol.WatchProtocolDecoder;
import com.xzkj.health.protocol.WatchProtocolEncoder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.sctp.SctpChannel;
import io.netty.channel.sctp.nio.NioSctpServerChannel;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║          Netty SCTP 服务器配置（新手必读）                           ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * 【这个类的职责】
 *
 * 与 NettyServerConfig（TCP 服务器）的结构完全对称，区别只在于：
 *   TCP  版：使用 NioServerSocketChannel + SocketChannel（字节流）
 *   SCTP 版：使用 NioSctpServerChannel  + SctpChannel   （消息导向）
 *
 * 其余配置（EventLoopGroup 数量、超时时间、业务 Handler 链）完全复用 TCP 版本的逻辑。
 *
 * 【@ConditionalOnProperty —— 配置开关】
 *
 * @ConditionalOnProperty(name = "netty.sctp.enabled", havingValue = "true")
 * 含义：只有当 application.yml 中 netty.sctp.enabled = true 时，才创建这个 Bean（启动 SCTP 服务器）。
 * 如果配置为 false 或不存在，Spring 完全忽略这个类，SCTP 服务器不启动。
 *
 * 好处：
 *   - 开发环境（Windows）：sctp.enabled=false → 不启动 SCTP，不影响开发
 *   - 生产环境（Linux）：sctp.enabled=true → 同时启动 TCP 和 SCTP
 *   - 测试环境：可以灵活配置
 *
 * 【SCTP vs TCP —— 核心技术差异】
 *
 * ┌─────────────────┬──────────────────────────────┬──────────────────────────────┐
 * │     特性         │          TCP                 │          SCTP                │
 * ├─────────────────┼──────────────────────────────┼──────────────────────────────┤
 * │ 数据模型         │ 字节流（无边界）              │ 消息导向（有边界）            │
 * │ 粘包处理         │ 需要手动处理（# 分隔符）      │ 天然支持，但兼容保留          │
 * │ 多路径（多宿主）  │ 不支持                       │ 支持（手表 LTE↔Wi-Fi 切换）  │
 * │ 多流             │ 不支持                       │ 支持（本项目用 stream 0）    │
 * │ 操作系统支持      │ 全平台                       │ 仅 Linux（需内核模块）        │
 * │ Netty 类         │ NioServerSocketChannel       │ NioSctpServerChannel         │
 * │ 通道类型         │ SocketChannel                │ SctpChannel                  │
 * │ 消息类型         │ ByteBuf                      │ SctpMessage（包含 ByteBuf）  │
 * └─────────────────┴──────────────────────────────┴──────────────────────────────┘
 *
 * 【SCTP Pipeline 设计】
 *
 * SCTP 的 Pipeline 比 TCP 多了 SctpChannelAdapter 层（负责类型转换）：
 *
 *   TCP  Pipeline: IdleStateHandler → HeartbeatHandler → WatchProtocolDecoder → WatchDataHandler → WatchProtocolEncoder
 *   SCTP Pipeline: IdleStateHandler → SctpChannelAdapter → HeartbeatHandler → WatchProtocolDecoder → WatchDataHandler → WatchProtocolEncoder
 *                                              ↑
 *                                    唯一新增的 Handler
 *
 * SctpChannelAdapter 的位置说明（非常关键！）：
 *
 *   入站方向（设备→服务器）：
 *     SctpMessage 到达 → [SctpChannelAdapter 解包 → ByteBuf] → HeartbeatHandler → WatchProtocolDecoder → ...
 *
 *   出站方向（服务器→设备）：
 *     WatchDataHandler 写出 String/ByteBuf → HeartbeatHandler(仅入站，跳过) →
 *     [SctpChannelAdapter 打包 → SctpMessage] → IdleStateHandler(仅入站，跳过) → Head(发送)
 *
 *   HeartbeatHandler 写出 ByteBuf → [SctpChannelAdapter 打包 → SctpMessage] → Head(发送) ✓
 *
 * 关键原则：SctpChannelAdapter 必须在 HeartbeatHandler 的左侧（靠近 Head），
 * 这样 HeartbeatHandler 的出站写操作才能被适配器拦截。
 *
 * 【部署前 Linux 检查命令】
 *
 *   # 1. 确认 SCTP 模块已加载（如果没有输出，需要执行步骤2）
 *   lsmod | grep sctp
 *
 *   # 2. 加载 SCTP 内核模块
 *   sudo modprobe sctp
 *
 *   # 3. 开放防火墙端口（iptables）
 *   sudo iptables -A INPUT -p sctp --dport 9001 -j ACCEPT
 *
 *   # 4. 或者使用 firewalld
 *   sudo firewall-cmd --permanent --add-port=9001/sctp && sudo firewall-cmd --reload
 *
 *   # 5. 验证端口监听（启动应用后）
 *   ss -lnp | grep 9001
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "netty.sctp.enabled", havingValue = "true")
public class NettySctpServerConfig {

    /**
     * SCTP 监听端口（从配置文件读取，默认 9001）
     * 与 TCP 服务器的端口（9000）不同，两者并行运行互不干扰
     */
    @Value("${netty.sctp.port:9001}")
    private int port;

    /** Boss 线程数（负责接受新 SCTP 连接，1 个足够） */
    @Value("${netty.sctp.boss-threads:1}")
    private int bossThreads;

    /** Worker 线程数（负责 SCTP 消息读写） */
    @Value("${netty.sctp.worker-threads:4}")
    private int workerThreads;

    /** 读空闲超时（秒）：超过此时间没有收到设备消息，断开连接 */
    @Value("${netty.sctp.idle-timeout:300}")
    private int idleTimeout;

    /** 写空闲超时（秒）：超过此时间没有向设备发送消息，主动发心跳 */
    @Value("${netty.sctp.write-idle-timeout:60}")
    private int writeIdleTimeout;

    // ─── 复用的 TCP 业务 Handler（所有业务逻辑不需要改）────────────────

    /** 心跳处理器：读空闲→断连，写空闲→发心跳。与 TCP 共用，无需修改 */
    @Autowired
    private HeartbeatHandler heartbeatHandler;

    /** 业务处理器：解析协议号，分发健康数据处理。与 TCP 完全共用 */
    @Autowired
    private WatchDataHandler dataHandler;

    /** 协议编码器：WatchMessage 对象 → 字节流。与 TCP 共用 */
    @Autowired
    private WatchProtocolEncoder protocolEncoder;

    /**
     * SCTP 信道适配器：SctpMessage ↔ ByteBuf 双向转换
     * SCTP 专用，TCP Pipeline 中没有这个 Handler
     */
    @Autowired
    private SctpChannelAdapter sctpChannelAdapter;

    /** 保存 EventLoopGroup 引用，用于应用关闭时优雅释放 */
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    /**
     * 启动 Netty SCTP 服务器
     *
     * 【与 TCP 版本的代码差异（只有3处不同）】
     *
     * 1. 通道类：.channel(NioSctpServerChannel.class)
     *    TCP 用 NioServerSocketChannel，SCTP 用 NioSctpServerChannel
     *
     * 2. ChannelInitializer 泛型：ChannelInitializer<SctpChannel>
     *    TCP 用 <SocketChannel>，SCTP 用 <SctpChannel>
     *
     * 3. Pipeline 新增 SctpChannelAdapter：
     *    在 HeartbeatHandler 之前（靠近 Head 侧）插入适配器
     *
     * 其余全部相同：EventLoopGroup 设置、SO_BACKLOG、SO_KEEPALIVE、
     * 超时参数、业务 Handler（HeartbeatHandler、WatchProtocolDecoder、
     * WatchDataHandler、WatchProtocolEncoder）。
     *
     * 【SO_BACKLOG 和 SO_KEEPALIVE 在 SCTP 下的行为】
     *
     * SO_BACKLOG（1024）：
     *   与 TCP 相同，控制 accept 队列长度。
     *   SCTP 的连接建立是四次握手（比 TCP 的三次握手多一次），
     *   稍微耗时更长，适当的 backlog 防止连接丢失。
     *
     * SO_KEEPALIVE（true）：
     *   SCTP 有自己的心跳机制（HB-chunks），比 TCP KeepAlive 更可靠。
     *   启用 SO_KEEPALIVE 提供额外的连接存活检测，双重保障。
     *
     * @return 启动好的 ServerBootstrap（注册为 Spring Bean，便于其他组件注入）
     * @throws InterruptedException 绑定端口过程中被中断（应用启动时极少发生）
     */
    @Bean(name = "nettySctpServer")
    public ServerBootstrap startNettySctpServer() throws InterruptedException {
        log.info("启动 Netty SCTP 服务器，监听端口: {}", port);
        log.info("SCTP 配置: bossThreads={}, workerThreads={}, idleTimeout={}s, writeIdleTimeout={}s",
                bossThreads, workerThreads, idleTimeout, writeIdleTimeout);

        // ─── 创建两组事件循环（与 TCP 完全相同的配置）────────────────
        bossGroup  = new NioEventLoopGroup(bossThreads);   // 接收新连接的线程组（1个线程）
        workerGroup = new NioEventLoopGroup(workerThreads); // 处理消息读写的线程组

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)

                // ═══ 差异点1：使用 SCTP 服务端通道类 ═══════════════════
                .channel(NioSctpServerChannel.class)          // TCP 是 NioServerSocketChannel
                // ═════════════════════════════════════════════════════════

                .option(ChannelOption.SO_BACKLOG, 1024)       // 同 TCP：accept 队列深度
                .childOption(ChannelOption.SO_KEEPALIVE, true) // 同 TCP：启用 KeepAlive

                // ═══ 差异点2：ChannelInitializer 泛型为 SctpChannel ════
                .childHandler(new ChannelInitializer<SctpChannel>() { // TCP 是 <SocketChannel>
                // ═════════════════════════════════════════════════════════
                    @Override
                    protected void initChannel(SctpChannel ch) {
                        ch.pipeline()
                            // Handler 1：空闲状态检测（与 TCP 完全相同）
                            // 读空闲 300s → HeartbeatHandler 关闭连接
                            // 写空闲 60s  → HeartbeatHandler 发心跳包
                            .addLast(new IdleStateHandler(idleTimeout, writeIdleTimeout, 0, TimeUnit.SECONDS))

                            // ═══ 差异点3：在 HeartbeatHandler 之前插入 SCTP 适配器 ═══
                            // 位置关键：SctpChannelAdapter 必须在 HeartbeatHandler 的左侧
                            // 原因（出站方向分析）：
                            //   HeartbeatHandler 写出 ByteBuf → 出站向左 → SctpChannelAdapter 拦截并包装 → SCTP 发出 ✓
                            //   如果适配器在右侧，HeartbeatHandler 的出站写就不会经过适配器！
                            .addLast(sctpChannelAdapter)         // SCTP ↔ ByteBuf 双向适配
                            // ═══════════════════════════════════════════════════════════

                            // Handler 3：心跳处理（与 TCP 完全复用，无需修改）
                            .addLast(heartbeatHandler)

                            // Handler 4：协议解码（与 TCP 完全复用）
                            // 注意：WatchProtocolDecoder 不能 @Sharable，每连接 new 一个新实例
                            // SCTP 是消息导向的，每个 SctpMessage 就是一条完整消息，
                            // 但 SctpChannelAdapter 解包后得到 ByteBuf，
                            // 解码器仍会用 '#' 检测边界（兼容处理，无副作用）
                            .addLast(new WatchProtocolDecoder())

                            // Handler 5：业务处理（与 TCP 完全复用）
                            // WatchDataHandler 根据协议号分发到对应的 handleXxx 方法
                            // 它写出 String/ByteBuf，SctpChannelAdapter 会在出站方向拦截并包装
                            .addLast(dataHandler)

                            // Handler 6：协议编码（与 TCP 完全复用）
                            // 如果 dataHandler 写出 WatchMessage 对象，由此编码器转为 ByteBuf
                            // 再由 SctpChannelAdapter 包装为 SctpMessage 发送
                            .addLast(protocolEncoder);
                    }
                });

        // 绑定端口并等待完成（同步等待，确保服务器真正启动后才继续 Spring 初始化）
        ChannelFuture future = bootstrap.bind(port).sync();
        log.info("Netty SCTP 服务器启动成功，监听端口: {}", port);

        // 注册关闭回调（异步，不阻塞）
        future.channel().closeFuture().addListener(f -> {
            log.info("Netty SCTP 服务器已关闭");
        });

        return bootstrap;
    }

    /**
     * 优雅关闭 SCTP 服务器
     *
     * Spring 容器关闭时（应用停止时）自动调用。
     * 与 TCP 版本的关闭逻辑完全相同：先关 workerGroup，再关 bossGroup。
     *
     * 关闭顺序的原因：
     *   1. 先关 workerGroup：停止处理新消息，等待正在处理的消息完成
     *   2. 再关 bossGroup：停止接受新的 SCTP 连接
     *   这样设备正在发送的最后一条健康数据不会丢失。
     */
    @PreDestroy
    public void shutdown() {
        log.info("正在关闭 Netty SCTP 服务器...");
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        log.info("Netty SCTP 服务器关闭完成");
    }
}
