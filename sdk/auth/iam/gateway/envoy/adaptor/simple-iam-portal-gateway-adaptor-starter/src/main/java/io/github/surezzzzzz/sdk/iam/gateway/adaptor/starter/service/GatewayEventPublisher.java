package io.github.surezzzzzz.sdk.iam.gateway.adaptor.starter.service;

import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model.GatewayRouteEvent;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.spi.GatewayRouteEventPublisher;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.starter.annotation.SimpleIamPortalGatewayAdaptorComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 将控制面事件交给宿主事件总线；事件消费失败不阻断同步主流程。
 */
@SimpleIamPortalGatewayAdaptorComponent
public class GatewayEventPublisher implements GatewayRouteEventPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayEventPublisher.class);
    private final ApplicationEventPublisher delegate;

    public GatewayEventPublisher(ApplicationEventPublisher delegate) {
        this.delegate = delegate;
    }

    /**
     * 发布事件；消费方异常只记录类型，不影响主同步流程。
     */
    @Override
    public void publish(GatewayRouteEvent event) {
        if (event == null) {
            LOGGER.warn("Portal Gateway 事件发布请求为空，忽略本次事件");
            return;
        }
        try {
            delegate.publishEvent(event);
        } catch (RuntimeException ex) {
            LOGGER.warn("Portal Gateway 事件发布失败，继续保留同步结果，eventType={}",
                    event.getType() == null ? null : event.getType().getCode());
        }
    }
}
