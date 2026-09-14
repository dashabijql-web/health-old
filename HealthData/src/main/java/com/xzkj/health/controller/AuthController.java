package com.xzkj.health.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.xzkj.health.common.Result;
import com.xzkj.health.config.datasource.HealthDataSourceOverrideAuthorizer;
import com.xzkj.health.model.entity.SysUser;
import com.xzkj.health.service.SysUserService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║              认证控制器（登录/查询/退出，新手必读）                    ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * 【Controller 层的职责】
 *
 * 在 MVC（Model-View-Controller）架构中：
 *   Model（模型）：数据实体（HealthRecord、SysUser 等）
 *   View（视图）：前端页面（Vue 组件）
 *   Controller（控制器）：接收 HTTP 请求，调用 Service，返回 JSON 数据
 *
 * Controller 只做三件事：
 *   1. 接收并解析请求参数（@RequestBody、@RequestParam 等）
 *   2. 调用 Service 层方法执行业务逻辑
 *   3. 将结果封装成 Result 返回给前端
 *
 * Controller 不写业务逻辑，业务逻辑在 Service 层。
 *
 * 【@RestController 说明】
 *
 * @RestController = @Controller + @ResponseBody
 *   @Controller：标记为 Spring MVC 控制器
 *   @ResponseBody：方法返回值自动转 JSON 字符串（通过 Jackson 库）
 *
 * 例如：return Result.ok("登录成功", data)
 * 前端收到的是：{"code":200,"message":"登录成功","data":{...}}
 *
 * 【@RequestMapping("/auth") 说明】
 *
 * 类级别的 @RequestMapping 设置 URL 前缀。
 * 注意：application.yml 中设置了 context-path: /health
 * 所以实际 URL = /health + /auth + 方法上的路径
 *
 * 例如：
 *   登录接口：POST http://localhost:8080/health/auth/login
 *   用户信息：GET  http://localhost:8080/health/auth/info
 *   退出登录：POST http://localhost:8080/health/auth/logout
 *
 * 【Sa-Token 认证流程】
 *
 * 登录流程：
 *   1. 前端 POST /auth/login，带 {username, password} JSON
 *   2. 验证用户名密码（SysUserService.login）
 *   3. StpUtil.login(userId) → Sa-Token 生成 Token，存入 Redis/内存
 *   4. StpUtil.getTokenValue() → 获取 Token 字符串
 *   5. 返回 Token 给前端
 *
 * 后续请求：
 *   1. 前端每次请求在 Header 带：satoken: eyJ0...（Token 值）
 *   2. SaTokenConfig 的拦截器：StpUtil.checkLogin() 验证 Token
 *   3. 验证通过 → 执行 Controller；失败 → 返回 401
 *
 * 查询当前用户信息：
 *   1. StpUtil.getLoginIdAsLong() → 从 Token 中取出 userId
 *   2. 用 userId 查数据库，获取用户详情
 */
@Slf4j
@RestController
@RequestMapping("/auth")  // 该 Controller 下所有接口的 URL 前缀为 /auth
public class AuthController {

    @Autowired
    private SysUserService userService;  // 注入用户服务

    @Autowired
    private HealthDataSourceOverrideAuthorizer dataSourceOverrideAuthorizer;

