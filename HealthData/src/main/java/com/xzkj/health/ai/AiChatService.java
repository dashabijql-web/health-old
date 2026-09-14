package com.xzkj.health.ai;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.xzkj.health.common.exception.BusinessException;
import com.xzkj.health.observability.HealthMetricsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 对话核心服务 — Text2SQL RAG 实现
 *
 * 【RAG 的本质】
 *
 * RAG = Retrieval-Augmented Generation（检索增强生成）
 *
 * 标准 RAG 流程：
 *   用户问题 → 检索相关文档 → 把文档塞进 Prompt → LLM 生成回答
 *
 * Text2SQL RAG 的特殊之处：
 *   "检索" 不是向量相似搜索，而是 SQL 数据库查询
 *   "文档" 不是文本片段，而是结构化的数据库查询结果
 *
 * 【两次调用 LLM 的设计】
 *
 * 第一次调用：问题 → SQL（让 LLM 扮演 SQL 专家）
 * 第二次调用：SQL结果 → 自然语言（让 LLM 扮演健康顾问）
 *
 * 为什么不一次搞定？因为两个任务的特点不同：
 *   - 生成 SQL：需要低温度（temperature=0.1），精确，不要发挥
 *   - 解释结果：需要中等温度，自然，有一定表达灵活性
 *
 * 未来可以优化为一次调用（让 LLM 同时返回 SQL + 解释），但两次更清晰易调试。
 */
@Slf4j
@Service
public class AiChatService {

    // SQL 提取正则：匹配 ```sql ... ``` 代码块，忽略大小写，跨行匹配
    private static final Pattern SQL_PATTERN = Pattern.compile(
        "```sql\\s*([\\s\\S]+?)```", Pattern.CASE_INSENSITIVE
    );

    // 解释查询结果的系统 Prompt（P0：要求 Markdown 格式输出）
    private static final String INTERPRETER_SYSTEM_PROMPT = """
            你是一个煤矿工人健康管理系统的智能助手，拥有完整的对话记忆。
            用户提问后，系统已经查询了数据库，请根据查询结果用自然、友好的中文回答用户问题。
            如果用户引用了之前的问题（如"他"、"这个部门"、"刚才说的"），请结合对话历史理解。

            【Markdown 格式要求 - 必须严格遵守，禁止把列表挤成一段话】
            - 排名/列表：每条占一行，使用有序列表格式，例如：
              1. **机电队**：80.39 次/分
              2. **地测科**：80.32 次/分
            - 多列数据对比（≥3列）：使用 Markdown 表格 | 列名 | 列名 |
            - 关键数值加粗：**96.77%**、**50人**
            - 健康建议放最后，用 > 引用块

            【内容要求】
            - 直接回答，不要说"根据查询结果"等废话
            - 英文字段名翻译成中文（heart_rate→心率，blood_oxygen→血氧，dept_name→部门）
            - 结果为空时告知用户
            - 回答不超过400字
            """;

    // 每个 session 最多保留最近 N 轮对话，防止 token 超限
    private static final int MAX_HISTORY_TURNS = 5;
    private static final int MAX_SQL_RETRY = 2;
    // Redis key 前缀，TTL 24小时
    private static final String SESSION_KEY_PREFIX = "ai:session:";
    private static final long SESSION_TTL_HOURS = 24;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private SchemaProvider schemaProvider;

    @Autowired
    private DeepSeekClient deepSeekClient;

    @Autowired
    private SqlExecutorMapper sqlExecutorMapper;

    @Autowired
    private HealthMetricsService healthMetricsService;

