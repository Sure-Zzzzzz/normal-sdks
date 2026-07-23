package io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm;

import io.github.surezzzzzz.sdk.limiter.redis.smart.annotation.SmartRedisLimiterComponent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterStarterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.starter.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.starter.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.executor.SmartRedisLimiterRedisExecutionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * 固定窗口限流算法实现
 *
 * <p>Lua脚本返回值：[passed(1/0), limit, remaining, resetAt]
 * <ul>
 *   <li>passed: 1=通过, 0=拒绝</li>
 *   <li>limit: 最严格窗口的限流阈值</li>
 *   <li>remaining: 最严格窗口的剩余配额</li>
 *   <li>resetAt: 最短窗口的重置 Unix 时间戳（秒）</li>
 * </ul>
 *
 * @author Sure.
 * @Date: 2026-05-08
 */
@SmartRedisLimiterComponent
@ConditionalOnProperty(prefix = SmartRedisLimiterConstant.CONFIG_PREFIX, name = "enable", havingValue = "true")
@Slf4j
public class SmartRedisLimiterFixedWindowAlgorithm extends AbstractSmartRedisLimiterAlgorithm {

    private static final String LIMITER_SCRIPT =
            "local key_count = #KEYS\n" +
                    "local current_time = tonumber(ARGV[key_count * 2 + 1])\n" +
                    "for i = 1, key_count do\n" +
                    "    local limit = tonumber(ARGV[i * 2 - 1])\n" +
                    "    local used = tonumber(redis.call('GET', KEYS[i]) or '0')\n" +
                    "    if used >= limit then\n" +
                    "        local ttl = redis.call('TTL', KEYS[i])\n" +
                    "        return {0, limit, 0, current_time + math.max(ttl, 0)}\n" +
                    "    end\n" +
                    "end\n" +
                    "local min_remaining = nil\n" +
                    "local min_limit = nil\n" +
                    "local min_reset = nil\n" +
                    "for i = 1, key_count do\n" +
                    "    local limit = tonumber(ARGV[i * 2 - 1])\n" +
                    "    local window = tonumber(ARGV[i * 2])\n" +
                    "    local used\n" +
                    "    if redis.call('SET', KEYS[i], 1, 'EX', window, 'NX') then\n" +
                    "        used = 1\n" +
                    "    else\n" +
                    "        used = tonumber(redis.call('INCR', KEYS[i]))\n" +
                    "    end\n" +
                    "    local remaining = math.max(limit - used, 0)\n" +
                    "    if min_remaining == nil or remaining < min_remaining then\n" +
                    "        min_remaining = remaining\n" +
                    "        min_limit = limit\n" +
                    "        local ttl = redis.call('TTL', KEYS[i])\n" +
                    "        min_reset = current_time + math.max(ttl, 0)\n" +
                    "    end\n" +
                    "end\n" +
                    "return {1, min_limit, min_remaining, min_reset}";

    @Override
    public String getAlgorithm() {
        return SmartRedisLimiterConstant.ALGORITHM_FIXED;
    }

    @Override
    protected String getScriptText() {
        return LIMITER_SCRIPT;
    }

    @Override
    protected SmartRedisLimiterResult doExecuteWithResult(SmartRedisLimiterContext context,
                                                          List<SmartRedisLimiterProperties.SmartLimitRule> limitRules,
                                                          String keyStrategy,
                                                          String baseKey) {
        List<String> keys = new ArrayList<>();
        List<String> args = new ArrayList<>();

        for (SmartRedisLimiterProperties.SmartLimitRule rule : limitRules) {
            keys.add(io.github.surezzzzzz.sdk.limiter.redis.smart.support.SmartRedisLimiterKeyHelper
                    .buildFixedUsedWindowKey(
                            baseKey,
                            rule.getWindowSeconds(),
                            Boolean.TRUE.equals(getProperties().getRedis().getUseHashTag())));
            args.add(String.valueOf(rule.getCount()));
            args.add(String.valueOf(rule.getWindowSeconds()));
        }

        args.add(String.valueOf(System.currentTimeMillis() / SmartRedisLimiterConstant.MILLIS_PER_SECOND));

        SmartRedisLimiterRedisExecutionResult<List<?>> executionResult = executeRedis(baseKey,
                redisTemplate -> redisTemplate.execute(getScript(), keys, args.toArray()));
        List<?> result = executionResult.getValue();

        if (result == null || result.size() < SmartRedisLimiterStarterConstant.LUA_RESULT_FIELD_COUNT) {
            log.warn("SmartRedisLimiter 限流脚本返回异常，触发降级: key={}", baseKey);
            throw scriptException(ErrorCode.FIXED_WINDOW_SCRIPT_RESULT_INVALID,
                    ErrorMessage.FIXED_WINDOW_SCRIPT_RESULT_INVALID, executionResult);
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

        if (!passed) {
            log.warn("SmartRedisLimiter 限流触发: key={}, rules={}", baseKey, limitRules);
        } else {
            log.debug("SmartRedisLimiter 限流通过: key={}", baseKey);
        }

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
}
