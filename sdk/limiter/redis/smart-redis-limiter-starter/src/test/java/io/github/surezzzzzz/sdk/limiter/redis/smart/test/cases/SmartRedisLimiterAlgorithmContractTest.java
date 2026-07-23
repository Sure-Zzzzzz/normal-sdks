package io.github.surezzzzzz.sdk.limiter.redis.smart.test.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.AbstractSmartRedisLimiterAlgorithm;
import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterContext;
import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterResult;
import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterContextAttribute;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterStarterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterTimeUnit;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.starter.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterScriptException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.executor.SmartRedisLimiterRedisExecutionResult;
import io.github.surezzzzzz.sdk.limiter.redis.smart.executor.SmartRedisLimiterRedisExecutor;
import io.github.surezzzzzz.sdk.limiter.redis.smart.executor.SmartRedisLimiterTimeoutExecutor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SmartRedisLimiter 算法公共契约测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class SmartRedisLimiterAlgorithmContractTest {

    private SmartRedisLimiterTimeoutExecutor timeoutExecutor;

    @AfterEach
    public void tearDown() {
        if (timeoutExecutor != null) {
            timeoutExecutor.destroy();
            timeoutExecutor = null;
        }
    }

    @Test
    public void testMalformedLuaListTriggersScriptErrorFallback() {
        ContractAlgorithm algorithm = createAlgorithm(new FakeRedisExecutor(Arrays.asList(
                "not-a-number", 10L, 9L, 1000L)));
        SmartRedisLimiterContext context = SmartRedisLimiterContext.builder().build();

        SmartRedisLimiterResult result = algorithm.tryAcquireWithResult(
                context, Collections.singletonList(rule()), null, "deny");

        log.info("Lua 字段非法降级结果: passed={}, fallbackReason={}",
                result.isPassed(), result.getFallbackReason());
        assertFalse(result.isPassed(), "fallback=deny 时脚本协议异常必须拒绝");
        assertTrue(result.isFallback(), "脚本协议异常必须标记为降级结果");
        assertEquals(SmartRedisLimiterConstant.FALLBACK_REASON_SCRIPT_ERROR,
                result.getFallbackReason(), "Lua 字段解析失败必须归类为 script_error");
        assertEquals("limiter", result.getDatasourceKey(), "脚本异常应保留 route 快照");
    }

    @Test
    public void testShortLuaListTriggersScriptErrorFallback() {
        ContractAlgorithm algorithm = createAlgorithm(new FakeRedisExecutor(Collections.singletonList(1L)));
        SmartRedisLimiterContext context = SmartRedisLimiterContext.builder().build();

        SmartRedisLimiterResult result = algorithm.tryAcquireWithResult(
                context, Collections.singletonList(rule()), null, "allow");

        assertTrue(result.isFallback(), "Lua 返回长度不足必须进入 fallback");
        assertEquals(SmartRedisLimiterConstant.FALLBACK_REASON_SCRIPT_ERROR,
                result.getFallbackReason(), "Lua 返回长度不足必须归类为 script_error");
    }

    @Test
    public void testSuccessfulResultCopiesRouteSnapshotToContext() {
        ContractAlgorithm algorithm = createAlgorithm(new FakeRedisExecutor(Arrays.asList(1L, 10L, 9L, 1000L)));
        SmartRedisLimiterContext context = SmartRedisLimiterContext.builder().build();

        SmartRedisLimiterResult result = algorithm.tryAcquireWithResult(
                context, Collections.singletonList(rule()), null, "deny");

        log.info("正常执行 route 快照: routeKey={}, datasource={}, durationNanos={}",
                result.getRouteKey(), result.getDatasourceKey(),
                context.getAttribute(SmartRedisLimiterContextAttribute.DURATION_NANOS));
        assertTrue(result.isPassed(), "Lua 放行结果应通过");
        assertFalse(result.isFallback(), "正常执行不应标记降级");
        assertEquals(result.getRouteKey(), context.getAttribute(SmartRedisLimiterContextAttribute.ROUTE_KEY),
                "正常结果 routeKey 应由业务线程写回 context");
        assertEquals(result.getDatasourceKey(),
                context.getAttribute(SmartRedisLimiterContextAttribute.DATASOURCE_KEY),
                "正常结果 datasourceKey 应由业务线程写回 context");
        assertTrue(Boolean.TRUE.equals(context.getAttribute(SmartRedisLimiterContextAttribute.ROUTE_RESOLVED)),
                "正常结果 routeResolved 应由业务线程写回 context");
        Object duration = context.getAttribute(SmartRedisLimiterContextAttribute.DURATION_NANOS);
        assertTrue(duration instanceof Long && (Long) duration >= 0L,
                "正常结果应写回 DURATION_NANOS");
    }

    @Test
    public void testTimeoutFallbackReasonAndLateResultDoesNotOverwriteContext() {
        ContractAlgorithm algorithm = createAlgorithm(new FakeRedisExecutor(Arrays.asList(1L, 10L, 9L, 1000L)) {
            @Override
            public <T> SmartRedisLimiterRedisExecutionResult<T> execute(
                    String routeKey, Function<StringRedisTemplate, T> callback) {
                try {
                    Thread.sleep(300L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return super.execute(routeKey, callback);
            }
        }, 50L);
        SmartRedisLimiterContext context = SmartRedisLimiterContext.builder().build();

        SmartRedisLimiterResult result = algorithm.tryAcquireWithResult(
                context, Collections.singletonList(rule()), null, "allow");

        log.info("超时降级结果: passed={}, fallbackReason={}",
                result.isPassed(), result.getFallbackReason());
        assertTrue(result.isFallback(), "超时必须进入 fallback");
        assertEquals(SmartRedisLimiterConstant.FALLBACK_REASON_TIMEOUT,
                result.getFallbackReason(), "超时降级原因必须为 timeout");
        assertNotEquals("limiter",
                context.getAttribute(SmartRedisLimiterContextAttribute.DATASOURCE_KEY),
                "超时后迟到结果不得把 route 快照写回业务线程 context");
    }

    @Test
    public void testParseScriptLongHandlesNumberAndString() {
        ContractAlgorithm algorithm = createAlgorithm(new FakeRedisExecutor(Arrays.asList(1L, 10L, 9L, 1000L)));
        SmartRedisLimiterRedisExecutionResult<Object> executionResult = executionResult();

        assertEquals(5L, algorithm.parseValue(5L, executionResult), "Number 类型应直接取 longValue");
        assertEquals(7L, algorithm.parseValue("7", executionResult), "数字字符串应解析成功");

        SmartRedisLimiterScriptException exception = assertThrows(
                SmartRedisLimiterScriptException.class,
                () -> algorithm.parseValue("abc", executionResult));
        assertTrue(exception.getMessage().contains("remaining"),
                "非数字字符串异常应包含字段名");
        assertEquals("limiter", exception.getDatasourceKey(), "字段解析异常应保留 route 快照");
    }

    private ContractAlgorithm createAlgorithm(SmartRedisLimiterRedisExecutor redisExecutor) {
        return createAlgorithm(redisExecutor, 1000L);
    }

    private ContractAlgorithm createAlgorithm(SmartRedisLimiterRedisExecutor redisExecutor, long commandTimeout) {
        SmartRedisLimiterProperties properties = new SmartRedisLimiterProperties();
        properties.setMe("test");
        properties.getRedis().setCommandTimeout(commandTimeout);
        timeoutExecutor = new SmartRedisLimiterTimeoutExecutor(properties);
        ContractAlgorithm algorithm = new ContractAlgorithm();
        ReflectionTestUtils.setField(algorithm, "properties", properties);
        ReflectionTestUtils.setField(algorithm, "timeoutExecutor", timeoutExecutor);
        ReflectionTestUtils.setField(algorithm, "redisExecutor", redisExecutor);
        return algorithm;
    }

    private SmartRedisLimiterRedisExecutionResult<Object> executionResult() {
        return SmartRedisLimiterRedisExecutionResult.builder()
                .routeKey("smart-limiter:test:method:query")
                .datasourceKey("limiter")
                .redisMode(SmartRedisLimiterConstant.REDIS_MODE_STANDALONE)
                .routeRequired(true)
                .routeResolved(true)
                .build();
    }

    private SmartRedisLimiterProperties.SmartLimitRule rule() {
        SmartRedisLimiterProperties.SmartLimitRule rule = new SmartRedisLimiterProperties.SmartLimitRule();
        rule.setCount(10L);
        rule.setWindow(1L);
        rule.setUnit(SmartRedisLimiterTimeUnit.SECONDS);
        return rule;
    }

    private static class FakeRedisExecutor implements SmartRedisLimiterRedisExecutor {

        private final List<?> luaResult;

        private FakeRedisExecutor(List<?> luaResult) {
            this.luaResult = luaResult;
        }

        @Override
        public <T> SmartRedisLimiterRedisExecutionResult<T> execute(
                String routeKey, Function<StringRedisTemplate, T> callback) {
            @SuppressWarnings("unchecked")
            T value = (T) luaResult;
            return SmartRedisLimiterRedisExecutionResult.<T>builder()
                    .value(value)
                    .routeKey(routeKey)
                    .datasourceKey("limiter")
                    .redisMode(SmartRedisLimiterConstant.REDIS_MODE_STANDALONE)
                    .routeRequired(true)
                    .routeResolved(true)
                    .build();
        }
    }

    private static class ContractAlgorithm extends AbstractSmartRedisLimiterAlgorithm {

        @Override
        public String getAlgorithm() {
            return SmartRedisLimiterConstant.ALGORITHM_FIXED;
        }

        @Override
        public DefaultRedisScript<List> getScript() {
            return null;
        }

        @Override
        protected String getScriptText() {
            return "";
        }

        @Override
        protected SmartRedisLimiterResult doExecuteWithResult(
                SmartRedisLimiterContext context,
                List<SmartRedisLimiterProperties.SmartLimitRule> limitRules,
                String keyStrategy,
                String baseKey) {
            SmartRedisLimiterRedisExecutionResult<List<?>> executionResult = executeRedis(baseKey,
                    redisTemplate -> null);
            List<?> result = executionResult.getValue();

            if (result == null || result.size() < SmartRedisLimiterStarterConstant.LUA_RESULT_FIELD_COUNT) {
                throw scriptException(ErrorMessage.FIXED_WINDOW_SCRIPT_RESULT_INVALID, executionResult);
            }
            boolean passed = parseScriptLong(
                    result.get(SmartRedisLimiterStarterConstant.LUA_RESULT_PASSED_INDEX),
                    SmartRedisLimiterStarterConstant.LUA_RESULT_PASSED_FIELD,
                    executionResult) == SmartRedisLimiterStarterConstant.LUA_RESULT_PASSED;
            long limit = parseScriptLong(
                    result.get(SmartRedisLimiterStarterConstant.LUA_RESULT_LIMIT_INDEX),
                    SmartRedisLimiterStarterConstant.LUA_RESULT_LIMIT_FIELD,
                    executionResult);
            long remaining = parseScriptLong(
                    result.get(SmartRedisLimiterStarterConstant.LUA_RESULT_REMAINING_INDEX),
                    SmartRedisLimiterStarterConstant.LUA_RESULT_REMAINING_FIELD,
                    executionResult);
            long resetAt = parseScriptLong(
                    result.get(SmartRedisLimiterStarterConstant.LUA_RESULT_RESET_AT_INDEX),
                    SmartRedisLimiterStarterConstant.LUA_RESULT_RESET_AT_FIELD,
                    executionResult);
            return SmartRedisLimiterResult.builder()
                    .passed(passed)
                    .limit(limit)
                    .remaining(remaining)
                    .resetAt(resetAt)
                    .routeKey(executionResult.getRouteKey())
                    .datasourceKey(executionResult.getDatasourceKey())
                    .redisMode(executionResult.getRedisMode())
                    .routeRequired(executionResult.isRouteRequired())
                    .routeResolved(executionResult.isRouteResolved())
                    .build();
        }

        @Override
        public String buildBaseKey(SmartRedisLimiterContext context, String keyStrategy) {
            return "smart-limiter:test:method:query";
        }

        private long parseValue(Object value, SmartRedisLimiterRedisExecutionResult<?> executionResult) {
            return parseScriptLong(value, "remaining", executionResult);
        }
    }
}
