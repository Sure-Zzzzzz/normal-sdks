package io.github.surezzzzzz.sdk.metrics.aksk.server.listener;

import io.github.surezzzzzz.sdk.auth.aksk.server.event.AbstractTokenEvent;
import io.github.surezzzzzz.sdk.metrics.aksk.server.annotation.SimpleAkskServerMetricsComponent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;

/**
 * Token 指标监听器
 *
 * <p>监听 {@link AbstractTokenEvent}，写入 Token 操作计数指标。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleAkskServerMetricsComponent
@ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
@ConditionalOnProperty(
        prefix = "io.github.surezzzzzz.sdk.metrics.aksk.server",
        name = "enable",
        havingValue = "true",
        matchIfMissing = true
)
public class TokenMetricsListener {

    private static final String METRIC_TOKEN_TOTAL = "smart_aksk_token_total";

    private final MeterRegistry registry;

    public TokenMetricsListener(MeterRegistry registry) {
        this.registry = registry;
        log.info("TokenMetricsListener initialized");
    }

    @EventListener
    public void onTokenEvent(AbstractTokenEvent event) {
        String eventType = event.getEventType().toString();
        String clientType = event.getClientType();

        Counter.builder(METRIC_TOKEN_TOTAL)
                .tag("eventType", eventType)
                .tag("clientType", clientType)
                .register(registry)
                .increment();
    }
}