    /**
     * 用户登录接口
     *
     * HTTP 方法：POST
     * 完整 URL：POST /health/auth/login
     *
     * 请求体（JSON）：
     *   { "username": "admin", "password": "admin123" }
     *
     * 成功响应：
     *   {
     *     "code": 200,
     *     "message": "登录成功",
     *     "data": {
     *       "token": "eyJhbGci...",  // Sa-Token 生成的 Token，前端保存在 Cookie 中
     *       "name": "管理员",         // 显示名（优先用 realName）
     *       "avatar": "",            // 头像 URL
     *       "roles": ["user"],        // 角色列表（用于前端权限控制）
     *       "buttons": ["user:add"], // 按钮权限码（用于按钮显示/隐藏）
     *       "routes": ["health:dashboard"] // 路由权限码（用于菜单过滤）
     *     }
     *   }
     *
     * 失败响应：
     *   { "code": 500, "message": "用户不存在" }
     *   { "code": 500, "message": "密码错误" }
     *   { "code": 500, "message": "账号已禁用" }
     *
     * 【@RequestBody 说明】
     *
     * @RequestBody LoginDTO dto：
     * 将请求体中的 JSON 字符串自动解析为 LoginDTO 对象（通过 Jackson）
     * 需要请求头 Content-Type: application/json
     *
     * @param dto 登录请求对象（用户名 + 密码）
     * @return 包含 Token 和用户基本信息的响应
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginDTO dto) {
        try {
            // 第一步：验证用户名和密码（SysUserServiceImpl.login 中校验）
            // 如果验证失败，userService.login 会抛出 RuntimeException
            SysUser user = userService.login(dto.getUsername(), dto.getPassword());

            // 第二步：Sa-Token 登录（核心！只需这一行）
            // 内部：生成 Token，与 userId 绑定，默认存入内存（可配置 Redis）
            StpUtil.login(user.getId());

            // 第三步：构建返回数据
            Map<String, Object> data = new HashMap<>();
            data.put("token", StpUtil.getTokenValue());   // 生成的 Token 字符串
            // 优先使用真实姓名，没有则用用户名
            data.put("name", user.getRealName() != null ? user.getRealName() : user.getUsername());
            data.put("avatar", user.getAvatar() != null ? user.getAvatar() : "");  // 头像 URL
            data.put("roles", userService.getUserRoles(user.getId()));              // 角色列表
            data.put("buttons", userService.getUserButtons(user.getId()));          // 按钮权限
            data.put("routes", userService.getUserRoutes(user.getId()));            // 路由权限
            data.put("canSwitchDataSource", dataSourceOverrideAuthorizer.canSwitchDataSource(user.getUsername()));

            log.info("登录成功: {}", dto.getUsername());
            return Result.ok("登录成功", data);

        } catch (RuntimeException e) {
            // 捕获 SysUserServiceImpl.login 抛出的业务异常（用户不存在、密码错误等）
            // 直接用异常消息返回给前端（已过滤敏感信息，没有堆栈）
            return Result.error(e.getMessage());
        } catch (Exception e) {
            // 捕获其他未预期异常（数据库连接失败等）
            log.error("登录异常", e);
            return Result.error("系统错误");
        }
    }

    /**
     * 获取当前登录用户信息
     *
     * HTTP 方法：GET
     * 完整 URL：GET /health/auth/info
     * 需要在请求头携带 Token：satoken: eyJ0...
     *
     * 用途：
     *   1. 前端页面刷新后，用 Cookie 中的 Token 恢复用户状态
     *   2. 心跳检测（heartbeat.js 每30秒调用此接口，确认 Session 有效）
     *
     * 成功响应（同登录成功的 data 部分，不含 token）：
     *   { "code": 200, "data": { "name": "管理员", "roles": [...], ... } }
     *
     * 失败（未登录或 Token 过期）：
     *   { "code": 401, "message": "未登录或失效" }
     *
     * @return 当前登录用户的基本信息和权限数据
     */
    @GetMapping("/info")
    public Result<Map<String, Object>> getInfo() {
        try {
            // checkLogin() 验证 Token 有效性
            // 如果 Token 不存在或已过期，抛出 NotLoginException
            StpUtil.checkLogin();

            // 从 Token 中取出登录时的 userId（StpUtil.login(userId) 时绑定的）
            long userId = StpUtil.getLoginIdAsLong();
            SysUser user = userService.getById(userId);  // 查询用户详情

            if (user == null) {
                return Result.error("用户不存在");
            }

            Map<String, Object> data = new HashMap<>();
            data.put("name", user.getRealName() != null ? user.getRealName() : user.getUsername());
            data.put("avatar", user.getAvatar() != null ? user.getAvatar() : "");
            data.put("roles", userService.getUserRoles(userId));
            data.put("buttons", userService.getUserButtons(userId));
            data.put("routes", userService.getUserRoutes(userId));
            data.put("canSwitchDataSource", dataSourceOverrideAuthorizer.canSwitchDataSource(user.getUsername()));

            return Result.ok("获取成功", data);

        } catch (Exception e) {
            // 任何异常（NotLoginException、用户查询失败等）都返回 401
            // 前端收到 401 后会自动跳转到登录页
            return Result.error(401, "未登录或失效");
        }
    }

    /**
     * 退出登录
     *
     * HTTP 方法：POST
     * 完整 URL：POST /health/auth/logout
     * 注意：此接口已在 SaTokenConfig 白名单中，不需要 Token 也可调用（幂等）
     *
     * 处理：
     *   StpUtil.logout() 销毁当前 Token（从内存/Redis 中删除）
     *   下次携带此 Token 请求 /auth/info 时，会返回 401
     *
     * @return 退出成功的响应（固定不变）
     */
    @PostMapping("/logout")
    public Result<String> logout(HttpServletRequest request) {
        String tokenValue = resolveLogoutToken(request);
        if (tokenValue == null) {
            StpUtil.logout();
        } else {
            StpUtil.logoutByTokenValue(tokenValue);
        }
        return Result.ok("退出成功");
    }

    static String resolveLogoutToken(HttpServletRequest request) {
        String headerToken = request.getHeader("satoken");
        if (headerToken != null && !headerToken.isBlank()) {
            return headerToken;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if ("satoken".equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }
}

/**
 * 登录请求参数 DTO（Data Transfer Object）
 *
 * 【为什么要单独定义 DTO？】
 *
 * 不直接用 SysUser 实体类接收登录参数，原因：
 *   1. SysUser 字段很多（头像、部门ID等），混入请求参数语义不清
 *   2. SysUser 的 password 字段是加密后的值，登录时传的是明文，名称语义冲突
 *   3. DTO 只包含请求需要的字段，更清晰安全
 *
 * 通常 DTO 放在独立的 dto 包中，这里为简洁写在同文件末尾。
 */
@Data  // Lombok: 生成 getter/setter（Jackson 需要 getter 来序列化/反序列化）
class LoginDTO {
    /** 登录用户名 */
    private String username;
    /** 登录密码（明文，传输后立即用 BCrypt 校验，不存储） */
    private String password;
}
