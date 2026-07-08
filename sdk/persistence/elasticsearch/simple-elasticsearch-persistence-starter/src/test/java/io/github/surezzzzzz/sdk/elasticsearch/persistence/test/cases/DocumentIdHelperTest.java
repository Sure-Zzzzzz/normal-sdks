package io.github.surezzzzzz.sdk.elasticsearch.persistence.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.DocumentIdHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * DocumentIdHelper 单元测试
 *
 * @author surezzzzzz
 */
@Slf4j
class DocumentIdHelperTest {

    @Test
    @DisplayName("uuid：生成无横线随机 ID")
    void uuidNoHyphen() {
        String id1 = DocumentIdHelper.uuid();
        String id2 = DocumentIdHelper.uuid();
        log.info("uuid id1={}, id2={}", id1, id2);
        assertNotNull(id1, "uuid 不应为空");
        assertFalse(id1.contains("-"), "uuid 不应包含横线");
        assertNotEquals(id1, id2, "连续 uuid 应不同");
    }

    @Test
    @DisplayName("sha1：固定输入输出稳定")
    void sha1Stable() {
        String result = DocumentIdHelper.sha1("abc");
        log.info("sha1 result={}", result);
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", result, "sha1 应稳定");
    }

    @Test
    @DisplayName("sha1：多字段顺序敏感")
    void sha1VarargsOrderSensitive() {
        String result1 = DocumentIdHelper.sha1("a", "b", 1);
        String result2 = DocumentIdHelper.sha1("b", "a", 1);
        log.info("sha1 result1={}, result2={}", result1, result2);
        assertNotEquals(result1, result2, "多字段 hash 应顺序敏感");
    }

    @Test
    @DisplayName("sha256：固定输入输出稳定")
    void sha256Stable() {
        String result = DocumentIdHelper.sha256("abc");
        log.info("sha256 result={}", result);
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", result, "sha256 应稳定");
    }

    @Test
    @DisplayName("join：null 转空串且分隔符稳定")
    void joinNullAsEmpty() {
        String result = DocumentIdHelper.join("a", null, 3);
        log.info("join result={}", result);
        assertEquals("a||3", result, "join 应保留字段位置");
    }
}
