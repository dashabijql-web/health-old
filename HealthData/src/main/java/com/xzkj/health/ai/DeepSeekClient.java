package com.xzkj.health.ai;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.xzkj.health.common.exception.BusinessException;
import com.xzkj.health.observability.HealthMetricsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * DeepSeek API 客户端
 *
 * 【职责】
 * 封装对 DeepSeek HTTP API 的调用细节，对外只暴露一个简洁的 chat() 方法。
 *
 * 【为什么单独抽成一个类？】
 * 如果以后要换成 Claude 或 GPT，只需改这一个文件，Service 层不用动。
 * 这是"依赖倒置原则"的实践：业务逻辑不依赖具体的 AI 厂商。
 *
 * 【System Prompt vs User Message 区别】
 *
 * DeepSeek（以及所有 OpenAI 兼容 API）支持两种消息角色：
 *
 *   system  → 给 AI 的"幕后指令"，定义它的身份、规则、背景知识
 *             类比：给新员工的岗位说明书
 *
 *   user    → 用户实际发送的消息
 *             类比：用户的具体问题
 *
 * 在 RAG 中：
 *   system = Schema描述 + 规则  （告诉AI你的数据库结构）
 *   user   = 用户的自然语言问题
 */
@Slf4j
@Component
public class DeepSeekClient {

    @Value("${deepseek.api-key}")
    private String apiKey;

    @Value("${deepseek.api-key-file:}")
    private String apiKeyFile;

    @Value("${deepseek.api-url}")
    private String apiUrl;

    @Value("${deepseek.model}")
    private String model;

    @Value("${deepseek.timeout-seconds:60}")
    private int timeoutSeconds;

    @Autowired
    private HealthMetricsService healthMetricsService;

    private volatile String resolvedApiKey;
    private volatile HttpClient httpClient;

    /**
     * 发送单轮对话请求
     *
     * @param systemPrompt 系统提示词（定义 AI 角色和背景知识）
     * @param userMessage  用户消息
     * @return AI 的回复文本
     * @throws RuntimeException 调用失败时抛出
     */
    public String chat(String systemPrompt, String userMessage) {
        return chatWithHistory(systemPrompt, List.of(), userMessage);
    }

