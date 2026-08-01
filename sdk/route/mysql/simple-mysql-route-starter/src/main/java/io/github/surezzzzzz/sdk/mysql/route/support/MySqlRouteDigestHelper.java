package io.github.surezzzzzz.sdk.mysql.route.support;

import io.github.surezzzzzz.sdk.mysql.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.mysql.route.constant.SimpleMysqlRouteConstant;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * MySQL Route 资源摘要帮助类。
 *
 * @author surezzzzzz
 */
public final class MySqlRouteDigestHelper {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private MySqlRouteDigestHelper() {
        throw new UnsupportedOperationException("帮助类不能实例化");
    }

    public static String sha256(String value) {
        if (value == null) {
            return null;
        }
        try {
            byte[] bytes = MessageDigest.getInstance(SimpleMysqlRouteConstant.SHA_256)
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            char[] result = new char[bytes.length * 2];
            for (int index = 0; index < bytes.length; index++) {
                int current = bytes[index] & 0xff;
                result[index * 2] = HEX[current >>> 4];
                result[index * 2 + 1] = HEX[current & 0x0f];
            }
            return new String(result);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(ErrorMessage.SHA_256_UNAVAILABLE, e);
        }
    }
}
