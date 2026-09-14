package com.xzkj.health.common.exception;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║                    业务异常类（新手必读）                           ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * 【为什么要自定义异常？】
 *
 * Java 内置了很多异常（NullPointerException、IllegalArgumentException 等），
 * 但它们表达的是"代码编写错误"，不适合用来表达业务层面的错误，比如：
 *   - "用户不存在"
 *   - "账号已被禁用"
 *   - "库存不足，无法下单"
 *
 * 自定义 BusinessException 专门用于表达"业务规则被违反"的情况，
 * 这样全局异常处理器（GlobalExceptionHandler）就能识别它并返回合适的响应。
 *
 * 【为什么继承 RuntimeException（运行时异常）？】
 *
 * Java 异常分两类：
 *   1. Checked Exception（受检异常）：调用方必须用 try-catch 处理，如 IOException
 *   2. Unchecked Exception / RuntimeException（非受检异常）：可以不处理，如 NullPointerException
 *
 * 业务异常用 RuntimeException 的原因：
 *   - 业务异常一旦发生，通常无法"恢复"，只能返回错误给前端
 *   - 如果用 Checked Exception，每个 Service/Controller 都要写大量 try-catch 样板代码
 *   - 用 RuntimeException 后，异常会自动向上传播，由 GlobalExceptionHandler 统一捕获
 *
 * 【使用场景举例】
 *
 * 在 Service 层：
 *   if (user == null) {
 *       throw new BusinessException("用户不存在");  // 抛出，不需要 catch
 *   }
 *   if (user.getStatus() != 0) {
 *       throw new BusinessException(403, "账号已被禁用");  // 带状态码
 *   }
 *
 * GlobalExceptionHandler 会自动捕获并返回：
 *   { "code": 500, "message": "用户不存在", "data": null }
 *   { "code": 403, "message": "账号已被禁用", "data": null }
 *
 * 【与 RuntimeException.message 的关系】
 *
 * 调用 super(message) 将 message 存储在父类 RuntimeException 的 message 字段中，
 * 通过 e.getMessage() 即可取出。
 * 额外的 code 字段存储 HTTP 状态码，供 GlobalExceptionHandler 构建响应使用。
 */
public class BusinessException extends RuntimeException {

    /**
     * HTTP 业务状态码
     * 默认 500（服务器内部错误），可以指定 400（参数错误）、403（无权限）等
     * 这个 code 会被 GlobalExceptionHandler 放入 Result.code 字段返回给前端
     */
    private final int code;

    /**
     * 构造函数（默认状态码 500）
     * 适用于：一般业务规则违反，如"用户不存在"、"密码错误"
     *
     * 示例：
     *   throw new BusinessException("用户名或密码错误");
     *
     * @param message 错误描述，会返回给前端显示
     */
    public BusinessException(String message) {
        super(message);       // 调用 RuntimeException(String message) 构造函数
        this.code = 500;      // 默认状态码
    }

    /**
     * 构造函数（自定义状态码）
     * 适用于：需要特定状态码的业务场景，如 403 权限不足
     *
     * 示例：
     *   throw new BusinessException(400, "参数格式不正确");
     *   throw new BusinessException(403, "只有管理员可以操作");
     *
     * @param code    业务状态码（如 400、403、500）
     * @param message 错误描述
     */
    public BusinessException(int code, String message) {
        super(message);       // 调用父类构造，存储 message
        this.code = code;     // 存储自定义状态码
    }

    /**
     * 获取业务状态码
     * 由 GlobalExceptionHandler 调用，用于构建 Result.error(code, message)
     *
     * @return 状态码
     */
    public int getCode() {
        return code;
    }
}
