package com.xzkj.health.config;

import com.xzkj.health.handler.HeartbeatHandler;
import com.xzkj.health.handler.WatchDataHandler;
import com.xzkj.health.protocol.WatchProtocolDecoder;
import com.xzkj.health.protocol.WatchProtocolEncoder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
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
 * ║              Netty TCP 服务器配置（新手必读）                        ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * 【为什么需要 Netty？】
 *
 * 智能手表通过 TCP 协议连接服务器上报健康数据，使用的是私有二进制/文本协议。
 * 而 Spring Boot 的 Tomcat 只处理 HTTP 请求，无法处理原始 TCP 连接。
 *
 * Netty 是专门用于构建高性能网络服务器的框架，特别适合处理大量设备的 TCP 长连接。
 * 本项目用 Netty 在端口 9000 监听智能手表的 TCP 连接。
 *
 * 【Netty 的 NIO 事件循环模型（重要概念）】
 *
 * 传统 BIO（阻塞 IO）：每个连接一个线程，连接多了线程爆炸
 *   设备1 → 线程1（阻塞等待数据）
 *   设备2 → 线程2（阻塞等待数据）
 *   ...
 *   设备1000 → 线程1000（内存不够）
 *
 * NIO（非阻塞 IO）：少量线程处理大量连接（事件驱动）
 *   1个 bossThread → 专门 accept 新连接
 *   4个 workerThread → 轮流处理所有连接的数据读写
 *   数据来了才处理，没数据就让出 CPU 给其他连接
 *
 * 这就是 Netty 高性能的秘密：用极少的线程处理海量连接。
 *
 * 【Netty 组件说明】
 *
 * ServerBootstrap：服务器启动器，配置所有参数后调用 bind() 开始监听
 *
 * EventLoopGroup：事件循环组（线程组），有两个：
 *   bossGroup：只负责接收新 TCP 连接（accept），通常 1 个线程就够
 *   workerGroup：负责处理已建立连接的数据读写，线程数 = CPU 核数 × 2 左右
 *
 * NioEventLoopGroup：基于 NIO 的事件循环，使用 Selector 复用多路 IO
 *
 * ChannelInitializer：当新连接建立时，为该连接初始化处理器管道（Pipeline）
 *
 * Pipeline（管道/处理器链）：
 *   每个连接有一个 Pipeline，数据经过每个 Handler 处理
 *   入站（设备→服务器）：IdleStateHandler → HeartbeatHandler → Decoder → DataHandler
 *   出站（服务器→设备）：Encoder → （发送到设备）
 *
 * 【TCP 选项说明】
 *
 * SO_BACKLOG = 1024：TCP 连接队列长度。
 *   在 accept() 处理新连接之前，最多允许 1024 个连接排队等待
 *
 * TCP_NODELAY = true：禁用 Nagle 算法。
 *   Nagle 算法会将小数据包合并延迟发送以提高效率，
 *   但会增加延迟（对实时性要求高的场景不适合）
 *   禁用后，每次 write 立即发送，降低延迟
 *
 * SO_KEEPALIVE = true：启用 TCP 层的 KeepAlive。
 *   操作系统会定期发送探测包检测连接是否还活着，
 *   防止因网络问题导致的"死连接"（客户端崩溃但连接未释放）
 *
 * 【@Value 注解说明】
 *
 * @Value("${netty.server.port:9000}") 从 application.yml 读取配置值
 * 格式：${配置键:默认值}，如果配置文件没有这个键，使用默认值
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "netty.server.enabled", havingValue = "true", matchIfMissing = true)
public class NettyServerConfig {

    /**
     * TCP 监听端口（从配置文件读取，默认 9000）
     * 智能手表通过此端口连接，发送健康数据
     */
    @Value("${netty.server.port:9000}")
    private int port;

    /**
     * Boss 线程数（负责 accept 新连接，默认 1）
     * 通常 1 个线程就足够处理连接建立，不需要更多
     */
    @Value("${netty.server.boss-threads:1}")
    private int bossThreads;

    /**
     * Worker 线程数（负责数据读写，默认 4）
     * 建议设置为 CPU 核数 × 2，根据实际设备数量调整
     */
    @Value("${netty.server.worker-threads:4}")
    private int workerThreads;

    /**
     * 读空闲超时时间（秒）
     * 设备超过此时间没有发送任何数据，视为断线，关闭连接
     * 默认 300 秒（5分钟）
     */
    @Value("${netty.server.idle-timeout:300}")
    private int idleTimeout;

    /**
     * 写空闲超时时间（秒）
     * 服务器超过此时间没有向设备发送任何数据，主动发送心跳包
     * 默认 60 秒
     */
    @Value("${netty.server.write-idle-timeout:60}")
    private int writeIdleTimeout;

    @Autowired
    private HeartbeatHandler heartbeatHandler;  // 心跳检测处理器

