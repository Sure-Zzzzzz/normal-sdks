package io.github.surezzzzzz.sdk.license.core.support;

import io.github.surezzzzzz.sdk.license.core.constant.SmartLicenseCoreConstant;

import java.nio.charset.StandardCharsets;

/**
 * License Compact JWS 组装帮助类。
 *
 * @author surezzzzzz
 */
public final class LicenseJwsHelper {

    private LicenseJwsHelper() {
        throw new UnsupportedOperationException(SmartLicenseCoreConstant.MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE);
    }

    /**
     * 构造固定 v1 protected header JSON。
     *
     * @param kid 业务密钥标识
     * @return 固定字段顺序的 header JSON
     */
    public static String header(String kid) {
        String escapedKid = escapeJson(LicenseValidationHelper.requireText(kid, SmartLicenseCoreConstant.FIELD_KEY_ID,
                SmartLicenseCoreConstant.MAX_IDENTIFIER_LENGTH));
        return String.format(SmartLicenseCoreConstant.JWS_HEADER_TEMPLATE, escapedKid);
    }

    /**
     * 构造原始 ASCII 签名输入。
     *
     * @param headerSegment  Base64URL header 段
     * @param payloadSegment Base64URL payload 段
     * @return 原始 ASCII 签名输入
     */
    public static byte[] signingInput(String headerSegment, String payloadSegment) {
        return (requireSegment(headerSegment) + SmartLicenseCoreConstant.JWS_SEPARATOR + requireSegment(payloadSegment))
                .getBytes(StandardCharsets.US_ASCII);
    }

    /**
     * 组装 Compact JWS。
     *
     * @param headerSegment  Base64URL header 段
     * @param payloadSegment Base64URL payload 段
     * @param signature      JOSE 签名
     * @return Compact JWS
     */
    public static String compact(String headerSegment, String payloadSegment, byte[] signature) {
        if (signature == null || signature.length != SmartLicenseCoreConstant.ES256_JOSE_SIGNATURE_LENGTH) {
            throw LicenseValidationHelper.validation(SmartLicenseCoreConstant.FIELD_COMPACT_JWS);
        }
        return requireSegment(headerSegment) + SmartLicenseCoreConstant.JWS_SEPARATOR + requireSegment(payloadSegment)
                + SmartLicenseCoreConstant.JWS_SEPARATOR + LicenseBase64UrlHelper.encode(signature);
    }

    private static String requireSegment(String value) {
        if (!LicenseBase64UrlHelper.isUnpadded(value)) {
            throw LicenseValidationHelper.validation(SmartLicenseCoreConstant.FIELD_COMPACT_JWS);
        }
        return value;
    }

    private static String escapeJson(String value) {
        StringBuilder builder = new StringBuilder();
        for (int index = SmartLicenseCoreConstant.ZERO; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == SmartLicenseCoreConstant.JSON_ESCAPE
                    || character == SmartLicenseCoreConstant.JSON_QUOTE) {
                builder.append(SmartLicenseCoreConstant.JSON_ESCAPE);
            }
            builder.append(character);
        }
        return builder.toString();
    }
}
