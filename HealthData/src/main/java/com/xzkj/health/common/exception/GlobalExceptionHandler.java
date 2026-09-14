package com.xzkj.health.common.exception;

import cn.dev33.satoken.exception.NotLoginException;
import com.xzkj.health.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║                全局异常处理器（新手必读）                           ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * 【问题背景：没有全局异常处理器会怎样？】
 *
 * 没有这个类时，如果 Controller 或 Service 中抛出了未被 catch 的异常，
 * Spring 会返回 HTTP 500 错误，前端收到的可能是：
 *   - 一个 HTML 格式的错误页面
 *   - 或者：{"timestamp":"...","status":500,"error":"Internal Server Error"}
 *
 * 这种格式前端无法统一处理（与 Result 格式不一致），用户也看不到有意义的错误信息。
 *
 * 【解决方案：全局异常处理器】
 *
 * 这个类使用 @RestControllerAdvice 注解，作用是：
 *   拦截所有 Controller 层抛出的未处理异常 → 转换为 Result 格式 → 返回给前端
 *
 * 这样无论哪里抛出异常，前端都会收到统一格式的 JSON：
 *   { "code": 500, "message": "用户不存在", "data": null }
 *
 * 【@RestControllerAdvice 注解说明】
 *
 * @RestControllerAdvice = @ControllerAdvice + @ResponseBody
 *   - @ControllerAdvice：对所有 Controller 生效的"增强类"
 *   - @ResponseBody：方法返回值自动转 JSON（而不是跳转页面）
 *
 * 【@ExceptionHandler 注解说明】
 *
 * @ExceptionHandler(XxxException.class) 表示：
 * 当有 XxxException 类型的异常抛出时，调用这个方法处理。
 *
 * 异常处理优先级：越具体的类型优先级越高：
 *   BusinessException > Exception（前者更具体，先匹配）
 *
 * 【异常处理流程】
 *
 *   Controller 方法抛出异常
 *       ↓
 *   Spring 检查 GlobalExceptionHandler 中是否有匹配的 @ExceptionHandler
 *       ↓ 有匹配
 *   调用对应的处理方法
 *       ↓
 *   方法返回 Result 对象 → 自动转 JSON → 发给前端
 */
@Slf4j                  // Lombok: 自动生成 log 变量，可以 log.warn(...) 记录日志
@RestControllerAdvice   // 标记为全局异常处理器（作用于所有 @RestController）
public class GlobalExceptionHandler {

    /**
     * 处理业务异常（BusinessException）
     *
     * 触发场景：Service/Controller 中 throw new BusinessException("xxx") 时
     *
     * 处理策略：
     *   - 用 log.warn 记录警告日志（业务异常不算严重，用 warn 级别，不用 error）
     *   - 从异常对象取出 code 和 message，构建 Result.error 返回前端
     *
     * 示例响应：
     *   { "code": 500, "message": "用户不存在", "data": null }
     *   { "code": 403, "message": "账号已被禁用", "data": null }
     *
     * @param e 捕获到的 BusinessException 对象
     * @return 统一格式的错误响应
     */
    @ExceptionHandler(NotLoginException.class)
    public Result<String> handleNotLogin(NotLoginException e) {
        log.warn("未登录或token已失效: {}", e.getMessage());
        return Result.error(401, "未登录或登录已过期，请重新登录");
    }