    /**
     * 处理用户问题（多轮对话版）
     *
     * 完整流程：
     *   Step 1: 取出该 session 的历史对话
     *   Step 2: 调用 DeepSeek 生成 SQL（带历史上下文）
     *   Step 3: 提取 SQL → 安全校验 → 执行
     *   Step 4: 调用 DeepSeek 解释结果（带历史上下文）
     *   Step 5: 把本轮 user/assistant 追加到 session 历史
     *
     * @param userQuestion 用户的自然语言问题
     * @param sessionId    会话ID，用于维护多轮上下文
     * @return AI 的中文回答
     */
    public String chat(String userQuestion, String sessionId) {
        log.info("收到用户问题: {} | session: {}", userQuestion, sessionId);

        // ─── Step 1: 从 Redis 加载 session 历史 ──────────────────────────
        List<Map<String, Object>> history = loadHistory(sessionId);

        // ─── Step 2: 第一次调用 DeepSeek → 生成 SQL ──────────────────────
        // SQL 生成带历史上下文，让 LLM 理解"那个部门"/"他"等指代
        String schema = schemaProvider.getSchema();
        log.info("Step 2: 调用 DeepSeek 生成 SQL（history={}轮）...", history.size() / 2);
        String llmResponse = deepSeekClient.chatWithHistory(schema, history, userQuestion);
        log.info("DeepSeek 返回: {}", llmResponse);

        // ─── Step 3: 提取 SQL → 安全校验 → 执行（含自动修复重试）──────────
        String sql = extractSql(llmResponse);

        String finalAnswer;

        if (sql == null) {
            log.info("LLM 未生成 SQL，直接返回解释");
            finalAnswer = llmResponse;
        } else {
            sql = validateSql(sql);
            log.info("提取到 SQL: {}", sql);

            AiSqlResultSet queryResult = executeWithRetry(sql, schema, userQuestion);

            // ─── Step 4: 第二次调用 DeepSeek → 解释查询结果（带历史）──────
            log.info("Step 4: 调用 DeepSeek 解释查询结果（history={} 轮）...", history.size() / 2);

            String interpreterUserMessage = String.format(
                "用户问题：%s\n\n数据库查询结果（JSON格式）：\n%s",
                userQuestion,
                JSON.toJSONString(queryResult)
            );

            finalAnswer = deepSeekClient.chatWithHistory(INTERPRETER_SYSTEM_PROMPT, history, interpreterUserMessage);
        }

        // ─── Step 5: 追加本轮对话并持久化到 Redis ──────────────────────────
        appendToHistory(history, userQuestion, finalAnswer, sessionId);

        log.info("最终回答生成完毕 | session={} history={}轮", sessionId, history.size() / 2);
        return finalAnswer;
    }

    /** 兼容旧的无 session 调用（生成随机 sessionId） */
    public String chat(String userQuestion) {
        return chat(userQuestion, "anonymous-" + System.currentTimeMillis());
    }

    /**
     * 流式对话：先同步执行 SQL 查询，再流式调用 DeepSeek 解释结果
     *
     * 流程：
     *   1. SQL 生成 + 执行（同步，不能流式）
     *   2. 解释结果时流式调用 DeepSeek，每收到 token 发送 SSE 事件
     *   3. 完成后发 [DONE] 事件，追加历史
     *
     * SSE 事件格式（前端按 EventSource 协议解析）：
     *   data: token文本\n\n
     *   data: [DONE]\n\n
     *   data: [SESSION]:sessionId\n\n
     */
    @Async("aiChatTaskExecutor")
    public void chatStreamAsync(String userQuestion, String sessionId, SseEmitter emitter) {
        try {
            chatStream(userQuestion, sessionId, emitter);
        } catch (Exception e) {
            log.error("流式对话失败", e);
            sendStreamFailure(emitter, e);
        }
    }

