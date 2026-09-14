package com.xzkj.health.ai;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 动态 SQL 执行 Mapper
 *
 * 【为什么需要这个？】
 *
 * LLM 生成的 SQL 是运行时动态产生的字符串，不是编译时确定的。
 * MyBatis 的普通 @Select 注解要求 SQL 在代码里写死，无法执行动态 SQL。
 *
 * 解决方案：使用 MyBatis 的 <script> + ${sql} 参数直接传入 SQL 字符串。
 *
 * 【⚠️ 安全说明】
 *
 * ${sql} 是 MyBatis 的字符串替换（非参数化），存在 SQL 注入风险。
 * 我们在 AiChatService 中通过以下方式防护：
 *   1. 检查 SQL 只允许 SELECT 开头
 *   2. 禁止包含 INSERT/UPDATE/DELETE/DROP/EXEC 等关键字
 *   3. 限制查询结果行数（TOP 100）
 *
 * 这是 Text2SQL RAG 的常见做法，风险可控。
 */
@Mapper
public interface SqlExecutorMapper {

    /**
     * 执行动态 SELECT 查询
     *
     * 返回 List<Map<String, Object>>：
     *   - 每个 Map 代表一行数据
     *   - key = 列名（字符串），value = 列值（Object，可能是 String/Integer/Date 等）
     *
     * 例如查询结果：
     *   [
     *     {"emp_name": "张三", "heart_rate": 85, "record_time": "2026-04-07 08:30:00"},
     *     {"emp_name": "李四", "heart_rate": 92, "record_time": "2026-04-07 08:31:00"}
     *   ]
     *
     * @param sql 要执行的 SELECT 语句（由 LLM 生成，经过安全校验）
     */
    @Select("${sql}")
    @Options(timeout = 15)
    List<Map<String, Object>> executeQuery(String sql);
}
