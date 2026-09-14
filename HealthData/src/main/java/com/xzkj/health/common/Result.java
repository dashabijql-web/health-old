package com.xzkj.health.common;

import lombok.Data;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║              统一 API 响应包装类（新手必读）                        ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * 【为什么需要这个类？】
 *
 * 前端调用后端 API 时，需要一种统一的"信封"格式，以便判断请求是否成功。
 * 如果每个接口返回格式不同（有的返回字符串，有的返回对象，有的返回 null），
 * 前端就很难统一处理。
 *
 * 解决方案：所有接口都返回 Result 对象，格式固定：
 * {
 *   "code": 200,           // 状态码：200=成功，500=失败，400=参数错误，401=未登录
 *   "message": "操作成功", // 提示信息
 *   "data": { ... }        // 实际数据（可以是任何类型）
 * }
 *
 * 前端只需统一判断 code === 200 即可。
 *
 * 【泛型 <T> 是什么？】
 *
 * Result<T> 中的 T 是类型参数（泛型），表示 data 字段可以是任意类型：
 *   - Result<String>          → data 是字符串
 *   - Result<SysUser>         → data 是用户对象
 *   - Result<List<SysUser>>   → data 是用户列表
 *   - Result<Map<String,Object>> → data 是键值对
 *
 * 这样一个类就能适配所有场景，不需要为每种数据写不同的响应类。
 *
 * 【静态工厂方法模式】
 *
 * 注意这个类的方法都是 static 的，调用方式是 Result.ok(...) / Result.error(...)
 * 而不是 new Result().setCode(200)。
 * 这种写法叫"静态工厂方法"，优点是代码更简洁、语义更清晰：
 *
 *   return Result.ok("获取成功", userList);   // 成功
 *   return Result.error("用户不存在");        // 失败
 *
 * 【@Data 注解说明】
 *
 * @Data 是 Lombok 提供的注解，会在编译时自动生成：
 *   - 所有字段的 getter 方法（getCode, getMessage, getData）
 *   - 所有字段的 setter 方法（setCode, setMessage, setData）
 *   - toString() 方法
 *   - equals() 和 hashCode() 方法
 * 省去了大量手写样板代码。
 */
@Data
public class Result<T> {

    /**
     * 业务状态码（不是 HTTP 状态码，是业务自定义的）
     *
     * 约定：
     *   200 → 操作成功
     *   400 → 客户端参数错误（如缺少必填字段、格式不对）
     *   401 → 未登录或 Token 已过期
     *   403 → 已登录但没有权限
     *   500 → 服务器内部错误（未预期的异常）
     */
    private int code;

    /**
     * 提示信息，展示给用户或供前端判断
     * 例如："操作成功"、"用户不存在"、"密码错误" 等
     */
    private String message;

    /**
     * 实际业务数据
     * 成功时携带数据，失败时通常为 null
     * 类型由泛型 T 决定，可以是任何对象
     */
    private T data;

    // ────────────────────────────────────────────────────────
    //  成功响应的静态工厂方法
    // ────────────────────────────────────────────────────────

    /**
     * 成功响应（带自定义消息和数据）
     *
     * 使用场景示例：
     *   return Result.ok("查询成功", userList);
     *   return Result.ok("创建成功", newUser);
     *
     * @param message 提示消息
     * @param data    实际数据
     * @param <T>     数据类型（Java 编译器会自动推断）
     * @return 封装好的 Result 对象
     */
    public static <T> Result<T> ok(String message, T data) {
        Result<T> r = new Result<>();
        r.code = 200;
        r.message = message;
        r.data = data;
        return r;
    }

    /**
     * 成功响应（使用默认消息"操作成功"，带数据）
     *
     * 使用场景示例：
     *   return Result.ok(userList);
     *
     * @param data 实际数据
     * @param <T>  数据类型
     * @return 封装好的 Result 对象
     */
    public static <T> Result<T> ok(T data) {
        return ok("操作成功", data);
    }

    /**
     * 成功响应（无数据，如删除/更新操作只需告知成功）
     *
     * 使用场景示例：
     *   return Result.ok();  // 例如：退出登录成功
     *
     * @param <T> 数据类型（此场景下 data 为 null）
     * @return 封装好的 Result 对象
     */
    public static <T> Result<T> ok() {
        return ok("操作成功", null);
    }

    // ────────────────────────────────────────────────────────
    //  失败响应的静态工厂方法
    // ────────────────────────────────────────────────────────

    /**
     * 失败响应（默认状态码 500，只带消息）
     *
     * 使用场景示例：
     *   return Result.error("用户名或密码错误");
     *
     * @param message 错误描述
     * @param <T>     数据类型（data 为 null）
     * @return 封装好的 Result 对象
     */
    public static <T> Result<T> error(String message) {
        Result<T> r = new Result<>();
        r.code = 500;
        r.message = message;
        return r;
    }

    /**
     * 失败响应（自定义状态码 + 消息）
     *
     * 使用场景示例：
     *   return Result.error(400, "缺少必填参数: username");
     *   return Result.error(401, "未登录或登录已过期");
     *
     * @param code    自定义状态码
     * @param message 错误描述
     * @param <T>     数据类型（data 为 null）
     * @return 封装好的 Result 对象
     */
    public static <T> Result<T> error(int code, String message) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        return r;
    }
}
