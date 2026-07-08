package io.github.surezzzzzz.sdk.elasticsearch.persistence.support;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.constant.SimpleElasticsearchPersistenceConstant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Field Value Normalizer Helper
 *
 * @author surezzzzzz
 */
public final class FieldValueNormalizerHelper {

    private FieldValueNormalizerHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 去除首尾空白。
     *
     * @param value 原始值
     * @return 标准化后的值
     */
    public static String trim(String value) {
        return value == null ? null : value.trim();
    }

    /**
     * 转为小写。
     *
     * @param value 原始值
     * @return 标准化后的值
     */
    public static String lowerCase(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    /**
     * 去除首尾空白后转为小写。
     *
     * @param value 原始值
     * @return 标准化后的值
     */
    public static String trimLowerCase(String value) {
        return lowerCase(trim(value));
    }

    /**
     * 全角 ASCII 转半角。
     *
     * @param value 原始值
     * @return 标准化后的值
     */
    public static String fullWidthToHalfWidth(String value) {
        if (value == null) {
            return null;
        }
        char[] chars = value.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] == SimpleElasticsearchPersistenceConstant.FULL_WIDTH_SPACE) {
                chars[i] = SimpleElasticsearchPersistenceConstant.HALF_WIDTH_SPACE;
            } else if (chars[i] >= SimpleElasticsearchPersistenceConstant.FULL_WIDTH_CHAR_START
                    && chars[i] <= SimpleElasticsearchPersistenceConstant.FULL_WIDTH_CHAR_END) {
                chars[i] = (char) (chars[i] - SimpleElasticsearchPersistenceConstant.FULL_WIDTH_TO_HALF_WIDTH_OFFSET);
            }
        }
        return new String(chars);
    }

    /**
     * 空白字符串转 null。
     *
     * @param value 原始值
     * @return 标准化后的值
     */
    public static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 压缩连续空白。
     *
     * @param value 原始值
     * @return 标准化后的值
     */
    public static String collapseWhitespace(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().replaceAll(SimpleElasticsearchPersistenceConstant.REGEX_WHITESPACE_GROUP,
                SimpleElasticsearchPersistenceConstant.SINGLE_SPACE);
    }

    /**
     * 标准化字符串列表。
     *
     * @param valueList 原始列表
     * @param normalizer 标准化函数
     * @return 标准化后的列表
     */
    public static List<String> normalizeList(List<String> valueList, Function<String, String> normalizer) {
        if (valueList == null) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<String>(valueList.size());
        for (String value : valueList) {
            result.add(normalizer == null ? value : normalizer.apply(value));
        }
        return result;
    }
}
