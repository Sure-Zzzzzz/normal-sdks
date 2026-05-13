package io.github.surezzzzzz.sdk.metrics.limiter.listener;

import io.github.surezzzzzz.sdk.limiter.redis.smart.event.SmartRedisLimiterEvent;
import io.github.surezzzzzz.sdk.metrics.limiter.annotation.SmartRedisLimiterMetricsComponent;
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
 * SmartRedisLimiter 限流指标监听器
 *
 * <p>监听 {@link SmartRedisLimiterEvent}，采集限流指标到 Micrometer。
 *
 * <p>指标列表：
 * <ul>
 *   <li>{@code smart_rate_limit_total} — 限流检查计数（标签：result/algorithm/source/rule）</li>
 *   <li>{@code smart_rate_limit_fallback_total} — 降级触发计数（标签：algorithm/source/strategy）</li>
 *   <li>{@code smart_rate_limit_command_seconds} — Redis 命令耗时（标签：algorithm/source）</li>
 * </ul>
 *
 * @author surezzzzzz
 */
@Slf4j
@SmartRedisLimiterMetricsComponent
@ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
@ConditionalOnProperty(
        prefix = "io.github.surezzzzzz.sdk.metrics.limiter",
        name = "enable",
        havingValue = "true",
        matchIfMissing = true
)
public class SmartRedisLimiterMetricsListener {

    private final MeterRegistry registry;

    public SmartRedisLimiterMetricsListener(MeterRegistry registry) {
        this.registry = registry;
        log.info("SmartRedisLimiterMetricsListener initialized");
    }

    @EventListener
    public void onLimitEvent(SmartRedisLimiterEvent event) {
        String source = normalizeSource(event.getSource());
        String algorithm = event.getAlgorithm() != null ? event.getAlgorithm() : "unknown";
        String rule = resolveRule(event);
        String result = event.isPassed() ? "passed" : "rejected";

        // 1. 限流计数器
        Counter.builder("smart_rate_limit_total")
                .tag("result", result)
                .tag("algorithm", algorithm)
                .tag("source", source)
                .tag("rule", rule)
                .register(registry)
                .increment();

        // 2. 降级计数器（fallback/fallbackStrategy 来自 event.attributes）
        Map<String, Object> attrs = event.getAttributes();
        if (attrs != null && Boolean.TRUE.equals(attrs.get("fallback"))) {
            String fallbackStrategy = attrs.get("fallbackStrategy") != null
                    ? attrs.get("fallbackStrategy").toString() : "unknown";
            Counter.builder("smart_rate_limit_fallback_total")
                    .tag("algorithm", algorithm)
                    .tag("source", source)
                    .tag("strategy", fallbackStrategy)
                    .register(registry)
                    .increment();
        }

        // 3. Redis 命令耗时 Timer
        if (event.getDurationNanos() > 0) {
            Timer.builder("smart_rate_limit_command_seconds")
                    .tag("algorithm", algorithm)
                    .tag("source", source)
                    .register(registry)
                    .record(event.getDurationNanos(), TimeUnit.NANOSECONDS);
        }
    }

    /**
     * 解析 rule 标签值
     *
     * @param event 限流事件
     * @return 规则标识
     */
    private String resolveRule(SmartRedisLimiterEvent event) {
        // 拦截器模式：有 matchedPathPattern 用它，否则 default
        if (event.getMatchedPathPattern() != null && !event.getMatchedPathPattern().isEmpty()) {
            return event.getMatchedPathPattern();
        }
        // 注解模式：用 methodQualifiedName
        if (event.getMethodQualifiedName() != null && !event.getMethodQualifiedName().isEmpty()) {
            return event.getMethodQualifiedName();
        }
        return "default";
    }

    /**
     * 标准化 source 字段（Event 用大写 ASPECT/INTERCEPTOR，Prometheus 标签习惯小写）
     *
     * @param source 事件来源
     * @return 标准化后的来源标识
     */
    private String normalizeSource(String source) {
        if (source == null) {
            return "unknown";
        }
        switch (source.toUpperCase()) {
            case "ASPECT":
                return "annotation";
            case "INTERCEPTOR":
                return "interceptor";
            default:
                return source.toLowerCase();
        }
    }
}
