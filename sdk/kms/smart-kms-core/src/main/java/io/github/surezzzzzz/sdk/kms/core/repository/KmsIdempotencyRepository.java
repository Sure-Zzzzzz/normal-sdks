package io.github.surezzzzzz.sdk.kms.core.repository;

import io.github.surezzzzzz.sdk.kms.core.model.KmsIdempotencyRecord;

import java.util.Optional;

/**
 * 幂等记录仓储端口。
 *
 * <p>查询作用域固定为 tenant、主体、端点和幂等键；适配层必须保证该组合的唯一性。</p>
 *
 * @author surezzzzzz
 */
public interface KmsIdempotencyRepository {

    /**
     * 按完整幂等作用域查询已有记录。
     *
     * @param tenantId       发起操作的 tenant
     * @param principalId    发起操作的主体标识
     * @param endpoint       管理操作稳定端点标识
     * @param idempotencyKey 客户端提供的幂等键
     * @return 匹配的记录；不存在时为空
     */
    Optional<KmsIdempotencyRecord> find(String tenantId, String principalId, String endpoint,
                                        String idempotencyKey);

    /**
     * 保存幂等记录。
     *
     * @param record 仅包含无敏感请求摘要的幂等记录
     * @return 已持久化的记录快照
     */
    KmsIdempotencyRecord save(KmsIdempotencyRecord record);
}
