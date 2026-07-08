package io.github.surezzzzzz.sdk.elasticsearch.persistence.support;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.constant.SimpleElasticsearchPersistenceConstant;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.exception.PersistenceExecutionException;

import java.security.MessageDigest;
import java.util.UUID;

/**
 * Document Id Helper
 *
 * @author surezzzzzz
 */
public final class DocumentIdHelper {

    private DocumentIdHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 生成无横线 UUID。
     *
     * @return 无横线 UUID
     */
    public static String uuid() {
        return UUID.randomUUID().toString().replace(SimpleElasticsearchPersistenceConstant.UUID_HYPHEN,
                SimpleElasticsearchPersistenceConstant.EMPTY_STRING);
    }

    /**
     * 计算 SHA-1。
     *
     * @param value 原始值
     * @return SHA-1 hex
     */
    public static String sha1(String value) {
        return hash(SimpleElasticsearchPersistenceConstant.HASH_ALGORITHM_SHA1, value);
    }

    /**
     * 拼接多字段后计算 SHA-1。
     *
     * @param values 字段值
     * @return SHA-1 hex
     */
    public static String sha1(Object... values) {
        return sha1(join(values));
    }

    /**
     * 计算 SHA-256。
     *
     * @param value 原始值
     * @return SHA-256 hex
     */
    public static String sha256(String value) {
        return hash(SimpleElasticsearchPersistenceConstant.HASH_ALGORITHM_SHA256, value);
    }

    /**
     * 拼接多字段后计算 SHA-256。
     *
     * @param values 字段值
     * @return SHA-256 hex
     */
    public static String sha256(Object... values) {
        return sha256(join(values));
    }

    /**
     * 使用固定分隔符拼接字段。
     *
     * @param values 字段值
     * @return 拼接后的字符串
     */
    public static String join(Object... values) {
        if (values == null || values.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                builder.append(SimpleElasticsearchPersistenceConstant.ID_JOIN_DELIMITER);
            }
            if (values[i] != null) {
                builder.append(values[i]);
            }
        }
        return builder.toString();
    }

    private static String hash(String algorithm, String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] bytes = value == null ? new byte[0]
                    : value.getBytes(SimpleElasticsearchPersistenceConstant.UTF_8);
            return toHex(digest.digest(bytes));
        } catch (Exception e) {
            throw new PersistenceExecutionException(ErrorCode.EXECUTION_FAILED,
                    String.format(ErrorMessage.EXECUTION_FAILED, algorithm), e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            String hex = Integer.toHexString(b & 0xff);
            if (hex.length() == 1) {
                builder.append('0');
            }
            builder.append(hex);
        }
        return builder.toString();
    }
}
