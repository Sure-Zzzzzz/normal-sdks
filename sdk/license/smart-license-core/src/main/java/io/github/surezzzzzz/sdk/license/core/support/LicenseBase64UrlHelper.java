package io.github.surezzzzzz.sdk.license.core.support;

import io.github.surezzzzzz.sdk.license.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.license.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.license.core.constant.SmartLicenseCoreConstant;
import io.github.surezzzzzz.sdk.license.core.exception.LicenseValidationException;

import java.util.Base64;

/**
 * 无 padding Base64URL 帮助类。
 *
 * @author surezzzzzz
 */
public final class LicenseBase64UrlHelper {

    private LicenseBase64UrlHelper() {
        throw new UnsupportedOperationException(SmartLicenseCoreConstant.MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE);
    }

    /**
     * 将字节编码为无 padding Base64URL 文本。
     *
     * @param value 原始字节
     * @return 无 padding Base64URL 文本
     */
    public static String encode(byte[] value) {
        if (value == null) {
            throw LicenseValidationHelper.validation(SmartLicenseCoreConstant.FIELD_COMPACT_JWS);
        }
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(value);
        if (!isUnpadded(encoded)) {
            throw new LicenseValidationException(ErrorCode.VALIDATION_FAILED,
                    String.format(ErrorMessage.VALIDATION_FAILED, SmartLicenseCoreConstant.FIELD_COMPACT_JWS));
        }
        return encoded;
    }

    /**
     * 判断文本是否为无 padding Base64URL。
     *
     * @param value 待校验文本
     * @return 是否为无 padding Base64URL
     */
    public static boolean isUnpadded(String value) {
        if (value == null || value.isEmpty() || value.indexOf(SmartLicenseCoreConstant.BASE64URL_PADDING)
                >= SmartLicenseCoreConstant.ZERO
                || value.length() % SmartLicenseCoreConstant.BASE64URL_QUANTUM_LENGTH
                == SmartLicenseCoreConstant.ONE) {
            return false;
        }
        for (int index = SmartLicenseCoreConstant.ZERO; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!isBase64UrlCharacter(character)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isBase64UrlCharacter(char value) {
        return value >= SmartLicenseCoreConstant.BASE64URL_UPPERCASE_BEGIN
                && value <= SmartLicenseCoreConstant.BASE64URL_UPPERCASE_END
                || value >= SmartLicenseCoreConstant.BASE64URL_LOWERCASE_BEGIN
                && value <= SmartLicenseCoreConstant.BASE64URL_LOWERCASE_END
                || value >= SmartLicenseCoreConstant.BASE64URL_DIGIT_BEGIN
                && value <= SmartLicenseCoreConstant.BASE64URL_DIGIT_END
                || value == SmartLicenseCoreConstant.BASE64URL_MINUS
                || value == SmartLicenseCoreConstant.BASE64URL_UNDERSCORE;
    }
}
