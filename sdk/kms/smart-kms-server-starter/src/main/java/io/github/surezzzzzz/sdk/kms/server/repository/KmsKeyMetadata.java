package io.github.surezzzzzz.sdk.kms.server.repository;

import io.github.surezzzzzz.sdk.kms.core.model.KmsKey;

import java.time.Instant;

/**
 * KMS HTTP 管理查询使用的无材料密钥元数据投影。
 *
 * @author surezzzzzz
 */
public final class KmsKeyMetadata {

    private final KmsKey key;
    private final Instant createdAt;
    private final Instant updatedAt;

    /**
     * 创建无材料密钥元数据投影。
     */
    public KmsKeyMetadata(KmsKey key, Instant createdAt, Instant updatedAt) {
        this.key = key;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public KmsKey getKey() {
        return key;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
