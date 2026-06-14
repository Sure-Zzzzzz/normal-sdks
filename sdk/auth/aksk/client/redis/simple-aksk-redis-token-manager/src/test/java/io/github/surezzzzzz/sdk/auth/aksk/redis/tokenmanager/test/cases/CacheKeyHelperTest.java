package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.support.CacheKeyHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CacheKeyHelper 单元测试
 *
 * @author surezzzzzz
 */
class CacheKeyHelperTest {

    private static final String DEFAULT_KEY = "default";
    private static final String HEX_PATTERN = "[0-9a-f]{32}";

    @Test
    @DisplayName("null 输入返回 default")
    void testNullInput() {
        assertEquals(DEFAULT_KEY, CacheKeyHelper.generate(null));
    }

    @Test
    @DisplayName("空字符串输入返回 default")
    void testEmptyInput() {
        assertEquals(DEFAULT_KEY, CacheKeyHelper.generate(""));
    }

    @Test
    @DisplayName("纯空白输入返回 default（StringUtils.hasText 行为）")
    void testBlankInput() {
        assertEquals(DEFAULT_KEY, CacheKeyHelper.generate("   "));
        assertEquals(DEFAULT_KEY, CacheKeyHelper.generate("\t\n"));
    }

    @Test
    @DisplayName("普通输入返回 32 字符 hex")
    void testNormalInput() {
        String key = CacheKeyHelper.generate("foo");
        assertTrue(key.matches(HEX_PATTERN), "应为 32 字符 hex，实际: " + key);
    }

    @Test
    @DisplayName("相同输入多次调用结果一致（确定性）")
    void testDeterministic() {
        String input = "{\"user_id\":\"u1\",\"tenant_id\":\"t1\"}";
        String first = CacheKeyHelper.generate(input);
        for (int i = 0; i < 100; i++) {
            assertEquals(first, CacheKeyHelper.generate(input));
        }
    }

    @Test
    @DisplayName("不同输入产生不同 Key")
    void testDifferentInputs() {
        assertNotEquals(CacheKeyHelper.generate("a"), CacheKeyHelper.generate("b"));
        assertNotEquals(CacheKeyHelper.generate("foo"), CacheKeyHelper.generate("foo "));
    }

    @Test
    @DisplayName("1000 个不同输入全部不重复")
    void testNoCollisionFor1000Inputs() {
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String input = String.format("{\"user_id\":\"u%d\",\"tenant_id\":\"t%d\"}", i, i);
            keys.add(CacheKeyHelper.generate(input));
        }
        assertEquals(1000, keys.size(), "1000 个不同输入应产生 1000 个不同 Key");
    }

    @Test
    @DisplayName("长字符串（10KB JSON）正常输出 32 字符 hex")
    void testLongInput() {
        StringBuilder sb = new StringBuilder(10240);
        sb.append("{\"data\":\"");
        for (int i = 0; i < 10000; i++) {
            sb.append('x');
        }
        sb.append("\"}");
        String key = CacheKeyHelper.generate(sb.toString());
        assertTrue(key.matches(HEX_PATTERN), "长输入也应为 32 字符 hex，实际: " + key);
    }

    @Test
    @DisplayName("含中文 / emoji 的 securityContext 正常输出 32 字符 hex（UTF-8 编码）")
    void testUnicodeInput() {
        String chineseKey = CacheKeyHelper.generate("{\"用户\":\"张三\"}");
        assertTrue(chineseKey.matches(HEX_PATTERN), "中文输入应为 32 字符 hex，实际: " + chineseKey);

        String emojiKey = CacheKeyHelper.generate("{\"name\":\"\uD83D\uDE00\"}");
        assertTrue(emojiKey.matches(HEX_PATTERN), "emoji 输入应为 32 字符 hex，实际: " + emojiKey);

        // 不同 unicode 输入应得到不同 Key
        assertNotEquals(chineseKey, emojiKey);
    }

    @Test
    @DisplayName("hashCode 经典碰撞输入（Aa / BB）应映射到不同 Key")
    void testKnownHashCodeCollisionResolved() {
        // "Aa".hashCode() == "BB".hashCode() == 2112
        assertEquals(2112, "Aa".hashCode());
        assertEquals(2112, "BB".hashCode());

        String aaKey = CacheKeyHelper.generate("Aa");
        String bbKey = CacheKeyHelper.generate("BB");
        assertNotEquals(aaKey, bbKey, "经典 hashCode 碰撞输入应被 SHA-256 区分开");
    }
}
