package io.github.surezzzzzz.sdk.auth.aksk.server.core.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.server.configuration.SimpleAkskServerProperties;
import io.github.surezzzzzz.sdk.auth.aksk.server.support.RedisKeyHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Redis token 缓存 key 脱敏测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class RedisKeyHelperTest {

    @Test
    void shouldUseStableDigestWithoutBearerPlaintext() {
        String token = "bearer-token-must-not-appear-in-cache-key";
        RedisKeyHelper helper = new RedisKeyHelper(new SimpleAkskServerProperties());

        String first = helper.buildCacheKeyByToken(token, "access_token");
        String same = helper.buildCacheKeyByToken(token, "access_token");
        String otherToken = helper.buildCacheKeyByToken(token + "-other", "access_token");
        String otherType = helper.buildCacheKeyByToken(token, null);

        assertEquals(first, same);
        assertFalse(first.contains(token));
        assertNotEquals(first, otherToken);
        assertNotEquals(first, otherType);
        log.info("token 缓存 key 使用稳定摘要并按 token 类型隔离");
    }
}
