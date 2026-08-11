package io.github.surezzzzzz.sdk.ops.middleware.audit;

/**
 * 中间件运维查询审计事件发布口。
 *
 * @author surezzzzzz
 */
public interface MiddlewareOpsAuditPublisher {

    /**
     * 发布单次查询的脱敏审计事件。
     *
     * @param event 脱敏审计事件
     */
    void publish(MiddlewareOpsAuditEvent event);
}