    @Autowired
    private WatchDataHandler dataHandler;        // 业务数据处理器

    @Autowired
    private WatchProtocolEncoder protocolEncoder; // 协议编码器（出站）

    /**
     * 保存 EventLoopGroup 引用，用于关闭时优雅释放资源
     */
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    /**
     * 启动 Netty TCP 服务器（注册为 Spring Bean）
     *
     * 【为什么用 @Bean 而不是 @PostConstruct？】
     *
     * @Bean 方法的返回值会被注册到 Spring 容器，其他组件可以注入 ServerBootstrap
     * @PostConstruct 适合只初始化不返回值的场景
     * 这里选 @Bean 是为了让 Spring 能管理 Netty 服务器的生命周期
     *
     * 【future.channel().closeFuture().addListener 说明】
     *
     * Netty 是异步的，bind() 返回一个 ChannelFuture（异步任务的占位符）
     * .sync() 表示同步等待 bind 完成（确保服务器真正启动后才继续）
     * closeFuture().addListener() 注册服务器关闭时的回调（打印日志）
     *
     * @return 配置好的 ServerBootstrap 对象
     * @throws InterruptedException 如果绑定端口过程中被中断
     */
    @Bean
    public ServerBootstrap startNettyServer() throws InterruptedException {
        log.info("启动Netty服务器，监听端口: {}", port);
        log.info("配置参数: bossThreads={}, workerThreads={}, idleTimeout={}s, writeIdleTimeout={}s",
                bossThreads, workerThreads, idleTimeout, writeIdleTimeout);

        // 创建两个 EventLoopGroup
        bossGroup = new NioEventLoopGroup(bossThreads);    // 接收连接的线程组
        workerGroup = new NioEventLoopGroup(workerThreads); // 处理数据的线程组

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                // 使用 NIO 的 ServerSocketChannel
                .channel(NioServerSocketChannel.class)
                // 服务器 TCP 选项
                .option(ChannelOption.SO_BACKLOG, 1024)          // 连接队列长度
                // 子连接（每个设备）的 TCP 选项
                .childOption(ChannelOption.TCP_NODELAY, true)     // 禁用 Nagle，降低延迟
                .childOption(ChannelOption.SO_KEEPALIVE, true)    // 启用 TCP KeepAlive
                // 当新连接建立时，初始化该连接的处理器管道
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        // 处理器顺序非常重要！数据按顺序经过每个处理器
                        ch.pipeline()
                            // 1. 空闲检测：监控读空闲和写空闲
                            //    readIdleTime=300s, writeIdleTime=60s, allIdleTime=0(不检测)
                            .addLast(new IdleStateHandler(idleTimeout, writeIdleTimeout, 0, TimeUnit.SECONDS))
                            // 2. 心跳处理：读空闲→断连，写空闲→发心跳包
                            .addLast(heartbeatHandler)
                            // 3. 协议解码：字节流 → WatchMessage 对象（每次new，不能共享！）
                            //    ByteToMessageDecoder 有状态，不能 @Sharable，所以每次 new
                            .addLast(new WatchProtocolDecoder())
                            // 4. 业务处理：根据协议号分发处理（接收 WatchMessage）
                            .addLast(dataHandler)
                            // 5. 协议编码：WatchMessage 对象 → 字节流（出站）
                            .addLast(protocolEncoder);
                    }
                });

        // bind(port).sync() 异步绑定端口并等待完成
        ChannelFuture future = bootstrap.bind(port).sync();

        // 注册服务器关闭回调（异步，不阻塞当前线程）
        future.channel().closeFuture().addListener(f -> {
            log.info("Netty服务器已关闭");
        });

        return bootstrap;
    }

    /**
     * 优雅关闭 Netty 服务器
     *
     * 【@PreDestroy 注解说明】
     *
     * @PreDestroy 在 Spring 容器销毁此 Bean 之前调用（应用关闭时）
     * 等价于生命周期：@PostConstruct → 使用 Bean → @PreDestroy → 销毁
     *
     * 【为什么需要 shutdownGracefully？】
     *
     * 直接 kill 进程会导致：
     *   - 正在处理的数据丢失
     *   - 已连接的设备收到 RST（连接重置），需要重新连接
     *   - 线程池中的任务被强制中断
     *
     * shutdownGracefully() 会：
     *   1. 停止接受新连接
     *   2. 等待当前正在处理的数据完成
     *   3. 再关闭线程池
     *
     * workerGroup 先关（先停止处理数据），再关 bossGroup（再停止接收连接）
     */
    @PreDestroy
    public void shutdown() {
        log.info("正在关闭Netty服务器...");

        if (workerGroup != null) {
            workerGroup.shutdownGracefully();  // 优雅关闭 worker 线程组
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();    // 优雅关闭 boss 线程组
        }
        log.info("Netty服务器关闭完成");
    }
}
