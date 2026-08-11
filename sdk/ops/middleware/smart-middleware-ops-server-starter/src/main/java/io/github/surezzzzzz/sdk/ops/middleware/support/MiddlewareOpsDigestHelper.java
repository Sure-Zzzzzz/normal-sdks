package io.github.surezzzzzz.sdk.ops.middleware.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 运维敏感资源的不可逆摘要帮助器。
 *
 * @author surezzzzzz
 */
public final class MiddlewareOpsDigestHelper {

    private static final String ALGORITHM = "SHA-256";
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private MiddlewareOpsDigestHelper() {
        throw new UnsupportedOperationException("帮助类不能实例化");
    }

    /**
     * 计算 UTF-8 文本的 SHA-256 摘要。
     *
     * @param value 原始文本
     * @return 小写十六进制摘要
     */
    public static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance(ALGORITHM)
                    .digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            char[] result = new char[bytes.length * 2];
            for (int index = 0; index < bytes.length; index++) {
                int current = bytes[index] & 0xff;
                result[index * 2] = HEX[current >>> 4];
                result[index * 2 + 1] = HEX[current & 0x0f];
            }
            return new String(result);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }
}
