package io.github.surezzzzzz.sdk.ops.middleware.controller.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Middleware Ops 审计分页响应。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class MiddlewareOpsAuditPageResponse {

    /**
     * 审计记录总数。
     */
    private final Long total;
    /**
     * 当前页码，从 1 开始。
     */
    private final Integer page;
    /**
     * 当前页大小。
     */
    private final Integer size;
    /**
     * 是否仍有后续页面。
     */
    private final Boolean hasMore;
    /**
     * 实际生效的开始时间。
     */
    private final String from;
    /**
     * 实际生效的结束时间。
     */
    private final String to;
    /**
     * 脱敏后的审计记录。
     */
    private final List<MiddlewareOpsAuditRecordResponse> items;
}
