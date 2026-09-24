package io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 可信应用异步删除操作响应。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class TrustedApplicationCleanupOperationResponse {

    /**
     * 操作主键。
     */
    private Long operationId;

    /**
     * 应用主键审计快照。
     */
    private Long applicationId;

    /**
     * 操作类型。
     */
    private String action;

    /**
     * 当前状态。
     */
    private String state;

    /**
     * 当前是否允许管理端显式重试。
     */
    private boolean retryable;

    /**
     * 已处理授权数量。
     */
    private Long processedAuthorizationCount;

    /**
     * 已尝试次数。
     */
    private Long attemptCount;

    /**
     * 最近失败分类；未失败时为 null。
     */
    private String failureCategory;

    /**
     * 受理时间。
     */
    private Instant acceptedAt;

    /**
     * 最后状态更新时间。
     */
    private Instant updatedAt;
}
