package io.github.surezzzzzz.sdk.auth.iam.core.support;

import io.github.surezzzzzz.sdk.auth.iam.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.core.constant.SimpleIamCoreConstant;
import io.github.surezzzzzz.sdk.auth.iam.core.exception.IamProtocolException;

/**
 * IAM路由键帮助类。
 *
 * @author surezzzzzz
 */
public final class IamRouteKeyHelper {

    private IamRouteKeyHelper() {
        throw new UnsupportedOperationException(SimpleIamCoreConstant.MESSAGE_HELPER_CLASS_CANNOT_INSTANTIATE);
    }

    /**
     * 创建IAM外层JOSE路由键。
     *
     * @param keyId IAM密钥标识
     * @return iam命名空间路由键
     */
    public static String createRouteKey(String keyId) {
        validateKeyId(keyId);
        return String.format(SimpleIamCoreConstant.ROUTE_KEY_TEMPLATE,
                SimpleIamCoreConstant.RESOURCE_AUTHENTICATION_SOURCE_ID,
                SimpleIamCoreConstant.ROUTE_KEY_SEPARATOR, keyId);
    }

    /**
     * 提取IAM路由键中的密钥标识。
     *
     * @param routeKey 未验证的外层JOSE路由键
     * @return IAM密钥标识；非IAM或格式非法时返回null
     */
    public static String extractKeyId(String routeKey) {
        if (routeKey == null || !routeKey.startsWith(SimpleIamCoreConstant.ROUTE_KEY_PREFIX)) {
            return null;
        }
        String keyId = routeKey.substring(SimpleIamCoreConstant.ROUTE_KEY_PREFIX.length());
        return isValidKeyId(keyId) ? keyId : null;
    }

    /**
     * 判断路由键是否属于合法IAM命名空间。
     *
     * @param routeKey 未验证的外层JOSE路由键
     * @return true表示可由IAM Provider继续验证，false表示必须拒绝
     */
    public static boolean isIamRouteKey(String routeKey) {
        return extractKeyId(routeKey) != null;
    }

    private static void validateKeyId(String keyId) {
        if (keyId == null) {
            throw invalid(String.format(SimpleIamCoreConstant.DETAIL_CANNOT_BE_NULL,
                    SimpleIamCoreConstant.FIELD_KEY_ID));
        }
        if (!isValidKeyId(keyId)) {
            throw invalid(String.format(SimpleIamCoreConstant.DETAIL_KEY_ID_INVALID, keyId));
        }
    }

    private static boolean isValidKeyId(String keyId) {
        return !keyId.isEmpty()
                && !Character.isWhitespace(keyId.charAt(0))
                && !Character.isWhitespace(keyId.charAt(keyId.length() - 1))
                && keyId.codePointCount(0, keyId.length())
                <= SimpleIamCoreConstant.MAX_KEY_ID_CODE_POINT_COUNT
                && keyId.matches(SimpleIamCoreConstant.KEY_ID_ALLOWED_CHARACTER_PATTERN);
    }

    private static IamProtocolException invalid(String detail) {
        return new IamProtocolException(ErrorCode.INVALID_ROUTE_KEY,
                String.format(ErrorMessage.INVALID_ROUTE_KEY, detail));
    }
}
