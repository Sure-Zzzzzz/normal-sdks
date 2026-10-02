package io.github.surezzzzzz.sdk.auth.aksk.client.core.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.strategy.TokenCacheStrategy;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.test.SimpleAkskClientCoreTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * TokenCacheStrategy 默认 TTL 计算测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleAkskClientCoreTestApplication.class)
class TokenCacheStrategyTest {

    private final TokenCacheStrategy strategy = new TokenCacheStrategy() {
        @Override
        public String generateCacheKey(String securityContext) {
            return securityContext;
        }

        @Override
        public String get(String cacheKey) {
            return null;
        }

        @Override
        public void put(String cacheKey, String token, long expiresInSeconds) {
        }

        @Override
        public void remove(String cacheKey) {
        }
    };

    @Test
    @DisplayName("正常过期时间应提前 30 秒计算 TTL")
    void shouldSubtractThirtySecondsFromNormalExpiry() {
        long ttl = strategy.calculateTtl(3600);

        log.info("服务端过期时间: 3600 秒，缓存 TTL: {} 秒", ttl);
        assertEquals(3570, ttl, "正常过期时间应提前 30 秒缓存过期");
    }

    @Test
    @DisplayName("短过期时间的 TTL 不得低于 60 秒")
    void shouldKeepMinimumTtlForShortExpiry() {
        long ttlAtThirtySeconds = strategy.calculateTtl(30);
        long ttlAtZero = strategy.calculateTtl(0);
        long ttlAtNegative = strategy.calculateTtl(-1);

        log.info("短过期时间计算结果: 30 秒 -> {}, 0 秒 -> {}, -1 秒 -> {}",
                ttlAtThirtySeconds, ttlAtZero, ttlAtNegative);
        assertEquals(60, ttlAtThirtySeconds, "30 秒过期时间的缓存 TTL 应为 60 秒");
        assertEquals(60, ttlAtZero, "0 秒过期时间的缓存 TTL 应为 60 秒");
        assertEquals(60, ttlAtNegative, "负数过期时间的缓存 TTL 应为 60 秒");
    }
}
