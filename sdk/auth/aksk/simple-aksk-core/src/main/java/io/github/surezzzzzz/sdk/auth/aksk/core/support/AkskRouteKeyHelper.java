package io.github.surezzzzzz.sdk.auth.aksk.core.support;

import io.github.surezzzzzz.sdk.auth.aksk.core.constant.AkskConstant;
import io.github.surezzzzzz.sdk.auth.aksk.core.exception.AkskException;

/**
 * AKSK外层JOSE路由键帮助类。
 *
 * @author surezzzzzz
 */
public final class AkskRouteKeyHelper {

    private AkskRouteKeyHelper() {
        throw new UnsupportedOperationException("帮助类不能实例化");
    }

    /**
     * 创建AKSK外层JOSE路由键。
     *
     * @param keyId AKSK密钥标识
     * @return aksk命名空间路由键
     */
    public static String createRouteKey(String keyId) {
        if (!isValidKeyId(keyId)) {
            throw new AkskException("AKSK路由密钥标识无效");
        }
        return AkskConstant.ROUTE_KEY_PREFIX + keyId;
    }

    /**
     * 提取AKSK路由键中的密钥标识。
     *
     * @param routeKey 未验证的外层JOSE路由键
     * @return 密钥标识；非AKSK或格式非法时返回null
     */
    public static String extractKeyId(String routeKey) {
        if (routeKey == null || !routeKey.startsWith(AkskConstant.ROUTE_KEY_PREFIX)) {
            return null;
        }
        String keyId = routeKey.substring(AkskConstant.ROUTE_KEY_PREFIX.length());
        return isValidKeyId(keyId) ? keyId : null;
    }

    /**
     * 判断路由键是否属于合法AKSK命名空间。
     *
     * @param routeKey 未验证的外层JOSE路由键
     * @return true表示可由AKSK Provider继续验证
     */
    public static boolean isAkskRouteKey(String routeKey) {
        return extractKeyId(routeKey) != null;
    }

    private static boolean isValidKeyId(String keyId) {
        return keyId != null
                && !keyId.isEmpty()
                && !Character.isWhitespace(keyId.charAt(0))
                && !Character.isWhitespace(keyId.charAt(keyId.length() - 1))
                && keyId.codePointCount(0, keyId.length()) <= AkskConstant.MAX_ROUTE_KEY_ID_CODE_POINT_COUNT
                && keyId.matches(AkskConstant.ROUTE_KEY_ID_ALLOWED_CHARACTER_PATTERN);
    }
}
