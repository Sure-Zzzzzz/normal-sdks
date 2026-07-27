package io.github.surezzzzzz.sdk.kms.server.repository;

import java.time.Instant;
import java.util.Optional;

/**
 * KMS 管理响应快照内部仓储端口。
 *
 * @author surezzzzzz
 */
public interface KmsIdempotencyResponseSnapshotRepository {

    /**
     * 查询完整幂等作用域的响应快照。
     *
     * @param tenantId       发起操作的租户标识
     * @param principalId    发起操作的认证主体标识
     * @param endpoint       规范化具体端点路径
     * @param idempotencyKey 客户端幂等键
     * @return 已保存的无敏感响应快照；不存在时为空
     */
    Optional<byte[]> findResponseSnapshot(String tenantId, String principalId, String endpoint, String idempotencyKey);

    /**
     * 在管理操作事务内保存无敏感响应快照。
     *
     * @param record           幂等记录
     * @param responseSnapshot 模块私有 JSON 响应快照；空响应使用空字节数组
     */
    void saveResponseSnapshot(io.github.surezzzzzz.sdk.kms.core.model.KmsIdempotencyRecord record,
                              byte[] responseSnapshot);

    /**
     * 删除当前完整作用域内已过期的幂等记录。
     *
     * @param tenantId       发起操作的租户标识
     * @param principalId    发起操作的认证主体标识
     * @param endpoint       规范化具体端点路径
     * @param idempotencyKey 客户端幂等键
     * @param now            数据库权威当前时间
     */
    void deleteExpired(String tenantId, String principalId, String endpoint, String idempotencyKey, Instant now);
}