    /**
     * 发送多轮对话请求（带历史记录）
     *
     * messages 结构：
     *   system prompt → history[0](user) → history[1](assistant) → ... → 当前 userMessage
     *
     * @param systemPrompt 系统提示词
     * @param history      历史对话，每条 Map 包含 role("user"/"assistant") 和 content
     * @param userMessage  当前用户消息
     */
    public String chatWithHistory(String systemPrompt, List<Map<String, Object>> history, String userMessage) {
        log.debug("调用 DeepSeek | history={} 轮 | userMessage: {}", history.size() / 2, userMessage);
        long startedAt = System.nanoTime();
        String metricResult = "success";

        // 构建完整 messages 列表：system + history + 当前 user
        List<Map<String, Object>> messages = new java.util.ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.addAll(history);
        messages.add(Map.of("role", "user", "content", userMessage));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("max_tokens", 1000);
        body.put("temperature", 0.1);  // 低温度：SQL生成要准确，不要创意
        body.put("stream", false);

        try {
            String apiToken = resolveApiKey();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiToken)
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(body)))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .build();

            HttpResponse<String> response = getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                metricResult = "http-" + response.statusCode();
                healthMetricsService.recordAiReject("provider", "http-" + response.statusCode());
                log.error("DeepSeek API 返回错误: status={}, body={}", response.statusCode(), response.body());
                throw new BusinessException(toUserCode(response.statusCode()), toUserMessage(response.statusCode()));
            }

            // 解析响应：choices[0].message.content
            JSONObject json = JSON.parseObject(response.body());
            String content = json.getJSONArray("choices")
                                 .getJSONObject(0)
                                 .getJSONObject("message")
                                 .getString("content");

            log.debug("DeepSeek 回复: {}", content);
            return content;

        } catch (BusinessException e) {
            if ("success".equals(metricResult)) {
                metricResult = "business-error";
            }
            throw e;
        } catch (HttpTimeoutException e) {
            metricResult = "timeout";
            log.warn("DeepSeek API 响应超时");
            throw new BusinessException(504, "AI服务响应超时，请稍后重试");
        } catch (InterruptedException e) {
            metricResult = "interrupted";
            Thread.currentThread().interrupt();
            log.warn("DeepSeek API 调用被中断");
            throw new BusinessException(503, "AI服务调用被中断，请稍后重试");
        } catch (IOException e) {
            metricResult = "io-error";
            log.warn("调用 DeepSeek API 网络失败: {}", e.getMessage());
            throw new BusinessException(503, "AI服务网络异常，请稍后重试");
        } catch (Exception e) {
            metricResult = "error";
            log.error("调用 DeepSeek API 失败", e);
            throw new BusinessException(503, "AI服务暂时不可用，请稍后重试");
        } finally {
            healthMetricsService.recordAiCall("sync", metricResult, elapsedMs(startedAt));
        }
    }

    /**
     * 流式对话：DeepSeek 每生成一个 token 就回调 onToken，完成后回调 onDone
     *
     * DeepSeek 流式响应格式（SSE）：
     *   data: {"choices":[{"delta":{"content":"你好"}}]}
     *   data: {"choices":[{"delta":{"content":"！"}}]}
     *   data: [DONE]
     *
     * 我们逐行解析，每行提取 delta.content，通过 onToken 回调传给调用方。
     *
     * @param systemPrompt 系统提示词
     * @param history      历史对话
     * @param userMessage  当前用户消息
     * @param onToken      每收到一个 token 时的回调（接收 token 字符串）
     * @param onDone       流结束时的回调（接收完整的回复文本）
     */
    public void chatStream(String systemPrompt, List<Map<String, Object>> history,
                           String userMessage, Consumer<String> onToken, Consumer<String> onDone) {
        log.debug("流式调用 DeepSeek | history={}轮 | user: {}", history.size() / 2, userMessage);
        long startedAt = System.nanoTime();
        String metricResult = "success";

        List<Map<String, Object>> messages = new java.util.ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.addAll(history);
        messages.add(Map.of("role", "user", "content", userMessage));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("max_tokens", 1000);
        body.put("temperature", 0.1);
        body.put("stream", true);  // ← 关键：开启流式模式

        try {
            String apiToken = resolveApiKey();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiToken)
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(body)))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .build();

            // 用流式 BodyHandler 接收响应，不等全部完成
            HttpResponse<java.io.InputStream> response = getHttpClient().send(
                request, HttpResponse.BodyHandlers.ofInputStream()
            );

            if (response.statusCode() != 200) {
                metricResult = "http-" + response.statusCode();
                healthMetricsService.recordAiReject("provider", "http-" + response.statusCode());
                log.error("DeepSeek API 流式返回错误: status={}", response.statusCode());
                throw new BusinessException(toUserCode(response.statusCode()), toUserMessage(response.statusCode()));
            }

            StringBuilder fullContent = new StringBuilder();

            // 逐行读取 SSE 流
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) continue;           // 空行跳过
                    if (!line.startsWith("data: ")) continue; // 只处理 data: 行

                    String data = line.substring(6).trim(); // 去掉 "data: " 前缀
                    if ("[DONE]".equals(data)) break;       // 流结束标志

                    try {
                        JSONObject json = JSON.parseObject(data);
                        String token = json.getJSONArray("choices")
                                          .getJSONObject(0)
                                          .getJSONObject("delta")
                                          .getString("content");
                        if (token != null && !token.isEmpty()) {
                            fullContent.append(token);
                            onToken.accept(token);          // 回调：把 token 发给前端
                        }
                    } catch (Exception e) {
                        // 某些行可能不是 JSON（如注释），忽略
                        log.debug("跳过非JSON行: {}", data);
                    }
                }
            }

            onDone.accept(fullContent.toString()); // 流结束：传入完整内容

        } catch (BusinessException e) {
            if ("success".equals(metricResult)) {
                metricResult = "business-error";
            }
            throw e;
        } catch (HttpTimeoutException e) {
            metricResult = "timeout";
            log.warn("流式调用 DeepSeek 超时");
            throw new BusinessException(504, "AI服务响应超时，请稍后重试");
        } catch (InterruptedException e) {
            metricResult = "interrupted";
            Thread.currentThread().interrupt();
            log.warn("流式调用 DeepSeek 被中断");
            throw new BusinessException(503, "AI服务调用被中断，请稍后重试");
        } catch (IOException e) {
            metricResult = "io-error";
            log.warn("流式调用 DeepSeek 网络失败: {}", e.getMessage());
            throw new BusinessException(503, "AI服务网络异常，请稍后重试");
        } catch (Exception e) {
            metricResult = "error";
            log.error("流式调用 DeepSeek 失败", e);
            throw new BusinessException(503, "AI服务暂时不可用，请稍后重试");
        } finally {
            healthMetricsService.recordAiCall("stream", metricResult, elapsedMs(startedAt));
        }
    }

    private String resolveApiKey() {
        String envKey = trimToNull(apiKey);
        if (envKey != null) return envKey;

        String cached = trimToNull(resolvedApiKey);
        if (cached != null) return cached;

        for (Path candidate : candidateApiKeyFiles()) {
            try {
                if (!Files.isRegularFile(candidate)) {
                    continue;
                }
                String fileKey = trimToNull(Files.readString(candidate));
                if (fileKey != null) {
                    resolvedApiKey = fileKey;
                    log.info("从 API Key 文件加载 DeepSeek 配置: {}", candidate);
                    return fileKey;
                }
            } catch (Exception e) {
                log.warn("读取 DeepSeek API Key 文件失败: path={}, error={}", candidate, e.getMessage());
            }
        }

        throw new BusinessException(503, "AI服务未完成配置，请联系管理员");
    }

    private List<Path> candidateApiKeyFiles() {
        LinkedHashSet<Path> candidates = new LinkedHashSet<>();

        String configured = trimToNull(apiKeyFile);
        if (configured != null) {
            candidates.add(Path.of(configured));
        }

        String userHome = trimToNull(System.getProperty("user.home"));
        if (userHome != null) {
            candidates.add(Path.of(userHome, "Desktop", "deepseek-key.txt"));
            candidates.add(Path.of(userHome, "Desktop", "deepseek_key.txt"));
        }

        String[] userNames = {
                trimToNull(System.getProperty("user.name")),
                trimToNull(System.getenv("USER")),
                trimToNull(System.getenv("USERNAME"))
        };
        for (String userName : userNames) {
            if (userName != null) {
                candidates.add(Path.of("/mnt/c/Users", userName, "Desktop", "deepseek-key.txt"));
                candidates.add(Path.of("/mnt/c/Users", userName, "Desktop", "deepseek_key.txt"));
            }
        }

        return new ArrayList<>(candidates);
    }

    private HttpClient getHttpClient() {
        HttpClient client = httpClient;
        if (client != null) {
            return client;
        }
        synchronized (this) {
            if (httpClient == null) {
                httpClient = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(Math.max(5, Math.min(timeoutSeconds, 15))))
                        .build();
            }
            return httpClient;
        }
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    private static String toUserMessage(int statusCode) {
        if (statusCode == 401 || statusCode == 403) {
            return "AI服务认证失败，请联系管理员检查配置";
        }
        if (statusCode == 429) {
            return "AI服务调用过于频繁或额度不足，请稍后重试";
        }
        return "AI服务暂时不可用，请稍后重试";
    }

    private static int toUserCode(int statusCode) {
        if (statusCode == 429) {
            return 429;
        }
        return 503;
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
