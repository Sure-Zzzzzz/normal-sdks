package io.github.surezzzzzz.sdk.limiter.redis.smart.support;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterDataDimension;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.typed.SmartRedisLimiterTypedPolicyKey;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SmartRedisLimiter 类型化计数桶身份 Helper
 * <p>桶身份是 {@code serviceCode + resourceCode + dimension + namespace + customType + 实际对象}
 * 的版本化无歧义编码摘要：固定域前缀后按固定顺序写各字段，每字段先写大端两字节长度再写原始
 * Java 字符单元（每个字符同样两字节大端）。不依赖分隔符猜测与平台默认字符集，
 * 未配对代理字符按原始字符单元保留，不同输入不会因 UTF-8 替换而合并。</p>
 * <p>摘要只提供稳定检索标识；ruleId、selector、revision、Token 与限额值不进入桶身份，
 * 修改额度或切换默认/覆盖不重置计数。同桶多窗口使用相同 Redis Cluster Hash Tag 由运行端组装。</p>
 *
 * @author surezzzzzz
 */
public final class SmartRedisLimiterBucketIdentityHelper {

    /**
     * 空字段在长度前缀编码中的哨兵值，与合法空串区分
     */
    private static final int NULL_FIELD_SENTINEL = 0xFFFF;

    private SmartRedisLimiterBucketIdentityHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 计算类型化规则计数桶身份摘要
     * <p>RESOURCE 维度的实际对象为固定共享标识，保证同资源总量共桶。</p>
     *
     * @param key 类型化规则键
     * @return 64 位小写十六进制 SHA-256 摘要
     * @throws SmartRedisLimiterException 规则键为空时抛出
     */
    public static String digest(SmartRedisLimiterTypedPolicyKey key) {
        if (key == null) {
            throw new SmartRedisLimiterException(
                    ErrorCode.TYPED_POLICY_KEY_INVALID,
                    String.format(ErrorMessage.TYPED_POLICY_KEY_INVALID,
                            SmartRedisLimiterConstant.POLICY_FIELD_DIMENSION,
                            ErrorMessage.REASON_TYPED_DIMENSION_INVALID));
        }
        String objectId = key.getDimension() == SmartRedisLimiterDataDimension.RESOURCE
                ? SmartRedisLimiterConstant.TYPED_RESOURCE_SHARED_OBJECT
                : key.getObjectId();
        ByteArrayOutputStream input = new ByteArrayOutputStream();
        writeDomain(input, SmartRedisLimiterConstant.TYPED_REDIS_BUSINESS_TYPE);
        writeField(input, key.getServiceCode());
        writeField(input, key.getResourceCode());
        writeField(input, key.getDimension().getCode());
        writeField(input, key.getNamespace());
        writeField(input, key.getCustomType());
        writeField(input, objectId);
        return sha256Hex(input.toByteArray());
    }

    /**
     * 计算操作人身份事实短摘要
     * <p>输入为 {@code sourceId + subjectType + subjectId} 三元组的固定编码，
     * 完整形态为 {@code resource:v1:sha256:<64位小写十六进制>}；摘要仅作稳定标识，
     * 权限与 DATA 判定不依据摘要，完整三元组由事件 attributes 的 operatorIdentity 携带。</p>
     *
     * @param sourceId    来源标识
     * @param subjectType 主体类型
     * @param subjectId   主体标识
     * @return 带前缀的 83 字符操作人短摘要
     * @throws SmartRedisLimiterException 任一字段为空时抛出
     */
    public static String operatorDigest(String sourceId, String subjectType, String subjectId) {
        if (sourceId == null || sourceId.trim().isEmpty()
                || subjectType == null || subjectType.trim().isEmpty()
                || subjectId == null || subjectId.trim().isEmpty()) {
            throw new SmartRedisLimiterException(
                    ErrorCode.MANAGEMENT_PAYLOAD_INVALID,
                    String.format(ErrorMessage.MANAGEMENT_PAYLOAD_INVALID,
                            ErrorMessage.REASON_MANAGEMENT_PAYLOAD_REQUIRED));
        }
        ByteArrayOutputStream input = new ByteArrayOutputStream();
        writeDomain(input, SmartRedisLimiterConstant.OPERATOR_IDENTITY_SCHEMA);
        writeField(input, sourceId);
        writeField(input, subjectType);
        writeField(input, subjectId);
        return SmartRedisLimiterConstant.OPERATOR_DIGEST_PREFIX + sha256Hex(input.toByteArray());
    }

    private static void writeDomain(ByteArrayOutputStream out, String domain) {
        byte[] domainBytes = domain.getBytes(StandardCharsets.US_ASCII);
        out.write((domainBytes.length >>> 8) & 0xFF);
        out.write(domainBytes.length & 0xFF);
        out.write(domainBytes, 0, domainBytes.length);
    }

    private static void writeField(ByteArrayOutputStream out, String value) {
        if (value == null) {
            out.write((NULL_FIELD_SENTINEL >>> 8) & 0xFF);
            out.write(NULL_FIELD_SENTINEL & 0xFF);
            return;
        }
        int length = value.length();
        out.write((length >>> 8) & 0xFF);
        out.write(length & 0xFF);
        for (int i = 0; i < length; i++) {
            char unit = value.charAt(i);
            out.write((unit >>> 8) & 0xFF);
            out.write(unit & 0xFF);
        }
    }

    private static String sha256Hex(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(input);
            StringBuilder hex = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                String value = Integer.toHexString(b & 0xFF);
                if (value.length() < 2) {
                    hex.append('0');
                }
                hex.append(value);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 摘要算法不可用", ex);
        }
    }
}
