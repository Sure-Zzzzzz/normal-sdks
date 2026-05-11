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
@ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.limiter.redis.smart", name = "enable", havingValue = "true")
@Slf4j
public class SmartRedisLimiterFixedWindowAlgorithm extends AbstractSmartRedisLimiterAlgorithm {

    private static final String LIMITER_SCRIPT =
            "local key_count = #KEYS\n" +
                    "local current_time = tonumber(ARGV[key_count * 2 + 1])\n" +
                    "for i = 1, key_count do\n" +
                    "    local current = redis.call('GET', KEYS[i])\n" +
                    "    if current and tonumber(current) <= 0 then\n" +
                    "        local limit = tonumber(ARGV[i * 2 - 1])\n" +
                    "        local window = tonumber(ARGV[i * 2])\n" +
                    "        local ttl = redis.call('TTL', KEYS[i])\n" +
                    "        local reset_at = current_time + math.max(ttl, 0)\n" +
                    "        return {0, limit, 0, reset_at}\n" +
                    "    end\n" +
                    "end\n" +
                    "local min_remaining = nil\n" +
                    "local min_limit = nil\n" +
                    "local min_reset = nil\n" +
                    "local arg_idx = 1\n" +
                    "for i = 1, key_count do\n" +
                    "    local limit = tonumber(ARGV[arg_idx])\n" +
                    "    local window = tonumber(ARGV[arg_idx + 1])\n" +
                    "    arg_idx = arg_idx + 2\n" +
                    "    local current = redis.call('GET', KEYS[i])\n" +
                    "    local remaining\n" +
                    "    if not current then\n" +
                    "        redis.call('SET', KEYS[i], limit - 1, 'EX', window)\n" +
                    "        remaining = limit - 1\n" +
                    "    else\n" +
                    "        remaining = tonumber(redis.call('DECR', KEYS[i]))\n" +
                    "    end\n" +
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
                                                          String keyStrategy) {
        String baseKey = buildBaseKey(context, keyStrategy);

        List<String> keys = new ArrayList<>();
        List<String> args = new ArrayList<>();

        for (SmartRedisLimiterProperties.SmartLimitRule rule : limitRules) {
            keys.add(buildWindowKey(baseKey, rule.getWindowSeconds(), SmartRedisLimiterRedisKeyConstant.SUFFIX_SECONDS));
            args.add(String.valueOf(rule.getCount()));
            args.add(String.valueOf(rule.getWindowSeconds()));
        }

        // current_time（Unix 秒）放在末尾，供 Lua 计算 resetAt
        args.add(String.valueOf(System.currentTimeMillis() / 1000));

        List<?> result = getRedisTemplate().execute(getScript(), keys, args.toArray());

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
