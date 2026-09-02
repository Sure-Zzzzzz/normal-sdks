package io.github.surezzzzzz.sdk.auth.captcha.storage;

import io.github.surezzzzzz.sdk.auth.captcha.annotation.SimpleCaptchaComponent;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.util.StringUtils;


import java.time.Duration;
import java.util.List;

/**
 * 挑战存储的 Redis 实现（唯一实现：出题与验题可落在不同实例，强制 Redis 共享）
 *
 * <p>key 由调用方按模块 key 规范构建（me 段 HashTag），经 redis-route
 * stringTemplateByKey 按 key 路由落数据源；TTL 即过期；consume 用
 * MULTI/EXEC 事务原子取删，保证挑战一次性消费（getAndDelete 需
 * spring-data-redis 2.6+，SB 2.2~2.7 四版本矩阵要求全兼容）。
 * 宿主无 redis-route bean 时启动快速失败。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleCaptchaComponent
@RequiredArgsConstructor
public class RedisRouteChallengeStore implements ChallengeStore {

    private final RedisRouteTemplate redisRouteTemplate;

    private static String shortKey(String key) {
        return key == null ? "null" : key.substring(0, Math.min(24, key.length()));
    }

    @Override
    public void save(String key, String answer, Duration ttl) {
        redisRouteTemplate.stringTemplateByKey(key).opsForValue().set(key, answer, ttl);
        log.debug("验证码挑战已暂存：key={}, ttl={}s", shortKey(key), ttl.getSeconds());
    }

    /**
     * 事务内 GET 后 DEL：并发两个消费请求只有一个拿到答案（原子一次性）
     */
    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public String consume(String key) {
        List<Object> results = redisRouteTemplate.stringTemplateByKey(key)
                .execute(new SessionCallback<List<Object>>() {
                    @Override
                    public List<Object> execute(RedisOperations operations) {
                        operations.multi();
                        operations.opsForValue().get(key);
                        operations.delete(key);
                        return operations.exec();
                    }
                });
        Object stored = results == null || results.isEmpty() ? null : results.get(0);
        if (!(stored instanceof String) || !StringUtils.hasText((String) stored)) {
            log.debug("验证码挑战不存在或已消费：key={}", shortKey(key));
            return null;
        }
        log.debug("验证码挑战已消费（原子取删）：key={}", shortKey(key));
        return (String) stored;
    }
}
