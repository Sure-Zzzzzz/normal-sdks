package io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm;

import io.github.surezzzzzz.sdk.limiter.redis.smart.annotation.SmartRedisLimiterComponent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterRedisKeyConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.ArrayList;
import java.util.List;

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
 *
 * @author Sure.
 * @Date: 2026-05-08
 */
@SmartRedisLimiterComponent
@ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.limiter.redis.smart", name = "enable", havingValue = "true")
@Slf4j
public class SmartRedisLimiterSlidingWindowAlgorithm extends AbstractSmartRedisLimiterAlgorithm {

    private static final String LIMITER_SCRIPT =
            "local key_count = #KEYS\n" +
                    "local current_time = tonumber(ARGV[#ARGV - 1])\n" +
                    "local current_time_sec = tonumber(ARGV[#ARGV])\n" +
                    "local NANOSECONDS_PER_SECOND = " + SmartRedisLimiterRedisKeyConstant.NANOSECONDS_PER_SECOND + "\n" +
                    "local min_remaining = nil\n" +
                    "local min_limit = nil\n" +
                    "local min_reset = nil\n" +
                    "local pass_count = 0\n" +
                    "for i = 1, key_count do\n" +
                    "    local window_key = KEYS[i]\n" +
                    "    local limit = tonumber(ARGV[i * 2 - 1])\n" +
                    "    local window = tonumber(ARGV[i * 2])\n" +
                    "    local window_sec = math.ceil(window / NANOSECONDS_PER_SECOND)\n" +
                    "    local reset_at = current_time_sec + window_sec\n" +
                    "\n" +
                    "    -- 优化：先只读计数，不清理\n" +
                    "    local current_count = redis.call('ZCARD', window_key)\n" +
                    "    local remaining = limit - current_count\n" +
                    "\n" +
                    "    -- 配额用尽或已达阈值才触发过期数据清理\n" +
                    "    if remaining <= 0 then\n" +
                    "        local window_start = current_time - window\n" +
                    "        redis.call('ZREMRANGEBYSCORE', window_key, '-inf', window_start)\n" +
                    "        current_count = redis.call('ZCARD', window_key)\n" +
                    "        remaining = limit - current_count\n" +
                    "    end\n" +
                    "\n" +
                    "    if min_remaining == nil or remaining < min_remaining then\n" +
                    "        min_remaining = remaining\n" +
                    "        min_limit = limit\n" +
                    "        min_reset = reset_at\n" +
                    "    end\n" +
                    "    if current_count < limit then\n" +
                    "        pass_count = pass_count + 1\n" +
                    "    end\n" +
                    "end\n" +
                    "if pass_count == key_count then\n" +
                    "    for i = 1, key_count do\n" +
                    "        local window_key = KEYS[i]\n" +
                    "        local window = tonumber(ARGV[i * 2])\n" +
                    "        local member = ARGV[key_count * 2 + i]\n" +
                    "        redis.call('ZADD', window_key, current_time, member)\n" +
                    "        redis.call('EXPIRE', window_key, math.ceil(window / NANOSECONDS_PER_SECOND) + 1)\n" +
                    "    end\n" +
                    "    return {1, min_limit, min_remaining, min_reset}\n" +
                    "end\n" +
                    "return {0, min_limit, min_remaining, min_reset}";

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
                                                          String keyStrategy) {
        String baseKey = buildBaseKey(context, keyStrategy);

        List<String> keys = new ArrayList<>();
        List<String> args = new ArrayList<>();

        long currentTimeNano = System.nanoTime();

        for (SmartRedisLimiterProperties.SmartLimitRule rule : limitRules) {
            keys.add(buildWindowKey(baseKey, rule.getWindowSeconds(), SmartRedisLimiterRedisKeyConstant.SUFFIX_SLIDING_WINDOW));
            args.add(String.valueOf(rule.getCount()));
            args.add(String.valueOf(rule.getWindowSeconds() * SmartRedisLimiterRedisKeyConstant.NANOSECONDS_PER_SECOND));
        }

        String member = "m-" + Thread.currentThread().getId() + "-" + currentTimeNano;
        args.add(member);

        // current_time（纳秒）和 current_time_sec（Unix秒）放在末尾
        args.add(String.valueOf(currentTimeNano));
        args.add(String.valueOf(System.currentTimeMillis() / 1000));

        List<?> result = getRedisTemplate().execute(getScript(), keys, args.toArray(new Object[0]));

        if (result == null || result.size() < 4) {
            log.warn("SmartRedisLimiter 限流脚本返回异常，默认拒绝: key={}", baseKey);
            return SmartRedisLimiterResult.builder()
                    .passed(false)
                    .limit(0)
                    .remaining(0)
                    .resetAt(0)
                    .build();
        }

        boolean passed = Long.valueOf(1).equals(toLong(result.get(0)));
        long limit = toLong(result.get(1));
        long remaining = toLong(result.get(2));
        long resetAt = toLong(result.get(3));

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
                .build();
    }

    private static long toLong(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
