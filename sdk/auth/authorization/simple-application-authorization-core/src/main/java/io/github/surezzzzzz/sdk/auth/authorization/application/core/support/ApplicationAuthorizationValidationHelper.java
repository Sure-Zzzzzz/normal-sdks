package io.github.surezzzzzz.sdk.auth.authorization.application.core.support;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.SimpleApplicationAuthorizationConstant;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.exception.ApplicationAuthorizationException;

import java.util.*;

/**
 * 应用授权校验帮助类。
 *
 * @author surezzzzzz
 */
public final class ApplicationAuthorizationValidationHelper {

    /**
     * Unicode 码点字符串比较器。
     */
    public static final Comparator<String> UNICODE_CODE_POINT_COMPARATOR = new Comparator<String>() {
        @Override
        public int compare(String left, String right) {
            int leftOffset = 0;
            int rightOffset = 0;
            while (leftOffset < left.length() && rightOffset < right.length()) {
                int leftCodePoint = left.codePointAt(leftOffset);
                int rightCodePoint = right.codePointAt(rightOffset);
                if (leftCodePoint != rightCodePoint) {
                    return leftCodePoint < rightCodePoint ? -1 : 1;
                }
                leftOffset += Character.charCount(leftCodePoint);
                rightOffset += Character.charCount(rightCodePoint);
            }
            return leftOffset == left.length() ? (rightOffset == right.length() ? 0 : -1) : 1;
        }
    };

    private ApplicationAuthorizationValidationHelper() {
        throw new UnsupportedOperationException(SimpleApplicationAuthorizationConstant.MESSAGE_HELPER_CLASS_CANNOT_INSTANTIATE);
    }

    /**
     * 校验通用标识符。
     *
     * @param value     字段值
     * @param fieldName 字段名
     * @return 合法标识符
     */
    public static String requireIdentifier(String value, String fieldName) {
        return requireText(value, fieldName, SimpleApplicationAuthorizationConstant.MAX_IDENTIFIER_CODE_POINT_COUNT);
    }

    /**
     * 校验应用标识符。
     *
     * @param value 字段值
     * @return 合法应用标识符
     */
    public static String requireApplicationCode(String value) {
        return requireText(value, SimpleApplicationAuthorizationConstant.FIELD_APPLICATION_CODE,
                SimpleApplicationAuthorizationConstant.MAX_APPLICATION_CODE_POINT_COUNT);
    }

    /**
     * 校验权限清单摘要。
     *
     * @param value 字段值
     * @return 合法权限清单摘要
     */
    public static String requireManifestDigest(String value) {
        return requireText(value, SimpleApplicationAuthorizationConstant.FIELD_MANIFEST_DIGEST,
                SimpleApplicationAuthorizationConstant.MAX_MANIFEST_DIGEST_CODE_POINT_COUNT);
    }

    /**
     * 规范化权限集合。
     *
     * @param values    输入集合
     * @param fieldName 字段名
     * @return 不可变、去重、排序后的权限集合
     */
    public static List<String> normalizePermissions(Collection<String> values, String fieldName) {
        if (values == null) {
            throw invalidContext(String.format(SimpleApplicationAuthorizationConstant.DETAIL_COLLECTION_CANNOT_BE_NULL,
                    fieldName));
        }
        LinkedHashSet<String> distinctValues = new LinkedHashSet<String>();
        for (String value : values) {
            if (value == null) {
                throw invalidContext(String.format(
                        SimpleApplicationAuthorizationConstant.DETAIL_COLLECTION_CANNOT_CONTAIN_NULL, fieldName));
            }
            distinctValues.add(requireIdentifier(value, fieldName));
            if (distinctValues.size() > SimpleApplicationAuthorizationConstant.MAX_PERMISSION_COUNT) {
                throw invalidContext(String.format(
                        SimpleApplicationAuthorizationConstant.DETAIL_COLLECTION_COUNT_TOO_LARGE, fieldName,
                        SimpleApplicationAuthorizationConstant.MAX_PERMISSION_COUNT));
            }
        }
        List<String> normalizedValues = new ArrayList<String>(distinctValues);
        Collections.sort(normalizedValues, UNICODE_CODE_POINT_COMPARATOR);
        return Collections.unmodifiableList(normalizedValues);
    }

    /**
     * 创建无效授权上下文异常。
     *
     * @param detail 安全详情
     * @return 异常
     */
    public static ApplicationAuthorizationException invalidContext(String detail) {
        return new ApplicationAuthorizationException(ErrorCode.INVALID_CONTEXT,
                String.format(ErrorMessage.INVALID_CONTEXT, detail));
    }

    private static String requireText(String value, String fieldName, int maxCodePoints) {
        if (value == null) {
            throw invalidContext(String.format(SimpleApplicationAuthorizationConstant.DETAIL_CANNOT_BE_NULL, fieldName));
        }
        if (containsIsolatedSurrogate(value)) {
            throw invalidContext(String.format(
                    SimpleApplicationAuthorizationConstant.DETAIL_CANNOT_CONTAIN_ISOLATED_SURROGATE, fieldName));
        }
        if (value.isEmpty() || isWhitespace(value.codePointAt(0)) || isWhitespace(value.codePointBefore(value.length()))) {
            throw invalidContext(String.format(
                    SimpleApplicationAuthorizationConstant.DETAIL_CANNOT_BE_BLANK_OR_OUTER_WHITESPACE, fieldName));
        }
        if (value.contains(SimpleApplicationAuthorizationConstant.DYNAMIC_EXPRESSION_PROPERTY_PREFIX)
                || value.contains(SimpleApplicationAuthorizationConstant.DYNAMIC_EXPRESSION_SPEL_PREFIX)) {
            throw invalidContext(String.format(
                    SimpleApplicationAuthorizationConstant.DETAIL_CANNOT_CONTAIN_DYNAMIC_EXPRESSION, fieldName));
        }
        for (int index = 0; index < value.length(); index++) {
            if (SimpleApplicationAuthorizationConstant.FORBIDDEN_PATTERN_CHARACTERS.indexOf(value.charAt(index)) >= 0) {
                throw invalidContext(String.format(
                        SimpleApplicationAuthorizationConstant.DETAIL_CANNOT_CONTAIN_PATTERN_CHARACTER, fieldName));
            }
        }
        int codePointCount = value.codePointCount(0, value.length());
        if (codePointCount > maxCodePoints) {
            throw invalidContext(String.format(SimpleApplicationAuthorizationConstant.DETAIL_MAXIMUM_CODE_POINT_COUNT,
                    fieldName, maxCodePoints));
        }
        return value;
    }

    private static boolean containsIsolatedSurrogate(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isHighSurrogate(character)) {
                if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    return true;
                }
                index++;
            } else if (Character.isLowSurrogate(character)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }
}
