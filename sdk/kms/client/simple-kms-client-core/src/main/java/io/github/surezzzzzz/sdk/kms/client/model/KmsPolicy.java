package io.github.surezzzzzz.sdk.kms.client.model;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * KMS 逻辑密钥上的精确授权策略资源。
 *
 * <p>{@code keyVersion} 为空代表不限制目标版本，{@code expiresAt} 为空代表策略没有到期时间；
 * {@code rowVersion} 用于撤销时的乐观锁校验。</p>
 */
@Value
@Builder
public class KmsPolicy {
    String policyId;
    String keyRef;
    String principalId;
    Integer keyVersion;
    String operation;
    Instant expiresAt;
    Long rowVersion;
}
