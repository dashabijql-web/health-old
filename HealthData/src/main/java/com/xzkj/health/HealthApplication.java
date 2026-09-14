package com.xzkj.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║        徐矿集团健康管理系统 - 后端主启动入口                      ║
 * ╚══════════════════════════════════════════════════════════════╝
 *
 * 【整体架构图】
 *
 *   前端 Vue3 浏览器
 *       │  HTTP 请求 (端口 8080)
 *       ▼
 *   Spring Boot 后端
 *   ├── Controller 层  ← 接收 HTTP 请求，返回 JSON
 *   ├── Service 层     ← 业务逻辑处理
 *   ├── Mapper 层      ← 数据库操作（MyBatis-Plus）
 *   └── SQL Server 数据库
 *
 *   智能手表设备
 *       │  TCP 连接 (端口 9000)，协议格式: IW*协议号*参数#
 *       ▼
 *   Netty TCP 服务器
 *   ├── WatchProtocolDecoder  ← 解码原始字节 -> WatchMessage 对象
 *   ├── WatchDataHandler      ← 根据协议号分发业务处理
 *   └── DataProcessService    ← 将设备数据异步保存到数据库
 *
 * 【主要技术栈说明】
 *
 * | 技术          | 版本     | 用途                                |
 * |--------------|----------|-------------------------------------|
 * | Spring Boot  | 3.5.16   | 核心框架，Web服务、依赖注入            |
 * | Java         | 21       | 编程语言                             |
 * | MyBatis-Plus | 3.5.17   | 数据库ORM，简化SQL操作               |
 * | Sa-Token     | 1.45.0   | 登录认证、Token管理                  |
 * | Netty        | 4.1.x    | 高性能TCP网络框架，处理设备连接        |
 * | Druid        | 1.2.21   | 数据库连接池，提升DB连接效率          |
 * | SQL Server   | -        | 数据库                              |
 * | Lombok       | -        | 代码简化，自动生成getter/setter等     |
 *
 * 【Spring Boot 核心思想 - 新手必读】
 *
 * "约定优于配置"：只要按规范组织代码，框架会自动做大量配置工作。
 *
 * Bean 和依赖注入：
 * - Spring 容器管理所有 @Component/@Service/@Controller 标记的类（称为 Bean）
 * - 使用 @Autowired 可以让 Spring 自动把依赖的 Bean 注入进来，
 *   不需要手动 new 对象
 *
 * 【端口说明】
 * - 8080：HTTP 接口端口，前端 Vue 页面通过此端口调用 REST API
 * - 9000：TCP 协议端口，智能手表设备连接此端口上报健康数据
 *
 * 【Druid 监控控制台】
 * 默认关闭；如需启用，请通过 HEALTH_DRUID_ENABLED、DRUID_USERNAME、DRUID_PASSWORD 显式配置
 * 访问地址: http://localhost:8080/health/druid
 *
 * 【Actuator 健康检查】
 * 启动后访问: http://localhost:8080/health/actuator/health
 * 返回系统健康状态（数据库连接是否正常等）
 */
@Slf4j          // Lombok: 自动生成 log 变量，可直接用 log.info("消息") 打印日志
                // 底层使用 SLF4J + Logback，日志会按 application.yml 的配置输出

@EnableScheduling  // 开启 Spring 定时任务支持（MonthlyTableScheduler 需要）

@EnableAsync    // 开启 Spring 异步功能支持
                // 加了此注解后，方法上的 @Async 才能生效
                // @Async 方法会在独立线程池中执行，不阻塞调用者（主线程）
                // 本项目 DataProcessService 中的数据保存方法使用了 @Async，
                // 确保 Netty IO 线程不因数据库操作而阻塞

@SpringBootApplication  // 组合注解，等价于同时写了以下三个注解：
                        // @Configuration        ← 标记为配置类
                        // @EnableAutoConfiguration ← 开启自动配置（读取 META-INF/spring.factories）
                        // @ComponentScan        ← 扫描当前包及子包下所有 @Component
public class HealthApplication {
    /**
     * 程序主入口 - JVM 启动时执行此方法
     *
     * Spring Boot 启动流程（简化版）：
     * 1. 创建 SpringApplication 实例
     * 2. 读取 application.yml 配置
     * 3. 扫描所有带注解的类，创建 Bean 并放入 Spring 容器
     * 4. 自动配置（数据源、Web服务器、MyBatis等）
     * 5. 启动内嵌 Tomcat，监听 HTTP 8080 端口
     * 6. 执行 @PostConstruct 方法（Bean 初始化后的额外操作）
     * 7. NettyServerConfig 的 @Bean 方法被调用，启动 TCP 9000 端口
     * 8. 应用启动完成
     *
     * @param args 命令行参数（如 --spring.profiles.active=prod）
     */
    public static void main(String[] args) {
        SpringApplication.run(HealthApplication.class, args);
        log.info("=========================================");
        log.info("智能手表健康监测服务器启动成功!");
        log.info("HTTP管理端口: 8080 (访问: http://localhost:8080/health)");
        log.info("TCP协议端口: 9000 (设备连接: 127.0.0.1:9000)");
        log.info("SCTP协议端口: 9001 (设备连接: 127.0.0.1:9001)");
        log.info("协议格式: IW*协议号*参数#");
        log.info("=========================================");
    }
}
