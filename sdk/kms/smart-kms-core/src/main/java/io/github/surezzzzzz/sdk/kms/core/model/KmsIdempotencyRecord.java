package io.github.surezzzzzz.sdk.kms.core.model;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 管理操作幂等记录。
 *
 * <p>记录以 tenant、主体、端点和幂等键作为作用域；{@code requestHash} 只能是服务端计算的
 * 请求摘要，禁止保存原始请求体、密码材料或其他敏感数据。</p>
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public final class KmsIdempotencyRecord {

    /**
     * 发起管理变更的 tenant。
     */
    private final String tenantId;
    /**
     * 发起管理变更的主体标识。
     */
    private final String principalId;
    /**
     * 管理操作的稳定端点标识。
     */
    private final String endpoint;
    /**
     * 客户端提供的幂等键。
     */
    private final String idempotencyKey;
    /**
     * 服务端计算的无敏感请求摘要。
     */
    private final String requestHash;
    /**
     * 成功操作对应的资源标识，不保存响应正文。
     */
    private final String resourceRef;
    /**
     * 重放时返回的 HTTP 状态。
     */
    private final int httpStatus;
    /**
     * 幂等记录失效时间。
     */
    private final Instant expiresAt;
}
