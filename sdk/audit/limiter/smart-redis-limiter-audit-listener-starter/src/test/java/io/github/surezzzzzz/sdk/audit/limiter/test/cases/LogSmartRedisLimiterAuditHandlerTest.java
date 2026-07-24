package io.github.surezzzzzz.sdk.audit.limiter.test.cases;

import io.github.surezzzzzz.sdk.audit.limiter.handler.impl.LogSmartRedisLimiterAuditHandler;
import io.github.surezzzzzz.sdk.audit.limiter.test.SmartRedisLimiterAuditListenerTestApplication;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.SmartRedisLimiterRecord;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 默认日志限流审计处理器测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SmartRedisLimiterAuditListenerTestApplication.class)
@ExtendWith(OutputCaptureExtension.class)
public class LogSmartRedisLimiterAuditHandlerTest {

    private static final String SENSITIVE_LIMIT_KEY = "sentinel-limit-key";
    private static final String SENSITIVE_ROUTE_KEY = "sentinel-route-key";
    private static final String SENSITIVE_CLIENT_ID = "sentinel-client-id";
    private static final String SENSITIVE_TRACE_ID = "sentinel-trace-id";
    private static final String SENSITIVE_REQUEST_URI = "/sentinel/request-uri";
    private static final String SENSITIVE_EXTRA = "sentinel-extra";

    @Autowired
    private LogSmartRedisLimiterAuditHandler handler;

    @Test
    public void testPassedLogNeverIncludesSensitiveRecordFields(CapturedOutput output) {
        handler.handle(sensitiveRecord(true));

        assertSafeLog(output, "[LimiterAudit:PASS]");
    }

    @Test
    public void testLimitedLogNeverIncludesSensitiveRecordFields(CapturedOutput output) {
        handler.handle(sensitiveRecord(false));

        assertSafeLog(output, "[LimiterAudit:LIMITED]");
    }

    private SmartRedisLimiterRecord sensitiveRecord(boolean passed) {
        return SmartRedisLimiterRecord.builder()
                .passed(passed)
                .source("INTERCEPTOR")
                .algorithm("fixed")
                .keyStrategy("path")
                .limitKey(SENSITIVE_LIMIT_KEY)
                .routeKey(SENSITIVE_ROUTE_KEY)
                .clientId(SENSITIVE_CLIENT_ID)
                .traceId(SENSITIVE_TRACE_ID)
                .requestUri(SENSITIVE_REQUEST_URI)
                .clientIp("198.51.100.10")
                .extra(Collections.singletonMap("sentinel", SENSITIVE_EXTRA))
                .datasourceKey("test-source")
                .redisMode(SmartRedisLimiterConstant.REDIS_MODE_STANDALONE)
                .routeRequired(true)
                .routeResolved(true)
                .matchedPathPattern("/safe/**")
                .limit(5L)
                .remaining(0L)
                .resetAt(1715635200L)
                .durationNanos(100L)
                .policySource(SmartRedisLimiterConstant.POLICY_SOURCE_LOCAL)
                .build();
    }

    private void assertSafeLog(CapturedOutput output, String expectedType) {
        String message = output.getOut();
        log.info("默认审计日志安全字段验证完成：type={}, length={}", expectedType, message.length());
        assertTrue(message.contains(expectedType), "默认日志必须输出预期审计类型");
        assertTrue(message.contains("matchedPathPattern=/safe/**"), "默认日志必须保留安全路径模式字段");
        assertFalse(message.contains(SENSITIVE_LIMIT_KEY), "默认日志不得包含限流 Key");
        assertFalse(message.contains(SENSITIVE_ROUTE_KEY), "默认日志不得包含路由 Key");
        assertFalse(message.contains(SENSITIVE_CLIENT_ID), "默认日志不得包含客户端标识");
        assertFalse(message.contains(SENSITIVE_TRACE_ID), "默认日志不得包含 TraceId");
        assertFalse(message.contains(SENSITIVE_REQUEST_URI), "默认日志不得包含原始 URI");
        assertFalse(message.contains(SENSITIVE_EXTRA), "默认日志不得包含扩展属性");
        assertFalse(message.contains("198.51.100.10"), "默认日志不得包含客户端 IP");
    }
}
