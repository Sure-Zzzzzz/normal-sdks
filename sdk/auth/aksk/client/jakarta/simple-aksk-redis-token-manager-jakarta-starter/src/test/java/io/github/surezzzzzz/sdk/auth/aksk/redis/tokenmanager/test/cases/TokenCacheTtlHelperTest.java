package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.support.TokenCacheTtlHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Token 缓存 TTL 计算测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class TokenCacheTtlHelperTest {

    @Test
    @DisplayName("正常有效期缓存应提前 30 秒失效")
    void shouldReserveExpiryBufferForNormalToken() {
        assertEquals(3570, TokenCacheTtlHelper.calculate(4600, 1000));
        log.info("正常有效期的缓存提前失效窗口验证通过");
    }

    @Test
    @DisplayName("短有效期 Token 缓存不得晚于真实失效时间")
    void shouldNotCacheShortLivedTokenBeyondRealExpiry() {
        assertEquals(1, TokenCacheTtlHelper.calculate(1020, 1000));
        assertEquals(1, TokenCacheTtlHelper.calculate(1001, 1000));
        log.info("短有效期 Token 的安全缓存 TTL 验证通过");
    }

    @Test
    @DisplayName("已失效 Token 不应写入缓存")
    void shouldRejectExpiredToken() {
        assertEquals(0, TokenCacheTtlHelper.calculate(1000, 1000));
        assertEquals(0, TokenCacheTtlHelper.calculate(999, 1000));
        log.info("失效 Token 的缓存拒绝规则验证通过");
    }
}
