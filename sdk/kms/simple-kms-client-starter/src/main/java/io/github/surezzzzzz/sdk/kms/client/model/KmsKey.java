package io.github.surezzzzzz.sdk.kms.client.model;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * KMS 逻辑密钥资源的元数据快照。
 *
 * <p>表示可管理的逻辑密钥而非底层私钥或对称密钥材料；{@code activeVersion} 是当前可用版本，
 * {@code rowVersion} 仅用于后续管理操作的乐观锁校验。</p>
 */
@Value
@Builder
public class KmsKey {
    String keyRef;
    String keyAlias;
    String purpose;
    String algorithm;
    String state;
    Integer activeVersion;
    Long rowVersion;
    Instant createdAt;
    Instant updatedAt;
}
