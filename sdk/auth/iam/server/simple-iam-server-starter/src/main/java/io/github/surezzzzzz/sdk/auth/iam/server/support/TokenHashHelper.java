package io.github.surezzzzzz.sdk.auth.iam.server.support;

import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Token 哈希工具
 *
 * @author surezzzzzz
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TokenHashHelper {

    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();
    private static final String SHA_256 = "SHA-256";

    /**
     * 计算 SHA-256 十六进制摘要
     *
     * @param token token 原文
     * @return SHA-256 十六进制摘要
     */
    public static String sha256Hex(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            char[] chars = new char[hash.length * 2];
            for (int i = 0; i < hash.length; i++) {
                int value = hash[i] & 0xFF;
                chars[i * 2] = HEX_ARRAY[value >>> 4];
                chars[i * 2 + 1] = HEX_ARRAY[value & 0x0F];
            }
            return new String(chars);
        } catch (NoSuchAlgorithmException e) {
            throw new SimpleIamServerException("SHA-256 算法不可用", e);
        }
    }
}
