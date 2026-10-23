package io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.spi;

import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model.GatewayRouteEvent;

/**
 * 控制面事件发布端口。实现方应隔离消费异常，不影响快照构建。
 *
 * @author surezzzzzz
 */
public interface GatewayRouteEventPublisher {

    /**
     * 发布一条脱敏事件。
     */
    void publish(GatewayRouteEvent event);
}
