package io.github.surezzzzzz.sdk.metrics.aksk.resource.listener;

import io.github.surezzzzzz.sdk.auth.aksk.resource.core.event.AkskAccessEvent;
import io.github.surezzzzzz.sdk.metrics.aksk.resource.annotation.SimpleAkskResourceMetricsComponent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * AKSK 认证指标监听器
 *
 * <p>监听 {@link AkskAccessEvent}，写入认证请求计数和认证耗时指标。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleAkskResourceMetricsComponent
@ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
@ConditionalOnProperty(
        prefix = "io.github.surezzzzzz.sdk.metrics.aksk.resource",
        name = "enable",
        havingValue = "true",
        matchIfMissing = true
)
public class AkskAccessMetricsListener {

    private static final String METRIC_ACCESS_TOTAL = "smart_aksk_access_total";
    private static final String METRIC_AUTHENTICATE_SECONDS = "smart_aksk_authenticate_seconds";
    private static final String CONTEXT_KEY_ERROR = "error";
    private static final String CONTEXT_KEY_DURATION_NANOS = "durationNanos";

    private final MeterRegistry registry;

    public AkskAccessMetricsListener(MeterRegistry registry) {
        this.registry = registry;
        log.info("AkskAccessMetricsListener initialized");
    }

    @EventListener(AkskAccessEvent.class)
    public void onAccess(AkskAccessEvent event) {
        String result = resolveResult(event);
        String clientType = event.getClientType();
        String source = event.getSource();

        Counter.builder(METRIC_ACCESS_TOTAL)
                .tag("result", result)
                .tag("clientType", clientType)
                .tag("source", source)
                .register(registry)
                .increment();

        Long durationNanos = resolveDurationNanos(event);
        if (durationNanos != null && durationNanos > 0) {
            Timer.builder(METRIC_AUTHENTICATE_SECONDS)
                    .tag("clientType", clientType)
                    .tag("source", source)
                    .register(registry)
                    .record(durationNanos, TimeUnit.NANOSECONDS);
        }
    }

    private String resolveResult(AkskAccessEvent event) {
        Map<String, String> context = event.getContext();
        if (context != null && context.containsKey(CONTEXT_KEY_ERROR)) {
            return "fail";
        }
        return "success";
    }

    private Long resolveDurationNanos(AkskAccessEvent event) {
        Map<String, String> context = event.getContext();
        if (context == null) {
            return null;
        }
        String val = context.get(CONTEXT_KEY_DURATION_NANOS);
        return val != null ? Long.parseLong(val) : null;
    }
}