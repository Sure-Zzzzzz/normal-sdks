package io.github.surezzzzzz.sdk.auth.aksk.core.support;

import io.github.surezzzzzz.sdk.auth.aksk.core.constant.AkskConstant;
import io.github.surezzzzzz.sdk.auth.aksk.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.aksk.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.auth.aksk.core.exception.AkskException;

import java.security.SecureRandom;

/**
 * Base62 编码工具类
 * <p>
 * 用于生成 AKSK 的 ClientId 和 ClientSecret
 *
 * @author Sure
 * @since 1.0.0
 */
public final class Base62Helper {

    private Base62Helper() {
        throw new AkskException(ErrorMessage.UTILITY_CLASS_INSTANTIATION);
    }

    /**
     * Base62 字符集（0-9, A-Z, a-z）
     */
    private static final String BASE62_CHARS =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    /**
     * 安全随机数生成器
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 生成指定长度的随机 Base62 字符串
     *
     * @param length 长度
     * @return 随机字符串
     * @throws AkskException 如果长度小于等于 0
     */
    public static String generateRandom(int length) {
        if (length <= 0) {
            throw new AkskException(ErrorCode.INVALID_LENGTH, ErrorMessage.INVALID_LENGTH);
        }

        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = RANDOM.nextInt(BASE62_CHARS.length());
            sb.append(BASE62_CHARS.charAt(index));
        }
        return sb.toString();
    }

    /**
     * 生成平台级 ClientId
     * <p>
     * 格式: AKP + 20位Base62随机字符
     * <p>
     * 示例: AKPx7k9m2n4p6q8r1s3t5u7v9
     *
     * @return 平台级 ClientId
     */
    public static String generatePlatformClientId() {
        return AkskConstant.PLATFORM_CLIENT_ID_PREFIX +
                generateRandom(AkskConstant.CLIENT_ID_RANDOM_LENGTH);
    }

    /**
     * 生成用户级 ClientId
     * <p>
     * 格式: AKU + 20位Base62随机字符
     * <p>
     * 示例: AKUa5b7c9d1e3f5g7h9i1j3k5
     *
     * @return 用户级 ClientId
     */
    public static String generateUserClientId() {
        return AkskConstant.USER_CLIENT_ID_PREFIX +
                generateRandom(AkskConstant.CLIENT_ID_RANDOM_LENGTH);
    }

    /**
     * 生成 ClientSecret
     * <p>
     * 格式: SK + 40位Base62随机字符
     * <p>
     * 示例: SKx7k9m2n4p6q8r1s3t5u7v9w1x3y5z7a9b1c3d5e7
     *
     * @return ClientSecret
     */
    public static String generateClientSecret() {
        return AkskConstant.CLIENT_SECRET_PREFIX +
                generateRandom(AkskConstant.CLIENT_SECRET_RANDOM_LENGTH);
    }

    /**
     * 验证是否为合法的 ClientId
     *
     * @param clientId ClientId
     * @return true 如果合法
     */
    public static boolean isValidClientId(String clientId) {
        if (clientId == null || clientId.length() != AkskConstant.CLIENT_ID_TOTAL_LENGTH) {
            return false;
        }

        String prefix = clientId.substring(0, 3);
        boolean validPrefix = AkskConstant.PLATFORM_CLIENT_ID_PREFIX.equals(prefix)
                || AkskConstant.USER_CLIENT_ID_PREFIX.equals(prefix);

        return validPrefix && isBase62(clientId.substring(3));
    }

    /**
     * 验证是否为合法的 ClientSecret
     *
     * @param secret ClientSecret
     * @return true 如果合法
     */
    public static boolean isValidClientSecret(String secret) {
        if (secret == null || secret.length() != AkskConstant.CLIENT_SECRET_TOTAL_LENGTH) {
            return false;
        }

        return AkskConstant.CLIENT_SECRET_PREFIX.equals(secret.substring(0, 2))
                && isBase62(secret.substring(2));
    }

    /**
     * 验证字符串是否为 Base62 格式
     *
     * @param str 待验证字符串
     * @return true 如果是 Base62 格式
     */
    private static boolean isBase62(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }

        for (char c : str.toCharArray()) {
            if (BASE62_CHARS.indexOf(c) == -1) {
                return false;
            }
        }
        return true;
    }
}
