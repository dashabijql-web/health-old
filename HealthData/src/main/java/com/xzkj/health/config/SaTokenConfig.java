package com.xzkj.health.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║              Sa-Token 认证配置 & 跨域配置（新手必读）               ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * 本类做了两件事：
 *   1. 配置 Sa-Token 拦截器（认证拦截）
 *   2. 配置 CORS 跨域（允许前端 Vue 访问后端 API）
 *
 * ─────────────────────────────────────────────────────────────────
 * 一、Sa-Token 是什么？
 * ─────────────────────────────────────────────────────────────────
 *
 * Sa-Token 是一个轻量级 Java 认证授权框架（比 Spring Security 更简单）。
 *
 * 核心 API（超简单）：
 *   StpUtil.login(userId)          // 登录（生成 Token，存入 Session）
 *   StpUtil.getLoginIdAsLong()     // 获取当前登录的用户 ID
 *   StpUtil.checkLogin()           // 校验是否登录（未登录抛异常）
 *   StpUtil.logout()               // 注销登录
 *   StpUtil.getTokenValue()        // 获取当前 Token 字符串
 *
 * 流程：
 *   用户登录 → StpUtil.login(userId) → 生成 Token → 返回给前端
 *   前端请求 → 在请求头带 Token（satoken: xxxx）→ Sa-Token 拦截器验证
 *   Token 有效 → 继续执行 Controller → Token 无效 → 抛出 NotLoginException
 *
 * ─────────────────────────────────────────────────────────────────
 * 二、什么是跨域（CORS）？
 * ─────────────────────────────────────────────────────────────────
 *
 * 浏览器有同源策略：A 域名的页面不能直接请求 B 域名的接口。
 * 例如：
 *   前端运行在 http://localhost:3000
 *   后端运行在 http://localhost:8080
 *   这两个端口不同，就属于"跨域"，浏览器默认会拒绝这种请求。
 *
 * 解决方案：后端设置 CORS 响应头，告诉浏览器"我允许跨域访问"
 *
 * ─────────────────────────────────────────────────────────────────
 * 三、WebMvcConfigurer 是什么？
 * ─────────────────────────────────────────────────────────────────
 *
 * WebMvcConfigurer 是 Spring MVC 提供的配置接口。
 * 实现它可以扩展/覆盖 Spring MVC 的默认行为，比如：
 *   - addInterceptors()：注册自定义拦截器
 *   - addCorsMappings()：配置跨域（另一种方式）
 *   - addFormatters()：注册自定义类型转换器
 *
 * 本类实现 WebMvcConfigurer，覆盖 addInterceptors() 方法来注册 Sa-Token 拦截器。
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    @Value("${health.security.cors.allowed-origin-patterns:http://localhost:9528,http://127.0.0.1:9528,http://localhost:9530,http://127.0.0.1:9530}")
    private String allowedOriginPatterns;

    @Value("${health.security.cors.allow-credentials:true}")
    private boolean allowCredentials;

    /**
     * 注册 Sa-Token 拦截器
     *
     * 【什么是拦截器（Interceptor）？】
     *
     * 拦截器是 Spring MVC 的切面机制：在请求到达 Controller 之前/之后执行代码。
     * 调用链：请求 → Filter（过滤器）→ DispatcherServlet → Interceptor → Controller
     *
     * Sa-Token 的 SaInterceptor 就是一个拦截器，在请求到达 Controller 前检查 Token。
     *
     * 【路由规则配置说明】
     *
     * SaRouter.match("/**")                          → 匹配所有路径
     * .notMatch("/auth/login", "/auth/logout", "/error") → 排除这些路径（白名单，不需要登录）
     * .check(r -> StpUtil.checkLogin())              → 对匹配的路径执行登录校验
     *
     * 白名单解释：
     *   /auth/login  → 登录接口，没登录也要能访问
     *   /auth/logout → 退出登录，Sa-Token 允许未登录状态调用（幂等）
     *   /error       → Spring Boot 错误处理路径，需要放行
     *
     * 【addPathPatterns / excludePathPatterns】
     *
     * 这是 Spring MVC 的拦截器路径配置（更外层的过滤）：
     *   addPathPatterns("/**")      → 拦截器作用于所有路径
     *   excludePathPatterns("/error") → Spring 错误路径不经过拦截器
     *
     * @param registry 拦截器注册表，用于添加拦截器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(ignored -> {
            Object source = SaHolder.getRequest().getSource();
            if (source instanceof HttpServletRequest request && shouldCheckLogin(request)) {
                StpUtil.checkLogin();
            }
        }))
        .addPathPatterns("/**")       // 此拦截器拦截所有路径
        .excludePathPatterns("/error"); // 排除 Spring 内置错误处理路径
    }

    boolean shouldCheckLogin(HttpServletRequest request) {
        DispatcherType dispatcherType = request.getDispatcherType();
        if (dispatcherType == DispatcherType.ASYNC || dispatcherType == DispatcherType.ERROR) {
            return false;
        }

        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }

        return !"/auth/login".equals(path)
                && !"/auth/logout".equals(path)
                && !"/error".equals(path);
    }

    /**
     * 跨域过滤器 Bean（CORS 配置）
     *
     * 【为什么用 CorsFilter 而不是 addCorsMappings？】
     *
     * Spring MVC 提供了 addCorsMappings() 方法配置跨域，但在有 Spring Security 的情况下
     * 可能会失效（Security 过滤器在 MVC 拦截器之前执行，OPTIONS 预检请求可能被 Security 拦截）。
     *
     * 用 CorsFilter（Servlet Filter）优先级更高，能在 Spring Security 之前处理跨域，
     * 避免 CORS 配置失效的问题。
     *
     * 【浏览器 CORS 预检请求（Preflight）】
     *
     * 当发起跨域的"复杂请求"（如 POST + 自定义 Header）时，浏览器会先发一个 OPTIONS 请求
     * 询问服务器是否允许跨域，服务器返回 CORS 响应头后，浏览器才发真正的请求。
     * setMaxAge(3600) 表示预检结果缓存 3600 秒，避免每次请求都发一次 OPTIONS。
     *
     * 【开发环境 vs 生产环境】
     *
     * addAllowedOriginPattern("*") 允许所有域名，适合开发阶段。
     * 生产环境建议替换为具体域名，如：
     *   config.addAllowedOrigin("https://your-frontend-domain.com");
     *
     * @return 配置好的 CorsFilter
     */
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        for (String pattern : allowedOriginPatterns.split(",")) {
            if (!pattern.isBlank()) {
                config.addAllowedOriginPattern(pattern.trim());
            }
        }

        config.setAllowCredentials(allowCredentials);

        // 允许所有请求头（包括自定义的 satoken 头）
        config.addAllowedHeader("*");

        // 允许所有 HTTP 方法（GET、POST、PUT、DELETE、OPTIONS 等）
        config.addAllowedMethod("*");

        // 预检请求（OPTIONS）的有效期：3600 秒（1小时）
        // 在此期间内，浏览器不会重复发送 OPTIONS 预检请求
        config.setMaxAge(3600L);

        // 将跨域配置应用到所有路径
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);  // /** 表示所有路径

        return new CorsFilter(source);
    }
}
