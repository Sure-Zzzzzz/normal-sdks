package io.github.surezzzzzz.sdk.auth.data.permission.core.support;

import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.SimpleDataPermissionConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.exception.DataPermissionValidationException;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataConstraint;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;

import java.util.*;

/**
 * 数据权限校验与规范化帮助类。
 *
 * @author surezzzzzz
 */
public final class DataPermissionValidationHelper {

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

    /**
     * 授权项规范排序比较器。
     */
    public static final Comparator<DataGrant> GRANT_COMPARATOR = new Comparator<DataGrant>() {
        @Override
        public int compare(DataGrant left, DataGrant right) {
            int result = UNICODE_CODE_POINT_COMPARATOR.compare(left.getResource(), right.getResource());
            if (result != 0) {
                return result;
            }
            result = compareStrings(left.getActions(), right.getActions());
            if (result != 0) {
                return result;
            }
            result = Boolean.valueOf(left.isAll()).compareTo(right.isAll());
            if (result != 0) {
                return result;
            }
            return compareConstraints(left.getConstraints(), right.getConstraints());
        }
    };

    private DataPermissionValidationHelper() {
        throw new UnsupportedOperationException(SimpleDataPermissionConstant.MESSAGE_HELPER_CLASS_CANNOT_INSTANTIATE);
    }

    /**
     * 校验标识符。
     *
     * @param value        字段值
     * @param fieldName    字段名
     * @param errorCode    错误码
     * @param errorMessage 错误信息模板
     * @return 原始合法标识符
     */
    public static String requireIdentifier(String value, String fieldName, String errorCode, String errorMessage) {
        return requireText(value, fieldName, SimpleDataPermissionConstant.MAX_IDENTIFIER_CODE_POINT_COUNT,
                errorCode, errorMessage);
    }

    /**
     * 校验约束值。
     *
     * @param value     字段值
     * @param fieldName 字段名
     * @return 原始合法约束值
     */
    public static String requireValue(String value, String fieldName) {
        return requireText(value, fieldName, SimpleDataPermissionConstant.MAX_VALUE_CODE_POINT_COUNT,
                ErrorCode.INVALID_CONSTRAINT, ErrorMessage.INVALID_CONSTRAINT);
    }

    /**
     * 校验并复制集合。
     *
     * @param values       输入集合
     * @param fieldName    字段名
     * @param maximum      最大数量
     * @param errorCode    错误码
     * @param errorMessage 错误信息模板
     * @param <T>          集合元素类型
     * @return 可修改的防御性副本
     */
    public static <T> List<T> requireCollection(Collection<T> values, String fieldName, int maximum, String errorCode,
                                                String errorMessage) {
        if (values == null) {
            throw validation(errorCode, errorMessage,
                    String.format(SimpleDataPermissionConstant.DETAIL_CANNOT_BE_NULL, fieldName));
        }
        List<T> copiedValues = new ArrayList<T>();
        for (T value : values) {
            if (value == null) {
                throw validation(errorCode, errorMessage,
                        String.format(SimpleDataPermissionConstant.DETAIL_CANNOT_CONTAIN_NULL, fieldName));
            }
            copiedValues.add(value);
            if (copiedValues.size() > maximum) {
                throw validation(errorCode, errorMessage,
                        String.format(SimpleDataPermissionConstant.DETAIL_MAXIMUM_COUNT, fieldName, maximum));
            }
        }
        return copiedValues;
    }

    /**
     * 规范化字符串集合。
     *
     * @param values        输入集合
     * @param fieldName     字段名
     * @param maximum       最大数量
     * @param maxCodePoints 最大码点数量
     * @param errorCode     错误码
     * @param errorMessage  错误信息模板
     * @return 不可修改的排序去重集合
     */
    public static List<String> normalizeStrings(Collection<String> values, String fieldName, int maximum, int maxCodePoints,
                                                String errorCode, String errorMessage) {
        List<String> copiedValues = requireCollection(values, fieldName, maximum, errorCode, errorMessage);
        if (copiedValues.isEmpty()) {
            throw validation(errorCode, errorMessage,
                    String.format(SimpleDataPermissionConstant.DETAIL_CANNOT_BE_EMPTY, fieldName));
        }
        LinkedHashSet<String> distinctValues = new LinkedHashSet<String>();
        for (String value : copiedValues) {
            distinctValues.add(requireText(value, fieldName, maxCodePoints, errorCode, errorMessage));
        }
        List<String> normalizedValues = new ArrayList<String>(distinctValues);
        Collections.sort(normalizedValues, UNICODE_CODE_POINT_COMPARATOR);
        return Collections.unmodifiableList(normalizedValues);
    }

