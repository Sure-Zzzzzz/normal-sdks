package io.github.surezzzzzz.sdk.elasticsearch.persistence.support;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.exception.PersistenceExecutionException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

import static io.github.surezzzzzz.sdk.elasticsearch.persistence.constant.SimpleElasticsearchPersistenceConstant.*;

/**
 * 文档 ID 辅助工具，保留旧版 UTF-8 哈希和字段拼接口径。
 * 哈希用于文档指纹，不用于密码存储、签名或消息认证。
 *
 * @author surezzzzzz
 */
public final class DocumentIdHelper {

    private DocumentIdHelper() {
    }

    /**
     * 生成无横线的随机 UUID；需要重复写入同一文档时不要每次重新生成。
     *
     * @return 无横线 UUID
     */
    public static String uuid() {
        return UUID.randomUUID().toString().replace(UUID_HYPHEN, EMPTY_STRING);
    }

    /**
     * 计算 UTF-8 字符串的 SHA-1 小写十六进制指纹；null 按空字符串处理。
     * 此方法用于兼容既有 ID，新接入优先选择 SHA-256。
     *
     * @param value 原始值
     * @return SHA-1 指纹
     */
    public static String sha1(String value) {
        return hash(HASH_ALGORITHM_SHA1, value);
    }

    /**
     * 按 join 的固定规则拼接字段后计算 SHA-1，不对字段进行标准化。
     *
     * @param values 按固定顺序排列的字段值
     * @return SHA-1 指纹
     */
    public static String sha1(Object... values) {
        return sha1(join(values));
    }

    /**
     * 计算 UTF-8 字符串的 SHA-256 小写十六进制指纹；null 按空字符串处理。
     *
     * @param value 原始值
     * @return SHA-256 指纹
     */
    public static String sha256(String value) {
        return hash(HASH_ALGORITHM_SHA256, value);
    }

    /**
     * 按 join 的固定规则拼接字段后计算 SHA-256，不对字段进行标准化。
     *
     * @param values 按固定顺序排列的字段值
     * @return SHA-256 指纹
     */
    public static String sha256(Object... values) {
        return sha256(join(values));
    }

    /**
     * 使用竖线拼接字段，null 字段保留空位置，null 数组或零字段返回空字符串。
     * 不转义竖线，也不区分 null 与空字符串；需要无歧义复合键时，调用方先规范编码。
     *
     * @param values 按固定顺序排列的字段值
     * @return 拼接后的字符串
     */
    public static String join(Object... values) {
        if (values == null || values.length == 0) return EMPTY_STRING;
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) result.append(ID_JOIN_DELIMITER);
            if (values[i] != null) result.append(values[i]);
        }
        return result.toString();
    }

    private static String hash(String algorithm, String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] bytes = (value == null ? EMPTY_STRING : value).getBytes(StandardCharsets.UTF_8);
            byte[] hash = digest.digest(bytes);
            StringBuilder result = new StringBuilder(hash.length * HEX_CHAR_PER_BYTE);
            for (byte valueByte : hash) {
                String hex = Integer.toHexString(valueByte & HASH_BYTE_MASK);
                if (hex.length() < HEX_CHAR_PER_BYTE) result.append(HEX_PADDING);
                result.append(hex);
            }
            return result.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new PersistenceExecutionException(ErrorCode.EXECUTION_FAILED, ErrorMessage.EXECUTION_FAILED, error);
        }
    }
}
