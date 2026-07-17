package io.github.surezzzzzz.sdk.retry.redis.smart.test.cases;

import io.github.surezzzzzz.sdk.retry.redis.smart.configuration.SmartRedisRetryProperties;
import io.github.surezzzzzz.sdk.retry.redis.smart.constant.SmartRedisRetryConstant;
import io.github.surezzzzzz.sdk.retry.redis.smart.support.RetryKeyHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link RetryKeyHelper} 单元测试
 *
 * @author surezzzzzz
 */
@Slf4j
class RetryKeyHelperTest {

    private SmartRedisRetryProperties properties;
    private RetryKeyHelper retryKeyHelper;

    @BeforeEach
    void setUp() {
        properties = new SmartRedisRetryProperties();
        retryKeyHelper = new RetryKeyHelper(properties);
        log.info("初始化 RetryKeyHelper 测试");
    }

    @Test
    void sha1HexShouldBeStableAndUpperCase() {
        String hash = retryKeyHelper.sha1Hex("test-key");
        assertEquals("3ACFB2C2B433C0EA7FF107E33DF91B18E52F960F", hash);
    }

    @Test
    void buildRedisKeyShouldUseHashTagByDefault() {
        String redisKey = retryKeyHelper.buildRedisKey("test-compensation", "test-key");
        String hash = retryKeyHelper.sha1Hex("test-key");
        assertTrue(redisKey.endsWith("::{" + hash + "}"), "默认应包裹 hash tag");
        assertTrue(redisKey.startsWith(SmartRedisRetryConstant.DEFAULT_KEY_PREFIX));
    }

    @Test
    void buildRedisKeyShouldNotUseHashTagWhenDisabled() {
        properties.getRedis().setUseHashTag(false);
        String redisKey = retryKeyHelper.buildRedisKey("test-compensation", "test-key");
        String hash = retryKeyHelper.sha1Hex("test-key");
        assertFalse(redisKey.contains("{" + hash + "}"), "关闭 hash tag 后不应包裹");
        assertTrue(redisKey.endsWith("::" + hash));
    }

    @Test
    void buildScanPatternShouldContainRetryTypeAndMe() {
        String pattern = retryKeyHelper.buildScanPattern("test-compensation");
        assertNotNull(pattern);
        assertTrue(pattern.startsWith(SmartRedisRetryConstant.DEFAULT_KEY_PREFIX));
        assertTrue(pattern.contains("test-compensation"));
        assertTrue(pattern.endsWith("::*"));
    }
}
