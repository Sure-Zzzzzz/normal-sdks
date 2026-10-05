package io.github.surezzzzzz.sdk.elasticsearch.persistence.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.FieldValueNormalizerHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 字段标准化的空值、字符范围、列表所有权和语言环境合同。
 */
@Slf4j
class FieldValueNormalizerHelperTest {
    @Test
    void allScalarMethodsPreserveNull() {
        assertNull(FieldValueNormalizerHelper.trim(null));
        assertNull(FieldValueNormalizerHelper.lowerCase(null));
        assertNull(FieldValueNormalizerHelper.trimLowerCase(null));
        assertNull(FieldValueNormalizerHelper.fullWidthToHalfWidth(null));
        assertNull(FieldValueNormalizerHelper.blankToNull(null));
        assertNull(FieldValueNormalizerHelper.collapseWhitespace(null));
    }

    @Test
    void trimmingAndBlankConversionKeepLegacyRules() {
        assertEquals("AbC", FieldValueNormalizerHelper.trim(" \tAbC\r\n"));
        assertEquals("abc", FieldValueNormalizerHelper.trimLowerCase(" AbC "));
        assertEquals("", FieldValueNormalizerHelper.trim(""));
        assertEquals("", FieldValueNormalizerHelper.lowerCase(""));
        assertNull(FieldValueNormalizerHelper.blankToNull(" \t\r\n"));
        assertNull(FieldValueNormalizerHelper.blankToNull(""));
        assertEquals("AbC", FieldValueNormalizerHelper.blankToNull(" AbC "));
        assertEquals("\u3000", FieldValueNormalizerHelper.trim("\u3000"));
        assertEquals("\u3000", FieldValueNormalizerHelper.blankToNull("\u3000"));
    }

    @Test
    @ResourceLock("default-locale")
    void lowerCaseDoesNotDependOnDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals("i", FieldValueNormalizerHelper.lowerCase("I"));
            assertEquals("i", FieldValueNormalizerHelper.trimLowerCase(" I "));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void fullWidthConversionCoversAsciiBoundariesOnly() {
        assertEquals("!~ A1", FieldValueNormalizerHelper.fullWidthToHalfWidth("\uff01\uff5e\u3000\uff21\uff11"));
        assertEquals("\uff00\uff5f\u4e2d\ud83d\ude80", FieldValueNormalizerHelper.fullWidthToHalfWidth("\uff00\uff5f\u4e2d\ud83d\ude80"));
        assertEquals("", FieldValueNormalizerHelper.fullWidthToHalfWidth(""));
    }

    @Test
    void whitespaceCompressionDoesNotChangeOtherUnicodeSpaces() {
        assertEquals("a b c", FieldValueNormalizerHelper.collapseWhitespace(" a\t\r\n b   c "));
        assertEquals("", FieldValueNormalizerHelper.collapseWhitespace(" \t\r\n"));
        assertEquals("a\u3000b", FieldValueNormalizerHelper.collapseWhitespace("a\u3000b"));
    }

    @Test
    void listNormalizationPreservesOrderDuplicatesAndNulls() {
        List<String> source = new ArrayList<>(Arrays.asList(" A ", null, " A ", " "));
        List<String> result = FieldValueNormalizerHelper.normalizeList(source, FieldValueNormalizerHelper::trimLowerCase);
        assertEquals(Arrays.asList("a", null, "a", ""), result);
        assertEquals(Arrays.asList(" A ", null, " A ", " "), source);
        assertNotSame(source, result);
        result.set(0, "changed");
        assertEquals(" A ", source.get(0));
        assertTrue(FieldValueNormalizerHelper.normalizeList(null, FieldValueNormalizerHelper::trim).isEmpty());
        assertTrue(FieldValueNormalizerHelper.normalizeList(Collections.emptyList(), FieldValueNormalizerHelper::trim).isEmpty());
    }

    @Test
    void nullNormalizerOnlyCopiesAndExplicitNormalizerMayDropValuesToNull() {
        List<String> source = Arrays.asList(" A ", null, " ");
        List<String> copied = FieldValueNormalizerHelper.normalizeList(source, null);
        assertEquals(source, copied);
        assertNotSame(source, copied);
        assertEquals(Arrays.asList("A", null, null), FieldValueNormalizerHelper.normalizeList(source, FieldValueNormalizerHelper::blankToNull));
    }

    @Test
    void callbackFailurePropagatesWithoutChangingSourceList() {
        List<String> source = Arrays.asList("a", "b");
        IllegalArgumentException failure = new IllegalArgumentException("测试标准化拒绝");
        assertSame(failure, assertThrows(IllegalArgumentException.class,
                () -> FieldValueNormalizerHelper.normalizeList(source, value -> {
                    throw failure;
                })));
        assertEquals(Arrays.asList("a", "b"), source);
    }
}
