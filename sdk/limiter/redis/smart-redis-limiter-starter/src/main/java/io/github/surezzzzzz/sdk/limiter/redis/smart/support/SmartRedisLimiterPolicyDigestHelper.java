package io.github.surezzzzzz.sdk.limiter.redis.smart.support;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterStarterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.starter.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.starter.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterLimit;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicy;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicySnapshot;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 远程策略 canonical 摘要 Helper
 *
 * @author surezzzzzz
 */
public final class SmartRedisLimiterPolicyDigestHelper {

    private SmartRedisLimiterPolicyDigestHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 计算已校验快照的 canonical SHA-256 摘要
     *
     * @param snapshot 已校验协议快照
     * @return 小写十六进制 SHA-256 摘要
     */
    public static String sha256(SmartRedisLimiterPolicySnapshot snapshot) {
        try {
            MessageDigest digest = MessageDigest.getInstance(
                    SmartRedisLimiterStarterConstant.DIGEST_ALGORITHM_SHA_256);
            return toHex(digest.digest(canonicalBytes(snapshot)));
        } catch (NoSuchAlgorithmException | IOException ex) {
            throw new SmartRedisLimiterException(
                    ErrorCode.POLICY_DIGEST_FAILED,
                    ErrorMessage.POLICY_DIGEST_FAILED,
                    ex);
        }
    }

    private static byte[] canonicalBytes(SmartRedisLimiterPolicySnapshot snapshot) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeField(output, snapshot.getSchemaVersion());
        writeField(output, snapshot.getServiceCode());
        writeField(output, String.valueOf(snapshot.getRevision()));
        writeField(output, snapshot.getPublishedAt().toString());

        List<SmartRedisLimiterPolicy> policies = new ArrayList<>(snapshot.getPolicies());
        policies.sort(Comparator
                .comparing((SmartRedisLimiterPolicy policy) -> policy.getKey().getServiceCode())
                .thenComparing(policy -> policy.getKey().getResourceCode())
                .thenComparing(policy -> policy.getKey().getSubject()));
        writeField(output, String.valueOf(policies.size()));
        for (SmartRedisLimiterPolicy policy : policies) {
            writeField(output, policy.getKey().getServiceCode());
            writeField(output, policy.getKey().getResourceCode());
            writeField(output, policy.getKey().getSubject());
            List<SmartRedisLimiterLimit> limits = new ArrayList<>(policy.getLimits());
            limits.sort(Comparator
                    .comparingLong(SmartRedisLimiterLimit::getCount)
                    .thenComparingLong(SmartRedisLimiterLimit::getWindowSeconds));
            writeField(output, String.valueOf(limits.size()));
            for (SmartRedisLimiterLimit limit : limits) {
                writeField(output, String.valueOf(limit.getCount()));
                writeField(output, String.valueOf(limit.getWindowSeconds()));
            }
        }
        return output.toByteArray();
    }

    private static void writeField(ByteArrayOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.write(String.valueOf(bytes.length).getBytes(StandardCharsets.US_ASCII));
        output.write(SmartRedisLimiterStarterConstant.CANONICAL_LENGTH_SEPARATOR);
        output.write(bytes);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format(SmartRedisLimiterStarterConstant.FORMAT_HEX_BYTE, value & 0xff));
        }
        return builder.toString();
    }
}
