package io.github.surezzzzzz.sdk.license.core.support;

import io.github.surezzzzzz.sdk.license.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.license.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.license.core.constant.SmartLicenseCoreConstant;
import io.github.surezzzzzz.sdk.license.core.exception.LicenseValidationException;

import java.util.*;

/**
 * License 校验与规范化帮助类。
 *
 * @author surezzzzzz
 */
public final class LicenseValidationHelper {

    private LicenseValidationHelper() {
        throw new UnsupportedOperationException(SmartLicenseCoreConstant.MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE);
    }

    /**
     * 校验必填文本。
     *
     * @param value     文本值
     * @param fieldName 字段名
     * @param maximum   最大字符长度
     * @return 原始合法文本
     */
    public static String requireText(String value, String fieldName, int maximum) {
        if (value == null) {
            throw validation(String.format(SmartLicenseCoreConstant.DETAIL_CANNOT_BE_NULL, fieldName));
        }
        if (isBlank(value)) {
            throw validation(String.format(SmartLicenseCoreConstant.DETAIL_CANNOT_BE_BLANK, fieldName));
        }
        if (isWhitespace(value.codePointAt(SmartLicenseCoreConstant.ZERO))
                || isWhitespace(value.codePointBefore(value.length()))) {
            throw validation(String.format(SmartLicenseCoreConstant.DETAIL_INVALID_VALUE, fieldName));
        }
        if (value.codePointCount(SmartLicenseCoreConstant.ZERO, value.length()) > maximum) {
            throw validation(String.format(SmartLicenseCoreConstant.DETAIL_MAXIMUM_LENGTH, fieldName, maximum));
        }
        for (int index = SmartLicenseCoreConstant.ZERO; index < value.length(); ) {
            char character = value.charAt(index);
            if (Character.isSurrogate(character) && !isSurrogatePair(value, index)) {
                throw validation(String.format(SmartLicenseCoreConstant.DETAIL_INVALID_VALUE, fieldName));
            }
            int codePoint = value.codePointAt(index);
            if (Character.isISOControl(codePoint)) {
                throw validation(String.format(SmartLicenseCoreConstant.DETAIL_INVALID_VALUE, fieldName));
            }
            index += Character.charCount(codePoint);
        }
        return value;
    }

    private static boolean isBlank(String value) {
        for (int index = SmartLicenseCoreConstant.ZERO; index < value.length(); ) {
            int codePoint = value.codePointAt(index);
            if (!isWhitespace(codePoint)) {
                return false;
            }
            index += Character.charCount(codePoint);
        }
        return true;
    }

    private static boolean isWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    private static boolean isSurrogatePair(String value, int index) {
        return Character.isHighSurrogate(value.charAt(index)) && index + SmartLicenseCoreConstant.ONE < value.length()
                && Character.isLowSurrogate(value.charAt(index + SmartLicenseCoreConstant.ONE));
    }

    /**
     * 校验并规范化文本集合。
     *
     * @param values    文本集合
     * @param fieldName 字段名
     * @param maximum   最大元素数量
     * @param maxLength 单个元素最大字符长度
     * @return 不可修改的排序去重集合
     */
    public static List<String> normalizeTexts(Collection<String> values, String fieldName, int maximum, int maxLength) {
        if (values == null) {
            throw validation(String.format(SmartLicenseCoreConstant.DETAIL_CANNOT_BE_NULL, fieldName));
        }
        List<String> copied = new ArrayList<String>();
        for (String value : values) {
            copied.add(requireText(value, fieldName, maxLength));
            if (copied.size() > maximum) {
                throw validation(String.format(SmartLicenseCoreConstant.DETAIL_MAXIMUM_COUNT, fieldName, maximum));
            }
        }
        LinkedHashSet<String> distinct = new LinkedHashSet<String>(copied);
        List<String> normalized = new ArrayList<String>(distinct);
        Collections.sort(normalized);
        return Collections.unmodifiableList(normalized);
    }

    /**
     * 创建校验异常。
     *
     * @param detail 安全校验详情
     * @return 校验异常
     */
    public static LicenseValidationException validation(String detail) {
        return new LicenseValidationException(ErrorCode.VALIDATION_FAILED,
                String.format(ErrorMessage.VALIDATION_FAILED, detail));
    }
}