    /**
     * 规范化对象集合。
     *
     * @param values       输入集合
     * @param fieldName    字段名
     * @param maximum      最大数量
     * @param comparator   排序比较器
     * @param errorCode    错误码
     * @param errorMessage 错误信息模板
     * @param <T>          集合元素类型
     * @return 不可修改的排序去重集合
     */
    public static <T> List<T> normalizeObjects(Collection<T> values, String fieldName, int maximum, Comparator<T> comparator,
                                               String errorCode, String errorMessage) {
        List<T> copiedValues = requireCollection(values, fieldName, maximum, errorCode, errorMessage);
        if (copiedValues.isEmpty()) {
            throw validation(errorCode, errorMessage,
                    String.format(SimpleDataPermissionConstant.DETAIL_CANNOT_BE_EMPTY, fieldName));
        }
        Collections.sort(copiedValues, comparator);
        List<T> normalizedValues = new ArrayList<T>(copiedValues.size());
        T previous = null;
        for (T value : copiedValues) {
            if (previous == null || !previous.equals(value)) {
                normalizedValues.add(value);
            }
            previous = value;
        }
        return Collections.unmodifiableList(normalizedValues);
    }

    /**
     * 创建校验异常。
     *
     * @param errorCode    错误码
     * @param errorMessage 错误信息模板
     * @param detail       错误详情
     * @return 校验异常
     */
    public static DataPermissionValidationException validation(String errorCode, String errorMessage, String detail) {
        return new DataPermissionValidationException(errorCode, String.format(errorMessage, detail));
    }

    private static int compareConstraints(List<DataConstraint> left, List<DataConstraint> right) {
        int count = Math.min(left.size(), right.size());
        for (int index = 0; index < count; index++) {
            DataConstraint leftConstraint = left.get(index);
            DataConstraint rightConstraint = right.get(index);
            int result = UNICODE_CODE_POINT_COMPARATOR.compare(leftConstraint.getDimension(),
                    rightConstraint.getDimension());
            if (result != 0) {
                return result;
            }
            result = UNICODE_CODE_POINT_COMPARATOR.compare(leftConstraint.getOperator().getCode(),
                    rightConstraint.getOperator().getCode());
            if (result != 0) {
                return result;
            }
            result = compareStrings(leftConstraint.getValues(), rightConstraint.getValues());
            if (result != 0) {
                return result;
            }
        }
        return Integer.compare(left.size(), right.size());
    }

    private static int compareStrings(List<String> left, List<String> right) {
        int count = Math.min(left.size(), right.size());
        for (int index = 0; index < count; index++) {
            int result = UNICODE_CODE_POINT_COMPARATOR.compare(left.get(index), right.get(index));
            if (result != 0) {
                return result;
            }
        }
        return Integer.compare(left.size(), right.size());
    }

    private static String requireText(String value, String fieldName, int maxCodePoints, String errorCode,
                                      String errorMessage) {
        if (value == null) {
            throw validation(errorCode, errorMessage,
                    String.format(SimpleDataPermissionConstant.DETAIL_CANNOT_BE_NULL, fieldName));
        }
        if (containsIsolatedSurrogate(value)) {
            throw validation(errorCode, errorMessage,
                    String.format(SimpleDataPermissionConstant.DETAIL_CANNOT_CONTAIN_ISOLATED_SURROGATE, fieldName));
        }
        if (value.isEmpty() || isBlank(value) || hasOuterWhitespace(value)) {
            throw validation(errorCode, errorMessage,
                    String.format(SimpleDataPermissionConstant.DETAIL_CANNOT_BE_BLANK_OR_OUTER_WHITESPACE, fieldName));
        }
        if (containsDynamicExpression(value)) {
            throw validation(errorCode, errorMessage, String.format(
                    SimpleDataPermissionConstant.DETAIL_CANNOT_CONTAIN_DYNAMIC_EXPRESSION, fieldName));
        }
        if (containsPatternCharacter(value)) {
            throw validation(errorCode, errorMessage,
                    String.format(SimpleDataPermissionConstant.DETAIL_CANNOT_CONTAIN_PATTERN_CHARACTER, fieldName));
        }
        int codePointCount = value.codePointCount(0, value.length());
        if (codePointCount > maxCodePoints) {
            throw validation(errorCode, errorMessage,
                    String.format(SimpleDataPermissionConstant.DETAIL_MAXIMUM_CODE_POINT_COUNT, fieldName, maxCodePoints));
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

    private static boolean isBlank(String value) {
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            if (!isWhitespace(codePoint)) {
                return false;
            }
            offset += Character.charCount(codePoint);
        }
        return true;
    }

    private static boolean hasOuterWhitespace(String value) {
        return isWhitespace(value.codePointAt(0)) || isWhitespace(value.codePointBefore(value.length()));
    }

    private static boolean isWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    private static boolean containsDynamicExpression(String value) {
        return value.contains(SimpleDataPermissionConstant.DYNAMIC_EXPRESSION_PROPERTY_PREFIX)
                || value.contains(SimpleDataPermissionConstant.DYNAMIC_EXPRESSION_SPEL_PREFIX);
    }

    private static boolean containsPatternCharacter(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (SimpleDataPermissionConstant.FORBIDDEN_PATTERN_CHARACTERS.indexOf(value.charAt(index)) >= 0) {
                return true;
            }
        }
        return false;
    }
}
