package com.xzkj.health.ai;

import com.xzkj.health.common.exception.BusinessException;
import com.xzkj.health.observability.HealthMetricsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiChatServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SchemaProvider schemaProvider;

    @Mock
    private DeepSeekClient deepSeekClient;

    @Mock
    private SqlExecutorMapper sqlExecutorMapper;

    @Mock
    private HealthMetricsService healthMetricsService;

    @InjectMocks
    private AiChatService aiChatService;

    @Test
    void chatRecordsSqlGuardRejectWhenGeneratedSqlIsUnsafe() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("ai:session:s-1")).thenReturn(null);
        when(schemaProvider.getSchema()).thenReturn("schema");
        when(deepSeekClient.chatWithHistory(eq("schema"), any(), eq("给我全部健康记录")))
                .thenReturn("```sql\nSELECT e.emp_name, h.heart_rate FROM v_health_record h JOIN employee e ON h.user_code = e.emp_code\n```");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> aiChatService.chat("给我全部健康记录", "s-1"));

        assertEquals(400, exception.getCode());
        verify(healthMetricsService).recordAiReject("sql-guard", "unsafe-sql");
        verify(sqlExecutorMapper, never()).executeQuery(anyString());
    }

    @Test
    void chatRecordsAutoRepairWhenRetryFixesSql() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("ai:session:s-2")).thenReturn(null);
        when(schemaProvider.getSchema()).thenReturn("schema");
        when(deepSeekClient.chatWithHistory(eq("schema"), any(), eq("查张三最新心率")))
                .thenReturn("```sql\nSELECT TOP 1 h.heart_rate, h.record_time FROM v_health_record h JOIN employee e ON h.user_code = e.emp_code WHERE e.emp_name = '张三' ORDER BY h.record_time DESC\n```");
        when(deepSeekClient.chatWithHistory(contains("你是一个煤矿工人健康管理系统的智能助手"), any(), contains("数据库查询结果")))
                .thenReturn("这是修正后的解释");
        when(sqlExecutorMapper.executeQuery(contains("ORDER BY h.record_time DESC")))
                .thenThrow(new RuntimeException("bad sql"))
                .thenReturn(List.of(Map.of("heart_rate", 88, "record_time", "2026-05-07 10:00:00")));
        when(deepSeekClient.chat(eq("schema"), contains("错误的 SQL")))
                .thenReturn("```sql\nSELECT TOP 1 h.heart_rate, h.record_time FROM v_health_record h JOIN employee e ON h.user_code = e.emp_code WHERE e.emp_name = '张三' ORDER BY h.record_time DESC\n```");

        String answer = aiChatService.chat("查张三最新心率", "s-2");

        assertEquals("这是修正后的解释", answer);
        verify(deepSeekClient).chatWithHistory(
                contains("你是一个煤矿工人健康管理系统的智能助手"),
                any(),
                contains("\"rowCount\":1")
        );
        verify(healthMetricsService).recordAiAutoRepair("success");
        verify(valueOperations).set(eq("ai:session:s-2"), anyString(), eq(24L), eq(TimeUnit.HOURS));
    }
}
