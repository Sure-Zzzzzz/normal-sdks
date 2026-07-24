package io.github.surezzzzzz.sdk.audit.limiter.test.cases;

import io.github.surezzzzzz.sdk.audit.limiter.support.SmartRedisLimiterAuditRecordHelper;
import io.github.surezzzzzz.sdk.limiter.redis.smart.audit.SmartRedisLimiterTraceIdProvider;
import io.github.surezzzzzz.sdk.limiter.redis.smart.audit.SmartRedisLimiterUserProvider;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.event.SmartRedisLimiterEvent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.SmartRedisLimiterEventPayload;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.SmartRedisLimiterRecord;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SmartRedisLimiter 审计记录转换工具测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class SmartRedisLimiterAuditRecordHelperTest {

    @Test
    public void testMapsCompleteRemoteRouteExecutionWithoutAttributes() {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("authorization", "test-secret-value");
        attributes.put("subject", "test-subject-value");
        attributes.put("requestBody", "test-request-body-value");

        long beforeTimestamp = System.currentTimeMillis();
        SmartRedisLimiterRecord record = mapper(testUserProvider(), traceIdProvider()).map(event(
                SmartRedisLimiterEventPayload.builder()
                        .limitKey("smart-limiter:test-service:query-data:subject-hash")
                        .routeKey("smart-limiter:test-service:query-data:subject-hash")
                        .datasourceKey("redis7Cluster")
                        .redisMode(SmartRedisLimiterConstant.REDIS_MODE_CLUSTER)
                        .routeRequired(true)
                        .routeResolved(true)
                        .keyStrategy("path")
                        .algorithm("fixed")
                        .limitRules("2/1s,2/1m")
                        .passed(false)
                        .sourceType("INTERCEPTOR")
                        .requestUri("/api/query")
                        .httpMethod("GET")
                        .clientIp("127.0.0.1")
                        .matchedPathPattern("/api/query")
                        .attributes(attributes)
                        .limit(2L)
                        .remaining(0L)
                        .resetAt(1715635200L)
                        .durationNanos(500000L)
                        .resourceCode("query-data")
                        .policySource(SmartRedisLimiterConstant.POLICY_SOURCE_REMOTE)
                        .policyRevision(7L)
                        .build()));
        long afterTimestamp = System.currentTimeMillis();

        log.info("远程 Route 映射结果：datasource={}, redisMode={}, policySource={}, timestamp={}",
                record.getDatasourceKey(), record.getRedisMode(), record.getPolicySource(), record.getTimestamp());
        assertTrue(record.getTimestamp() >= beforeTimestamp, "审计时间不得早于映射开始时间");
        assertTrue(record.getTimestamp() <= afterTimestamp, "审计时间不得晚于映射完成时间");
        assertEquals("test-client", record.getClientId());
        assertEquals("trace-test", record.getTraceId());
        assertEquals("redis7Cluster", record.getDatasourceKey());
        assertEquals(SmartRedisLimiterConstant.REDIS_MODE_CLUSTER, record.getRedisMode());
        assertTrue(record.isRouteRequired());
        assertTrue(record.isRouteResolved());
        assertEquals("query-data", record.getResourceCode());
        assertEquals(SmartRedisLimiterConstant.POLICY_SOURCE_REMOTE, record.getPolicySource());
        assertEquals(Long.valueOf(7L), record.getPolicyRevision());
        assertNull(record.getExtra());
    }

    @Test
    public void testMapsFallbackAndAspectFields() {
        SmartRedisLimiterRecord record = mapper(Collections.<SmartRedisLimiterUserProvider>emptyList(), null).map(event(
                SmartRedisLimiterEventPayload.builder()
                        .limitKey("smart-limiter:test-service:method:submit")
                        .routeKey("smart-limiter:test-service:method:submit")
                        .redisMode(SmartRedisLimiterConstant.REDIS_MODE_UNKNOWN)
                        .routeRequired(true)
                        .routeResolved(false)
                        .keyStrategy("method")
                        .algorithm("sliding")
                        .limitRules("10/1m")
                        .passed(true)
                        .sourceType("ASPECT")
                        .methodName("submit")
                        .methodQualifiedName("example.OrderService.submit")
                        .limit(10L)
                        .remaining(9L)
                        .resetAt(1715635200L)
                        .durationNanos(100L)
                        .fallbackReason("redis-timeout")
                        .policySource(SmartRedisLimiterConstant.POLICY_SOURCE_LOCAL)
                        .build()));

        log.info("注解降级映射结果：passed={}, fallbackReason={}, routeResolved={}",
                record.isPassed(), record.getFallbackReason(), record.isRouteResolved());
        assertTrue(record.isPassed());
        assertFalse(record.isRouteResolved());
        assertEquals("redis-timeout", record.getFallbackReason());
        assertEquals("submit", record.getMethodName());
        assertNull(record.getRequestUri());
        assertEquals(SmartRedisLimiterConstant.POLICY_SOURCE_LOCAL, record.getPolicySource());
        assertNull(record.getPolicyRevision());
        assertNull(record.getExtra());
    }

    @Test
    public void testProviderFailureDoesNotBlockRecordMapping() {
        SmartRedisLimiterUserProvider failingProvider = new SmartRedisLimiterUserProvider() {
            @Override
            public String getClientId() {
                throw new IllegalStateException("test provider failure");
            }

            @Override
            public String getClientType() {
                throw new IllegalStateException("test provider failure");
            }

            @Override
            public String getUserId() {
                throw new IllegalStateException("test provider failure");
            }

            @Override
            public String getUsername() {
                throw new IllegalStateException("test provider failure");
            }
        };
        SmartRedisLimiterTraceIdProvider failingTraceIdProvider = new SmartRedisLimiterTraceIdProvider() {
            @Override
            public String getTraceId() {
                throw new IllegalStateException("test provider failure");
            }
        };

        List<SmartRedisLimiterUserProvider> providers = new ArrayList<>();
        providers.add(failingProvider);
        providers.addAll(testUserProvider());

        SmartRedisLimiterRecord record = mapper(providers, failingTraceIdProvider)
                .map(event(basePayload().build()));

        log.info("Provider 异常隔离结果：clientIdExists={}, traceIdIsNull={}",
                record.getClientId() != null, record.getTraceId() == null);
        assertEquals("test-client", record.getClientId());
        assertNull(record.getTraceId());
        assertEquals(SmartRedisLimiterConstant.POLICY_SOURCE_LOCAL, record.getPolicySource());
    }

    private SmartRedisLimiterAuditRecordHelper mapper(List<SmartRedisLimiterUserProvider> userProviders,
                                                      SmartRedisLimiterTraceIdProvider traceIdProvider) {
        return new SmartRedisLimiterAuditRecordHelper(userProviders, traceIdProvider);
    }

    private SmartRedisLimiterEvent event(SmartRedisLimiterEventPayload payload) {
        return new SmartRedisLimiterEvent(this, payload);
    }

    private SmartRedisLimiterEventPayload.SmartRedisLimiterEventPayloadBuilder basePayload() {
        return SmartRedisLimiterEventPayload.builder()
                .limitKey("smart-limiter:test-service:path")
                .routeKey("smart-limiter:test-service:path")
                .redisMode(SmartRedisLimiterConstant.REDIS_MODE_STANDALONE)
                .routeRequired(true)
                .routeResolved(true)
                .keyStrategy("path")
                .algorithm("fixed")
                .limitRules("1/1s")
                .passed(false)
                .sourceType("INTERCEPTOR")
                .limit(1L)
                .remaining(0L)
                .resetAt(1715635200L)
                .durationNanos(100L);
    }

    private List<SmartRedisLimiterUserProvider> testUserProvider() {
        return Collections.singletonList(new SmartRedisLimiterUserProvider() {
            @Override
            public String getClientId() {
                return "test-client";
            }

            @Override
            public String getClientType() {
                return "platform";
            }

            @Override
            public String getUserId() {
                return "test-user";
            }

            @Override
            public String getUsername() {
                return "test-name";
            }
        });
    }

    private SmartRedisLimiterTraceIdProvider traceIdProvider() {
        return new SmartRedisLimiterTraceIdProvider() {
            @Override
            public String getTraceId() {
                return "trace-test";
            }
        };
    }
}
