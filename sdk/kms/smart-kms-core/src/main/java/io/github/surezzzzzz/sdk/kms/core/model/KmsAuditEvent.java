package io.github.surezzzzzz.sdk.kms.core.model;

import io.github.surezzzzzz.sdk.kms.core.constant.KmsAuditOutcome;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsOperation;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 安全审计事件。
 *
 * <p>该模型只能携带审计红线允许的主体、tenant、资源、操作、结果、时间、请求标识和安全元数据。
 * 调用方必须在构建前确保 metadata 不含密码材料、明文、密文、签名、AAD、请求正文、凭据、SQL、
 * Provider 细节或异常链。</p>
 *
 * @author surezzzzzz
 */
@Getter
public final class KmsAuditEvent {

    /**
     * 发生操作的 tenant。
     */
    private final String tenantId;
    /**
     * 操作关联的逻辑密钥标识。
     */
    private final String keyRef;
    /**
     * 操作关联的密钥版本；不关联具体版本时为 {@code null}。
     */
    private final Integer keyVersion;
    /**
     * 发起操作的认证主体标识。
     */
    private final String principalId;
    /**
     * 已执行或尝试执行的 KMS 操作。
     */
    private final KmsOperation operation;
    /**
     * 操作审计结果。
     */
    private final KmsAuditOutcome outcome;
    /**
     * 与调用链关联的请求标识。
     */
    private final String requestId;
    /**
     * 审计事件发生时间。
     */
    private final Instant occurredAt;
    /**
     * 经调用方安全筛选后的不可变元数据。
     */
    private final Map<String, String> metadata;

    /**
     * 创建安全审计事件并防御性复制元数据。
     *
     * @param tenantId    操作 tenant
     * @param keyRef      逻辑密钥标识
     * @param keyVersion  密钥版本，可为 {@code null}
     * @param principalId 认证主体标识
     * @param operation   KMS 操作
     * @param outcome     审计结果
     * @param requestId   请求标识
     * @param occurredAt  发生时间
     * @param metadata    已按审计红线筛选的安全元数据，可为 {@code null}
     */
    @Builder
    public KmsAuditEvent(String tenantId, String keyRef, Integer keyVersion, String principalId,
                         KmsOperation operation, KmsAuditOutcome outcome, String requestId,
                         Instant occurredAt, Map<String, String> metadata) {
        this.tenantId = tenantId;
        this.keyRef = keyRef;
        this.keyVersion = keyVersion;
        this.principalId = principalId;
        this.operation = operation;
        this.outcome = outcome;
        this.requestId = requestId;
        this.occurredAt = occurredAt;
        this.metadata = metadata == null ? Collections.<String, String>emptyMap()
                : Collections.unmodifiableMap(new HashMap<String, String>(metadata));
    }

    /**
     * 获取不可变的安全元数据快照。
     *
     * @return 不可修改的元数据集合
     */
    public Map<String, String> getMetadata() {
        return metadata;
    }
}
