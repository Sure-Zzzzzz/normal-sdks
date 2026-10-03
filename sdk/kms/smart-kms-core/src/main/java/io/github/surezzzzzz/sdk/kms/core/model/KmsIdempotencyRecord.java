package io.github.surezzzzzz.sdk.kms.core.model;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 管理操作幂等记录。
 *
 * <p>记录以主体、端点和幂等键为作用域；requestHash 只能是服务端计算的无敏感摘要。</p>
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public final class KmsIdempotencyRecord {

    private final String ownerPrincipalId;
    private final String principalId;
    private final String endpoint;
    private final String idempotencyKey;
    private final String requestHash;
    private final String resourceRef;
    private final int httpStatus;
    private final Instant expiresAt;
}
