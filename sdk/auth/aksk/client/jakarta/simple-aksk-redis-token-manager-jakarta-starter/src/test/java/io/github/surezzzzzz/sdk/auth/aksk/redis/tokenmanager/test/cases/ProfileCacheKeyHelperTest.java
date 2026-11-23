package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.support.CacheKeyHelper;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.support.ProfileCacheKeyHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ProfileCacheKeyHelper 单元测试
 *
 * <p>验证：default Profile 保持旧 key 算法；命名 Profile 按「Profile:凭据哈希:安全上下文」三层隔离。
 *
 * @author surezzzzzz
 */
class ProfileCacheKeyHelperTest {

    private static final String HASH = "5f8c1b0d9a734e2f8b1c0d9a734e2f8b";

    @Test
    @DisplayName("default 或空 Profile 沿用旧 key 算法")
    void testDefaultProfileKeepsLegacyKey() {
        assertEquals(CacheKeyHelper.generate(null), ProfileCacheKeyHelper.generate(null, null, null));
        assertEquals(CacheKeyHelper.generate(null), ProfileCacheKeyHelper.generate("default", null, null));
        assertEquals(CacheKeyHelper.generate("tenant-a"), ProfileCacheKeyHelper.generate("default", HASH, "tenant-a"));
    }

    @Test
    @DisplayName("命名 Profile 空安全上下文使用 default 上下文段")
    void testNamedProfileWithoutSecurityContext() {
        assertEquals("crm:" + HASH + ":default", ProfileCacheKeyHelper.generate("crm", HASH, null));
        assertEquals("crm:" + HASH + ":default", ProfileCacheKeyHelper.generate("crm", HASH, " "));
    }

    @Test
    @DisplayName("安全上下文段为 32 位小写十六进制且同上下文稳定")
    void testSecurityContextSegmentIsStableHash() {
        String first = ProfileCacheKeyHelper.generate("crm", HASH, "tenant-a");
        String second = ProfileCacheKeyHelper.generate("crm", HASH, "tenant-a");
        assertEquals(first, second);
        String segment = first.substring(first.lastIndexOf(':') + 1);
        assertTrue(segment.matches("[0-9a-f]{32}"));
    }

    @Test
    @DisplayName("Profile、凭据哈希、安全上下文任一不同则 key 不同")
    void testIsolationDimensions() {
        String base = ProfileCacheKeyHelper.generate("crm", HASH, "tenant-a");
        assertNotEquals(base, ProfileCacheKeyHelper.generate("feedback", HASH, "tenant-a"));
        assertNotEquals(base, ProfileCacheKeyHelper.generate("crm", "00000000000000000000000000000000", "tenant-a"));
        assertNotEquals(base, ProfileCacheKeyHelper.generate("crm", HASH, "tenant-b"));
    }

    @Test
    @DisplayName("命名 Profile 缺少凭据哈希时响亮失败")
    void testNamedProfileRequiresCredentialHash() {
        assertThrows(ConfigurationException.class, () -> ProfileCacheKeyHelper.generate("crm", null, null));
        assertThrows(ConfigurationException.class, () -> ProfileCacheKeyHelper.generate("crm", " ", null));
    }
}