    @ExceptionHandler(BusinessException.class)
    public Result<String> handleBusiness(BusinessException e) {
        // warn 级别：业务规则违反不算系统故障，不需要 error 级别
        log.warn("业务异常: {}", e.getMessage());
        // 使用 BusinessException 中携带的 code 和 message 构建响应
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理参数校验异常（@Valid 注解触发）
     *
     * 触发场景：
     *   Controller 方法参数上有 @Valid 注解，且传入的 JSON 不符合校验规则时
     *   例如：@NotBlank、@Min、@Max、@Email 等 JSR-303 校验注解失败
     *
     * 示例：
     *   public Result<?> addUser(@RequestBody @Valid UserDTO dto)
     *   当 dto.username 为空时，自动触发此处理方法
     *
     * 处理策略：
     *   从 BindingResult 中取出第一个字段错误的默认消息
     *   返回 400 状态码（客户端参数错误）
     *
     * 示例响应：
     *   { "code": 400, "message": "用户名不能为空", "data": null }
     *
     * @param e Spring Validation 框架抛出的异常，包含所有字段错误信息
     * @return 统一格式的参数错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<String> handleValidation(MethodArgumentNotValidException e) {
        // getBindingResult() 包含所有校验失败的字段信息
        // getFieldError() 取第一个失败的字段（通常只展示第一个错误）
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = fieldError != null ? fieldError.getDefaultMessage() : "参数校验失败";
        log.warn("参数校验失败: {}", msg);
        return Result.error(400, msg);  // 400 = Bad Request
    }

    /**
     * 处理缺少必填请求参数的异常
     *
     * 触发场景：
     *   Controller 方法用 @RequestParam(required = true) 标注了参数（默认就是必填），
     *   但前端请求的 URL 中没有传这个参数
     *
     * 示例：
     *   @GetMapping("/user") public Result<?> getUser(@RequestParam Long id)
     *   当访问 GET /user 而不带 id 参数时，触发此异常
     *
     * 示例响应：
     *   { "code": 400, "message": "缺少必填参数: id", "data": null }
     *
     * @param e Spring 框架抛出的缺少请求参数异常
     * @return 统一格式的参数缺少错误响应
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<String> handleMissingParam(MissingServletRequestParameterException e) {
        // getParameterName() 返回缺少的参数名（如 "id"、"pageNum" 等）
        String msg = "缺少必填参数: " + e.getParameterName();
        log.warn(msg);
        return Result.error(400, msg);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<String> handleIllegalArgument(IllegalArgumentException e) {
        String message = (e.getMessage() == null || e.getMessage().isBlank()) ? "请求参数不合法" : e.getMessage();
        log.warn("非法参数异常: {}", message);
        return Result.error(400, message);
    }

    @ExceptionHandler({TaskRejectedException.class, RejectedExecutionException.class})
    public Result<String> handleTaskRejected(Exception e) {
        log.warn("异步任务被拒绝: {}", e.getMessage());
        return Result.error(503, "服务繁忙，请稍后重试");
    }

    @ExceptionHandler({CompletionException.class, ExecutionException.class})
    public Result<String> handleAsyncWrapper(Exception e) {
        Throwable cause = e.getCause();
        if (cause instanceof BusinessException businessException) {
            return handleBusiness(businessException);
        }
        if (cause instanceof IllegalArgumentException illegalArgumentException) {
            return handleIllegalArgument(illegalArgumentException);
        }
        log.error("异步任务异常: ", e);
        return Result.error("服务器内部错误，请稍后重试");
    }

    /**
     * 处理静态资源404异常（NoResourceFoundException）
     *
     * 触发场景：
     *   前端使用 Vue Router hash 模式，但浏览器刷新时可能会直接请求路径（如 /heart-rate/realtime）
     *   Spring Boot 找不到对应的静态资源，抛出 NoResourceFoundException
     *
     * 处理策略：
     *   - 静态资源404是正常现象，不应该触发"服务器内部错误"提示
     *   - 返回 null 或空 Result，避免前端显示错误弹窗
     *   - 日志级别为 DEBUG（不影响正常业务日志）
     *
     * @param e Spring 6.x 新增的资源未找到异常
     * @return null（不触发前端错误提示）
     */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public Result<String> handleNoResourceFound(org.springframework.web.servlet.resource.NoResourceFoundException e) {
        // DEBUG 级别：这不是真正的错误，只是前端路由导致的正常404
        log.debug("静态资源未找到（前端路由404）: {}", e.getResourcePath());
        // 返回 null 避免触发前端错误提示
        // 如果需要返回正常的404响应，可以用 Result.error(404, "页面不存在")
        return null;
    }

    /**
     * 兜底处理：捕获所有未被上面方法匹配的异常
     *
     * 触发场景：
     *   - NullPointerException（空指针）
     *   - NumberFormatException（数字格式错误）
     *   - 数据库操作异常
     *   - 任何其他未预期的异常
     *
     * 处理策略：
     *   - 用 log.error 记录完整错误栈（生产环境排查问题的关键）
     *   - 向前端返回模糊的通用错误信息（不暴露内部细节，防止安全泄露）
     *
     * 注意：Exception.class 是所有异常的父类，因此这里会捕获所有其他未处理的异常。
     * 但优先级最低，只有上面的方法都不匹配时才会用这个。
     *
     * 示例响应：
     *   { "code": 500, "message": "服务器内部错误，请稍后重试", "data": null }
     *
     * @param e 任意未被其他 @ExceptionHandler 处理的异常
     * @return 通用的服务器错误响应
     */
    @ExceptionHandler({DataAccessException.class, UnexpectedRollbackException.class})
    public Result<String> handleDatabaseFailure(Exception e) {
        if (DatabaseAccessExceptions.isTimeoutOrClosed(e)) {
            log.warn("数据库超时或连接已关闭: {}", rootMessage(e));
            return Result.error(503, "数据查询超时，请稍后重试");
        }
        log.error("系统异常: ", e);
        return Result.error("服务器内部错误，请稍后重试");
    }

    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e) {
        if (ClientAbortExceptions.isClientAbort(e)) {
            log.debug("客户端中断请求: {}", e.getMessage());
            return null;
        }
        if (DatabaseAccessExceptions.isTimeoutOrClosed(e)) {
            log.warn("数据库超时或连接已关闭: {}", rootMessage(e));
            return Result.error(503, "数据查询超时，请稍后重试");
        }
        log.error("系统异常: ", e);
        return Result.error("服务器内部错误，请稍后重试");
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        String message = current.getMessage();
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                message = current.getMessage();
            }
        }
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }
}
