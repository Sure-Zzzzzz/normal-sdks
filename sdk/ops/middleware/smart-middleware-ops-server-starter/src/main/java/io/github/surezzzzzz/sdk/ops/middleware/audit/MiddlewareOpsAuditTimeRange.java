package io.github.surezzzzzz.sdk.ops.middleware.audit;

import lombok.Builder;
import lombok.Getter;

/**
 * Middleware Ops 审计查询的有效时间范围。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class MiddlewareOpsAuditTimeRange {

    /**
     * 开始时间。
     */
    private final String from;
    /**
     * 结束时间。
     */
    private final String to;
}
