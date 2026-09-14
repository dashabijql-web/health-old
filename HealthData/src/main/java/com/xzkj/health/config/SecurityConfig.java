package com.xzkj.health.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║              Spring Security 配置（新手必读）                       ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * 【这个类的核心作用】
 *
 * Spring Security 是 Spring 生态中的安全框架，默认会拦截所有 HTTP 请求，
 * 要求用户登录后才能访问（它有自己的登录页面和 Session 机制）。
 *
 * 但本项目使用 Sa-Token 作为认证框架（轻量级、更灵活），
 * 因此需要"关掉" Spring Security 的默认认证行为，
 * 同时保留它的密码加密工具（BCryptPasswordEncoder）。
 *
 * 【Spring Security 6 的变化（重要！）】
 *
 * Spring Security 5.x 及更早版本写法（已废弃）：
 *   public class SecurityConfig extends WebSecurityConfigurerAdapter {
 *       @Override
 *       protected void configure(HttpSecurity http) throws Exception {
 *           http.csrf().disable()...
 *       }
 *   }
 *
 * Spring Security 6.x（本项目用的）新写法：
 *   不再继承任何类，改为把 SecurityFilterChain 注册为 @Bean
 *   这是"组合优于继承"设计原则的实践
 *
 * 如果你在网上找到的代码还在继承 WebSecurityConfigurerAdapter，
 * 那是 Spring Boot 2.x 的老写法，在 Spring Boot 3.x 中会报编译错误。
 *
 * 【@Configuration 注解说明】
 *
 * @Configuration 标记这个类是一个配置类，相当于 XML 配置文件的 Java 版本。
 * Spring 启动时会扫描所有 @Configuration 类，执行其中的 @Bean 方法，
 * 将返回的对象注册到 Spring 容器中供其他地方注入使用。
 *
 * 【@EnableWebSecurity 注解说明】
 *
 * 明确开启 Spring Security 的 Web 安全功能（加载 SecurityFilterChain 等组件）。
 * 在 Spring Boot 中其实可以省略（自动配置会处理），但显式写出来更清晰。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 密码加密器 Bean（BCrypt 算法）
     *
     * 【BCrypt 是什么？】
     *
     * BCrypt 是一种专门设计用于密码存储的哈希算法，特点：
     *   1. 不可逆：从哈希值无法推算出原始密码
     *   2. 加盐（Salt）：每次加密结果不同，防止彩虹表攻击
     *   3. 慢速设计：计算耗时可调，让暴力破解代价极高
     *      （普通 MD5/SHA1 一秒可以尝试数十亿次，BCrypt 一秒只能试几百次）
     *
     * 【使用方式】
     *
     * 加密密码（注册/修改密码时）：
     *   String encoded = passwordEncoder.encode("123456");
     *   // 结果如: $2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi
     *
     * 校验密码（登录时）：
     *   boolean ok = passwordEncoder.matches("123456", user.getPassword());
     *   // 不能用 .equals()! 因为同一密码每次加密结果不同
     *
     * 【注册为 @Bean 的原因】
     *
     * 这样 Spring 容器管理这个对象，SysUserServiceImpl 等地方可以通过
     * @Autowired 注入，不需要每次手动 new BCryptPasswordEncoder()
     *
     * @return BCrypt 密码编码器实例
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Spring Security 过滤链配置
     *
     * 【SecurityFilterChain 是什么？】
     *
     * Spring Security 通过一系列过滤器（Filter Chain）处理 HTTP 请求。
     * 这里的配置决定哪些请求被拦截、哪些放行、如何处理认证。
     *
     * 【本项目的配置策略：关闭 Spring Security 默认认证，交给 Sa-Token】
     *
     * 1. 禁用 CSRF（Cross-Site Request Forgery，跨站请求伪造）防护
     *    原因：本项目使用 Token 认证（Sa-Token），不依赖浏览器 Cookie/Session，
     *          CSRF 攻击对 Token 认证方式无效，禁用可以避免接口调用时需要携带 CSRF Token
     *
     * 2. 禁用 frameOptions（X-Frame-Options HTTP 头）
     *    原因：允许页面在 iframe 中展示（如 Druid 监控控制台可能用 iframe）
     *          Spring Security 默认会设置 X-Frame-Options: DENY，禁止任何 iframe 嵌入
     *
     * 3. 放行所有请求（anyRequest().permitAll()）
     *    原因：Spring Security 的认证拦截全部关闭，认证工作完全由 Sa-Token 负责
     *    Sa-Token 的拦截器在 SaTokenConfig.java 中配置
     *
     * 【HttpSecurity 方法链（Lambda 风格）】
     *
     * Spring Security 6 使用 Lambda 表达式配置（旧版用方法链：.csrf().disable()）：
     *   .csrf(AbstractHttpConfigurer::disable)
     * 等价于 Lambda：
     *   .csrf(csrf -> csrf.disable())
     *
     * @param http Spring 注入的 HttpSecurity 对象，用于配置 HTTP 安全规则
     * @return 构建好的 SecurityFilterChain
     * @throws Exception 配置过程中的异常（由框架处理）
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 禁用 CSRF 防护（Token 认证不需要）
            .csrf(AbstractHttpConfigurer::disable)
            // 禁用 frameOptions（允许 iframe 嵌入，如 Druid 监控页）
            .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable))
            // 放行所有请求（认证由 Sa-Token 负责，Spring Security 不做拦截）
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
