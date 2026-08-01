package io.github.surezzzzzz.sdk.mysql.route.audit;

/**
 * MySQL Route 审计事件发布 SPI。
 *
 * @author surezzzzzz
 */
public interface MySqlRouteAuditPublisher {

    /**
     * 发布脱敏审计事件。
     *
     * @param event 审计事件
     */
    void publish(MySqlRouteAuditEvent event);
}
