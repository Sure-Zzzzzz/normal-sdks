package io.github.surezzzzzz.sdk.elasticsearch.persistence.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.DocumentIdHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ID 工具的已知摘要、拼接边界、UTF-8 和并发隔离合同。
 */
@Slf4j
class DocumentIdHelperTest {
    @Test
    void uuidHasFixedFormat() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 64; i++) {
            String id = DocumentIdHelper.uuid();
            assertTrue(id.matches("[0-9a-f]{32}"), "UUID 应为无横线的小写十六进制串");
            assertEquals('4', id.charAt(12), "应保留随机 UUID 的版本位");
            assertTrue(ids.add(id), "本轮生成的 UUID 不应重复");
        }
    }

    @Test
    void sha1MatchesKnownVector() {
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", DocumentIdHelper.sha1("abc"));
    }

    @Test
    void sha256MatchesKnownVector() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", DocumentIdHelper.sha256("abc"));
    }

    @Test
    void nullEmptyAndZeroFieldsKeepLegacySemantics() {
        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", DocumentIdHelper.sha1((String) null));
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", DocumentIdHelper.sha256((String) null));
        assertEquals(DocumentIdHelper.sha1(""), DocumentIdHelper.sha1((Object[]) null));
        assertEquals(DocumentIdHelper.sha256(""), DocumentIdHelper.sha256());
        assertEquals("", DocumentIdHelper.join((Object[]) null));
        assertEquals("", DocumentIdHelper.join());
        assertEquals("a||3", DocumentIdHelper.join("a", null, 3));
    }

    @Test
    void varargsUseFixedOrderWithoutNormalization() {
        assertEquals(DocumentIdHelper.sha1("a|b|1"), DocumentIdHelper.sha1("a", "b", 1));
        assertEquals(DocumentIdHelper.sha256("a|b|1"), DocumentIdHelper.sha256("a", "b", 1));
        assertNotEquals(DocumentIdHelper.sha256("a", "b"), DocumentIdHelper.sha256("b", "a"));
        assertNotEquals(DocumentIdHelper.sha256(" A "), DocumentIdHelper.sha256("a"));
    }

    @Test
    void delimiterAndNullAmbiguityAreNotSilentlyReencoded() {
        // 保留旧 ID 算法，不能为了消除歧义而在迁移时改变已有文档地址。
        assertEquals(DocumentIdHelper.sha256("a|b", "c"), DocumentIdHelper.sha256("a", "b|c"));
        assertEquals(DocumentIdHelper.sha256("a", null), DocumentIdHelper.sha256("a", ""));
    }

    @Test
    void unicodeUsesUtf8AndHexKeepsLeadingZero() throws Exception {
        String value = "\u6837\u4f8b\ud83d\ude80";
        assertEquals(hex(MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8))), DocumentIdHelper.sha1(value));
        assertEquals(hex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))), DocumentIdHelper.sha256(value));
        assertEquals("01b307acba4f54f55aafc33bb06bbbf6ca803e9a", DocumentIdHelper.sha1("1234567890"));
    }

    @Test
    void concurrentCallsDoNotShareDigestState() {
        CompletableFuture<?>[] calls = new CompletableFuture<?>[32];
        for (int i = 0; i < calls.length; i++) {
            String value = "sample-" + i;
            String expected = DocumentIdHelper.sha256(value);
            calls[i] = CompletableFuture.runAsync(() -> assertEquals(expected, DocumentIdHelper.sha256(value)));
        }
        CompletableFuture.allOf(calls).join();
    }

    private String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte value : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
        return result.toString();
    }
}
