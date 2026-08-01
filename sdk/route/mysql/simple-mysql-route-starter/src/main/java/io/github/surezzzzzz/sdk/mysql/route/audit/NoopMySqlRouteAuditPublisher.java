package io.github.surezzzzzz.sdk.mysql.route.audit;

/**
 * 默认空审计发布器。
 *
 * @author surezzzzzz
 */
public final class NoopMySqlRouteAuditPublisher implements MySqlRouteAuditPublisher {

    @Override
    public void publish(MySqlRouteAuditEvent event) {
    }
}
