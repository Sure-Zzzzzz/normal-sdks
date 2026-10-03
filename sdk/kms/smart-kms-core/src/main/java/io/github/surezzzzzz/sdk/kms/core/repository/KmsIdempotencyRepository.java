package io.github.surezzzzzz.sdk.kms.core.repository;

import io.github.surezzzzzz.sdk.kms.core.model.KmsIdempotencyRecord;

import java.util.Optional;

/**
 * 幂等记录仓储端口。作用域固定为主体、端点和幂等键。
 *
 * @author surezzzzzz
 */
public interface KmsIdempotencyRepository {

    Optional<KmsIdempotencyRecord> find(String ownerPrincipalId, String principalId, String endpoint,
                                        String idempotencyKey);

    KmsIdempotencyRecord save(KmsIdempotencyRecord record);
}
