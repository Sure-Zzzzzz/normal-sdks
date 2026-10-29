package io.github.surezzzzzz.sdk.crm.server.entity;

import lombok.Getter;

import java.time.Instant;

/**
 * 命令幂等记录 JDBC 映射实体（终身保留，重放依据）。
 *
 * @author surezzzzzz
 */
@Getter
public final class SimpleCrmServerIdempotencyEntity {

    private final String scopeKey;
    private final String requestDigest;
    private final String firstResourceId;
    private final String firstResultJson;
    private final Instant createdAt;

    /**
     * 创建SimpleCrmServerIdempotencyEntity。
     *
     * @param scopeKey        稳定范围键（owner|command|resourceType[|targetId]|idempotencyKey）
     * @param requestDigest   规范化请求摘要 SHA-256
     * @param firstResourceId 创建命令首成功顶级资源 ID（可空）
     * @param firstResultJson 已知目标命令首成功声明结果 JSON（可空）
     * @param createdAt       创建时间
     */
    public SimpleCrmServerIdempotencyEntity(String scopeKey, String requestDigest, String firstResourceId,
                             String firstResultJson, Instant createdAt) {
        this.scopeKey = scopeKey;
        this.requestDigest = requestDigest;
        this.firstResourceId = firstResourceId;
        this.firstResultJson = firstResultJson;
        this.createdAt = createdAt;
    }
}
