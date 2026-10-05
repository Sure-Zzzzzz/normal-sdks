package io.github.surezzzzzz.sdk.elasticsearch.persistence.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import static io.github.surezzzzzz.sdk.elasticsearch.persistence.constant.SimpleElasticsearchPersistenceConstant.*;

/**
 * 字符串字段标准化工具；不自动挂入写入链路，也不修改输入列表。
 *
 * @author surezzzzzz
 */
public final class FieldValueNormalizerHelper {

    private FieldValueNormalizerHelper() {
    }

    /**
     * 按 String.trim 的规则去除首尾空白，不扩展为全部 Unicode 空白。
     *
     * @param value 原始值
     * @return 标准化值；null 输入返回 null
     */
    public static String trim(String value) {
        return value == null ? null : value.trim();
    }

    /**
     * 使用 ROOT 区域规则转小写，不受宿主默认语言环境影响。
     *
     * @param value 原始值
     * @return 小写值；null 输入返回 null
     */
    public static String lowerCase(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    /**
     * 去除首尾空白后转小写。
     *
     * @param value 原始值
     * @return 标准化值；null 输入返回 null
     */
    public static String trimLowerCase(String value) {
        return lowerCase(trim(value));
    }

    /**
     * 将全角 ASCII 字符和全角空格转半角，其他字符原样保留。
     *
     * @param value 原始值
     * @return 半角值；null 输入返回 null
     */
    public static String fullWidthToHalfWidth(String value) {
        if (value == null) return null;
        char[] characters = value.toCharArray();
        for (int i = 0; i < characters.length; i++) {
            if (characters[i] == FULL_WIDTH_SPACE) characters[i] = HALF_WIDTH_SPACE;
            else if (characters[i] >= FULL_WIDTH_CHAR_START && characters[i] <= FULL_WIDTH_CHAR_END)
                characters[i] = (char) (characters[i] - FULL_WIDTH_TO_HALF_WIDTH_OFFSET);
        }
        return new String(characters);
    }

    /**
     * 去除首尾空白；结果为空时转为 null。
     *
     * @param value 原始值
     * @return 标准化值；null 或空白输入返回 null
     */
    public static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 去除首尾空白并将正则 \s 匹配的连续空白压缩为单个空格。
     * 不将全角空格等其他 Unicode 空白隐式改写。
     *
     * @param value 原始值
     * @return 标准化值；null 输入返回 null
     */
    public static String collapseWhitespace(String value) {
        return value == null ? null : value.trim().replaceAll(REGEX_WHITESPACE_GROUP, SINGLE_SPACE);
    }

    /**
     * 按原顺序逐项处理到新列表，不去重、不丢弃 null；函数异常原样传播。
     *
     * @param valueList  原始列表；null 返回空列表
     * @param normalizer 标准化函数；null 时只复制列表
     * @return 标准化后的新列表
     */
    public static List<String> normalizeList(List<String> valueList, Function<String, String> normalizer) {
        if (valueList == null) return Collections.emptyList();
        List<String> result = new ArrayList<>(valueList.size());
        for (String value : valueList) result.add(normalizer == null ? value : normalizer.apply(value));
        return result;
    }
}
