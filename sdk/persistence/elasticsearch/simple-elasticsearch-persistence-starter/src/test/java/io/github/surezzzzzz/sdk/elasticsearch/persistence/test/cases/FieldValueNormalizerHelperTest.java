package io.github.surezzzzzz.sdk.elasticsearch.persistence.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.FieldValueNormalizerHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * FieldValueNormalizerHelper 单元测试
 *
 * @author surezzzzzz
 */
@Slf4j
class FieldValueNormalizerHelperTest {

    @Test
    @DisplayName("trim：去除首尾空白")
    void trim() {
        String result = FieldValueNormalizerHelper.trim("  AbC  ");
        log.info("trim result={}", result);
        assertEquals("AbC", result, "应去除首尾空白");
    }

    @Test
    @DisplayName("lowerCase：使用 ROOT locale 小写")
    void lowerCase() {
        String result = FieldValueNormalizerHelper.lowerCase("AbC");
        log.info("lowerCase result={}", result);
        assertEquals("abc", result, "应转小写");
    }

    @Test
    @DisplayName("trimLowerCase：去空白后转小写")
    void trimLowerCase() {
        String result = FieldValueNormalizerHelper.trimLowerCase("  AbC  ");
        log.info("trimLowerCase result={}", result);
        assertEquals("abc", result, "应去空白并转小写");
    }

    @Test
    @DisplayName("fullWidthToHalfWidth：全角 ASCII 转半角")
    void fullWidthToHalfWidth() {
        String result = FieldValueNormalizerHelper.fullWidthToHalfWidth("ＡＢＣ１２３　x");
        log.info("fullWidthToHalfWidth result={}", result);
        assertEquals("ABC123 x", result, "全角 ASCII 应转半角");
    }

    @Test
    @DisplayName("blankToNull：全空白返回 null")
    void blankToNull() {
        String result = FieldValueNormalizerHelper.blankToNull("   ");
        log.info("blankToNull result={}", result);
        assertNull(result, "全空白应返回 null");
    }

    @Test
    @DisplayName("collapseWhitespace：连续空白压成单空格")
    void collapseWhitespace() {
        String result = FieldValueNormalizerHelper.collapseWhitespace(" a   b\t c ");
        log.info("collapseWhitespace result={}", result);
        assertEquals("a b c", result, "连续空白应压成单空格");
    }

    @Test
    @DisplayName("normalizeList：逐个标准化列表元素")
    void normalizeList() {
        List<String> source = new ArrayList<String>();
        source.add(" A ");
        source.add(" B ");
        List<String> result = FieldValueNormalizerHelper.normalizeList(source, FieldValueNormalizerHelper::trimLowerCase);
        log.info("normalizeList result={}", result);
        assertEquals("a", result.get(0), "第一个元素应标准化");
        assertEquals("b", result.get(1), "第二个元素应标准化");
    }
}
