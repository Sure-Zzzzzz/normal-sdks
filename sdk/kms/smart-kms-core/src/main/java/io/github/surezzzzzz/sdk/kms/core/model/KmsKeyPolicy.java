package io.github.surezzzzzz.sdk.kms.core.model;

import io.github.surezzzzzz.sdk.kms.core.constant.KmsOperation;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 精确 allow-only 密钥策略。
 *
 * <p>策略只允许精确匹配 tenant、主体、逻辑密钥和操作；不支持通配符。
 * {@code keyVersion} 为 {@code null} 时表示匹配该逻辑密钥的任意版本。</p>
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public final class KmsKeyPolicy {

    /**
     * 策略稳定标识。
     */
    private final String policyId;
    /**
     * 策略所属 tenant。
     */
    private final String tenantId;
    /**
     * 被授权的逻辑密钥标识。
     */
    private final String keyRef;
    /**
     * 被精确授权的主体标识。
     */
    private final String principalId;
    /**
     * 被授权的精确版本；为 {@code null} 时不限制版本。
     */
    private final Integer keyVersion;
    /**
     * 被允许执行的单一 KMS 操作。
     */
    private final KmsOperation operation;
    /**
     * 授权到期时间；为 {@code null} 时不设置到期限制。
     */
    private final Instant expiresAt;
    /**
     * 持久化层乐观锁版本。
     */
    private final long rowVersion;
}
