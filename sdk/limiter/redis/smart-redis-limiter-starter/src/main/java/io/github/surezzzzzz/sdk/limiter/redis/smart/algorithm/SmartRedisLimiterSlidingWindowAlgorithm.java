package io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm;

import io.github.surezzzzzz.sdk.limiter.redis.smart.annotation.SmartRedisLimiterComponent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterRedisKeyConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterStarterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.starter.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.starter.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.executor.SmartRedisLimiterRedisExecutionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 滑动窗口限流算法实现
 *
 * <p>Lua脚本返回值：[passed(1/0), limit, remaining, resetAt]
 * <ul>
 *   <li>passed: 1=通过, 0=拒绝</li>
 *   <li>limit: 最严格窗口的限流阈值</li>
 *   <li>remaining: 最严格窗口的剩余配额</li>
 *   <li>resetAt: 最短窗口的重置 Unix 时间戳（秒）</li>
 * </ul>
 * <p>窗口时间基准使用客户端 Unix epoch 微秒（跨 JVM 可比较），member 使用 UUID 保证多实例唯一。
 *
 * @author Sure.
 * @Date: 2026-05-08
 */
@SmartRedisLimiterComponent
@ConditionalOnProperty(prefix = SmartRedisLimiterConstant.CONFIG_PREFIX, name = "enable", havingValue = "true")
@Slf4j
public class SmartRedisLimiterSlidingWindowAlgorithm extends AbstractSmartRedisLimiterAlgorithm {

    private static final String LIMITER_SCRIPT =
            "local key_count = #KEYS\n" +
                    "local current_time = tonumber(ARGV[#ARGV - 1])\n" +
                    "local MICROSECONDS_PER_SECOND = 1000000\n" +
                    "local counts = {}\n" +
                    "for i = 1, key_count do\n" +
                    "    local window = tonumber(ARGV[i * 2])\n" +
                    "    redis.call('ZREMRANGEBYSCORE', KEYS[i], '-inf', current_time - window)\n" +
                    "    local current_count = redis.call('ZCARD', KEYS[i])\n" +
                    "    counts[i] = current_count\n" +
                    "    if current_count >= tonumber(ARGV[i * 2 - 1]) then\n" +
                    "        local oldest = redis.call('ZRANGE', KEYS[i], 0, 0, 'WITHSCORES')\n" +
                    "        local reset_at = math.ceil((tonumber(oldest[2]) + window) / MICROSECONDS_PER_SECOND)\n" +
                    "        return {0, tonumber(ARGV[i * 2 - 1]), 0, reset_at}\n" +
                    "    end\n" +
                    "end\n" +
                    "local min_remaining = nil\n" +
                    "local min_limit = nil\n" +
                    "local min_reset = nil\n" +
                    "for i = 1, key_count do\n" +
                    "    local limit = tonumber(ARGV[i * 2 - 1])\n" +
                    "    local window = tonumber(ARGV[i * 2])\n" +
                    "    local member = ARGV[key_count * 2 + i]\n" +
                    "    redis.call('ZADD', KEYS[i], current_time, member)\n" +
                    "    redis.call('EXPIRE', KEYS[i], math.ceil(window / MICROSECONDS_PER_SECOND) + 1)\n" +
                    "    local remaining = math.max(limit - counts[i] - 1, 0)\n" +
                    "    local oldest = redis.call('ZRANGE', KEYS[i], 0, 0, 'WITHSCORES')\n" +
                    "    local reset_at = math.ceil((tonumber(oldest[2]) + window) / MICROSECONDS_PER_SECOND)\n" +
                    "    if min_remaining == nil or remaining < min_remaining then\n" +
                    "        min_remaining = remaining\n" +
                    "        min_limit = limit\n" +
                    "        min_reset = reset_at\n" +
                    "    end\n" +
                    "end\n" +
                    "return {1, min_limit, min_remaining, min_reset}";

    @Override
    public String getAlgorithm() {
        return SmartRedisLimiterConstant.ALGORITHM_SLIDING;
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
            keys.add(buildWindowKey(baseKey, rule.getWindowSeconds(),
                    SmartRedisLimiterRedisKeyConstant.SUFFIX_SLIDING_WINDOW));
            args.add(String.valueOf(rule.getCount()));
            args.add(String.valueOf(rule.getWindowSeconds()
                    * SmartRedisLimiterStarterConstant.MICROSECONDS_PER_SECOND));
        }

        String requestId = UUID.randomUUID().toString();
        for (int i = 0; i < limitRules.size(); i++) {
            args.add(String.format(SmartRedisLimiterStarterConstant.TEMPLATE_SLIDING_WINDOW_MEMBER,
                    requestId, i));
        }

        long currentTimeMillis = System.currentTimeMillis();
        args.add(String.valueOf(currentTimeMillis
                * SmartRedisLimiterStarterConstant.MICROSECONDS_PER_MILLISECOND));
        args.add(String.valueOf(currentTimeMillis / SmartRedisLimiterConstant.MILLIS_PER_SECOND));

        SmartRedisLimiterRedisExecutionResult<List<?>> executionResult = executeRedis(baseKey,
                redisTemplate -> redisTemplate.execute(getScript(), keys, args.toArray(new Object[0])));
        List<?> result = executionResult.getValue();

        if (result == null || result.size() < SmartRedisLimiterStarterConstant.LUA_RESULT_FIELD_COUNT) {
            log.warn("SmartRedisLimiter 限流脚本返回异常，触发降级: key={}", baseKey);
            throw scriptException(ErrorCode.SLIDING_WINDOW_SCRIPT_RESULT_INVALID,
                    ErrorMessage.SLIDING_WINDOW_SCRIPT_RESULT_INVALID, executionResult);
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