    public void chatStream(String userQuestion, String sessionId, SseEmitter emitter) throws IOException {
        log.info("流式对话开始: {} | session: {}", userQuestion, sessionId);

        List<Map<String, Object>> history = loadHistory(sessionId);

        // Step 1: 生成 SQL（同步调用，带历史上下文让 LLM 理解追问指代）
        String schema = schemaProvider.getSchema();
        String llmResponse = deepSeekClient.chatWithHistory(schema, history, userQuestion);
        String sql = extractSql(llmResponse);

        String interpreterUserMessage;

        if (sql == null) {
            // 无 SQL：直接流式输出 LLM 的原始回复（逐字发送）
            for (char c : llmResponse.toCharArray()) {
                emitter.send(SseEmitter.event().data(String.valueOf(c)));
            }
            appendToHistory(history, userQuestion, llmResponse, sessionId);
            emitter.send(SseEmitter.event().data("[SESSION]:" + sessionId));
            emitter.send(SseEmitter.event().data("[DONE]"));
            emitter.complete();
            return;
        }

        // Step 2: 安全校验 + 执行 SQL（含自动修复）
        // validateSql 在 try-catch 内，确保校验失败时也能发 [DONE] 关闭前端 loading
        AiSqlResultSet queryResult;
        try {
            sql = validateSql(sql);
            queryResult = executeWithRetry(sql, schema, userQuestion);
        } catch (Exception e) {
            sendStreamFailure(emitter, e);
            return;
        }

        // Step 3: 流式调用 DeepSeek 解释结果
        interpreterUserMessage = String.format(
            "用户问题：%s\n\n数据库查询结果（JSON格式）：\n%s",
            userQuestion, JSON.toJSONString(queryResult)
        );

        final String finalSessionId = sessionId;
        final String finalQuestion = userQuestion;
        final String finalSql = sql;
        final AiSqlResultSet finalQueryResult = queryResult;

        deepSeekClient.chatStream(
            INTERPRETER_SYSTEM_PROMPT, history, interpreterUserMessage,
            // onToken：每收到一个 token，立刻发给前端
            token -> {
                try {
                    emitter.send(SseEmitter.event().data(token));
                } catch (IOException e) {
                    throw new RuntimeException("SSE 发送失败", e);
                }
            },
            // onDone：流结束，发数据/SQL/SESSION/DONE，追加历史
            fullContent -> {
                appendToHistory(history, finalQuestion, fullContent, finalSessionId);
                try {
                    // 发送原始查询结果（P1：供前端渲染表格/图表）
                    String dataB64 = java.util.Base64.getEncoder()
                        .encodeToString(JSON.toJSONString(finalQueryResult)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    emitter.send(SseEmitter.event().data("[DATA]:" + dataB64));
                    // 发送 SQL 调试信息
                    String sqlB64 = java.util.Base64.getEncoder()
                        .encodeToString(finalSql.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    emitter.send(SseEmitter.event().data("[SQL]:" + sqlB64));
                    emitter.send(SseEmitter.event().data("[SESSION]:" + finalSessionId));
                    emitter.send(SseEmitter.event().data("[DONE]"));
                    emitter.complete();
                    log.info("流式对话完成: session={}", finalSessionId);
                } catch (IOException e) {
                    emitter.completeWithError(e);
                }
            }
        );
    }

    /**
     * 清除指定 session 的历史（用于"开始新对话"按钮）
     */
    public void clearSession(String sessionId) {
        redisTemplate.delete(SESSION_KEY_PREFIX + sessionId);
        log.info("清除 session 历史: {}", sessionId);
    }

    /**
     * 追加一轮对话到历史，并持久化到 Redis
     */
    private void appendToHistory(List<Map<String, Object>> history, String question, String answer,
                                 String sessionId) {
        Map<String, Object> userMsg = new LinkedHashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", question);

        Map<String, Object> assistantMsg = new LinkedHashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", answer);

        history.add(userMsg);
        history.add(assistantMsg);

        // 超过最大轮数时，删除最早的一轮（2条消息）
        while (history.size() > MAX_HISTORY_TURNS * 2) {
            history.remove(0);
            history.remove(0);
        }

        // 持久化到 Redis，刷新 TTL
        saveHistory(sessionId, history);
    }

    /** 从 Redis 加载 session 历史，不存在则返回空列表 */
    private List<Map<String, Object>> loadHistory(String sessionId) {
        String json = redisTemplate.opsForValue().get(SESSION_KEY_PREFIX + sessionId);
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return JSON.parseObject(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("解析 session 历史失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /** 保存 session 历史到 Redis，重置 TTL */
    private void saveHistory(String sessionId, List<Map<String, Object>> history) {
        redisTemplate.opsForValue().set(
            SESSION_KEY_PREFIX + sessionId,
            JSON.toJSONString(history),
            SESSION_TTL_HOURS, TimeUnit.HOURS
        );
    }

    /**
     * 从 LLM 回复中提取 SQL 代码块
     *
     * LLM 通常会把 SQL 包裹在 ```sql ... ``` 里，
     * 我们用正则把它提取出来。
     *
     * 如果没有代码块，说明 LLM 判断无法用 SQL 回答（如问"你好"），
     * 直接返回 null，调用方会把 LLM 的原始回复返回给用户。
     *
     * @return SQL 字符串，或 null（如果 LLM 没有生成 SQL）
     */
    private String extractSql(String llmResponse) {
        Matcher matcher = SQL_PATTERN.matcher(llmResponse);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private AiSqlResultSet executeWithRetry(String sql, String schema, String userQuestion) {
        long startedAt = System.nanoTime();
        String currentSql = sql;
        Exception lastError = null;
        boolean autoRepaired = false;

        for (int attempt = 1; attempt <= MAX_SQL_RETRY; attempt++) {
            try {
                List<Map<String, Object>> rows = sqlExecutorMapper.executeQuery(currentSql);
                AiSqlResultSet resultSet = AiSqlResultSet.fromRows(rows);
                logAiQueryAudit(userQuestion, currentSql, resultSet.rowCount(), elapsedMs(startedAt), autoRepaired, null);
                return resultSet;
            } catch (Exception e) {
                lastError = e;
                log.warn("AI SQL 执行失败: attempt={}, sql={}, error={}", attempt, currentSql, e.getMessage());

                if (attempt < MAX_SQL_RETRY) {
                    String fixPrompt = String.format(
                        "以下 SQL 执行时出错，请修正后重新生成一条正确的 SQL。\n\n" +
                        "原始问题：%s\n\n" +
                        "错误的 SQL：\n```sql\n%s\n```\n\n" +
                        "错误信息：%s\n\n" +
                        "请只返回修正后的 SQL，用 ```sql ... ``` 包裹。",
                        userQuestion, currentSql, e.getMessage()
                    );
                    try {
                        String fixResponse = deepSeekClient.chat(schema, fixPrompt);
                        String fixedSql = extractSql(fixResponse);
                        if (fixedSql != null) {
                            currentSql = validateSql(fixedSql);
                            autoRepaired = true;
                            healthMetricsService.recordAiAutoRepair("success");
                            log.info("LLM 修正后的 SQL: {}", currentSql);
                        } else {
                            healthMetricsService.recordAiAutoRepair("empty");
                            break;
                        }
                    } catch (BusinessException fixEx) {
                        healthMetricsService.recordAiAutoRepair("rejected");
                        log.warn("SQL 自修复失败: {}", fixEx.getMessage());
                        break;
                    } catch (Exception fixEx) {
                        healthMetricsService.recordAiAutoRepair("failure");
                        log.warn("SQL 自修复失败: {}", fixEx.getMessage());
                        break;
                    }
                }
            }
        }
        logAiQueryAudit(
                userQuestion,
                currentSql,
                -1,
                elapsedMs(startedAt),
                autoRepaired,
                lastError == null ? "unknown" : lastError.getClass().getSimpleName()
        );
        throw new BusinessException(503, "AI查询执行失败，请缩小时间范围或稍后重试");
    }

    private String validateSql(String sql) {
        try {
            return AiSqlGuard.sanitizeAndValidate(sql);
        } catch (IllegalArgumentException e) {
            healthMetricsService.recordAiReject("sql-guard", "unsafe-sql");
            throw new BusinessException(400, "AI查询超出安全边界，请补充时间范围或筛选条件后重试");
        }
    }

    private void logAiQueryAudit(String question, String sql, int rowCount, long elapsedMs,
                                 boolean autoRepaired, String failureReason) {
        if (failureReason == null) {
            log.info("AI SQL 审计: question={}, sql={}, rows={}, elapsedMs={}, autoRepaired={}",
                    question, sql, rowCount, elapsedMs, autoRepaired);
            return;
        }
        log.warn("AI SQL 审计失败: question={}, sql={}, elapsedMs={}, autoRepaired={}, reason={}",
                question, sql, elapsedMs, autoRepaired, failureReason);
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private void sendStreamFailure(SseEmitter emitter, Throwable throwable) {
        String safeMessage = resolveClientMessage(throwable);
        try {
            if (!safeMessage.toUpperCase(Locale.ROOT).startsWith("[ERROR]")) {
                safeMessage = "[ERROR]" + safeMessage;
            }
            emitter.send(SseEmitter.event().data(safeMessage));
            emitter.send(SseEmitter.event().data("[DONE]"));
            emitter.complete();
        } catch (IOException ioException) {
            emitter.completeWithError(ioException);
        }
    }

    private String resolveClientMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof BusinessException businessException) {
                return businessException.getMessage();
            }
            if (current instanceof TimeoutException) {
                return "AI服务响应超时，请稍后重试";
            }
            current = current.getCause();
        }
        return "AI服务暂时不可用，请稍后重试";
    }
}
