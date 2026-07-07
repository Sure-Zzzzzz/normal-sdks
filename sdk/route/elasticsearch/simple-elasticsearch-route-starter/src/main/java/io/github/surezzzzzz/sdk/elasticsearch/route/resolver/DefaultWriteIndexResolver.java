package io.github.surezzzzzz.sdk.elasticsearch.route.resolver;

import io.github.surezzzzzz.sdk.elasticsearch.route.configuration.SimpleElasticsearchRouteProperties.RouteRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 写索引渲染能力默认实现。
 *
 * <p>从 1.1.1 的 {@code RouteRoutingInterceptor} 抽取，渲染算法（模板解析、时区解析、formatter 缓存、
 * 非法 zoneId 降级告警）与 1.1.1 逐行一致，保证升级后渲染结果不变。
 *
 * @author surezzzzzz
 * @since 1.1.2
 */
@Slf4j
@RequiredArgsConstructor
public class DefaultWriteIndexResolver implements WriteIndexResolver {

    private final RouteResolver routeResolver;
    private final ZoneId globalWriteIndexZoneId;

    /** 缓存 DateTimeFormatter，key = pattern 字符串，对齐 SpELHelper/RoutePatternMatcher 缓存风格 */
    private final Map<String, DateTimeFormatter> formatterCache = new ConcurrentHashMap<>();

    /** 已提示过的非法写索引时区配置值 */
    private final Set<String> warnedInvalidZoneIds = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Override
    public String resolveWriteIndex(String rawIndex) {
        if (rawIndex == null) {
            return null;
        }
        RouteRule rule = routeResolver.resolveRule(rawIndex);
        if (rule == null || !StringUtils.hasText(rule.getEffectiveWriteIndexTemplate())) {
            return rawIndex;
        }
        return renderTemplate(rule.getEffectiveWriteIndexTemplate(), resolveZoneId(rule));
    }

    @Override
    public String resolveWriteIndex(RouteRule rule) {
        if (rule == null || !StringUtils.hasText(rule.getEffectiveWriteIndexTemplate())) {
            return null;
        }
        return renderTemplate(rule.getEffectiveWriteIndexTemplate(), resolveZoneId(rule));
    }

    @Override
    public String renderTemplate(String template, ZoneId zoneId) {
        if (!StringUtils.hasText(template)) {
            return template;
        }
        int start = template.indexOf("{");
        int end = template.lastIndexOf("}");
        if (start == -1 || end == -1 || start >= end) {
            return template;
        }
        String pattern = template.substring(start + 1, end);
        try {
            ZoneId effectiveZoneId = zoneId != null ? zoneId : ZoneId.systemDefault();
            DateTimeFormatter formatter = formatterCache.computeIfAbsent(
                    pattern, DateTimeFormatter::ofPattern);
            String dateStr = LocalDate.now(effectiveZoneId).format(formatter);
            return template.substring(0, start) + dateStr + template.substring(end + 1);
        } catch (IllegalArgumentException | DateTimeException e) {
            formatterCache.remove(pattern);
            log.warn("日期模板渲染失败，pattern=[{}]，错误=[{}]，使用原始模板", pattern, e.getMessage());
            return template;
        }
    }

    @Override
    public String renderTemplate(String template) {
        return renderTemplate(template, ZoneId.systemDefault());
    }

    /**
     * 解析有效时区：rule 级 → 全局默认 → JVM 默认。
     */
    private ZoneId resolveZoneId(RouteRule rule) {
        if (rule != null && StringUtils.hasText(rule.getEffectiveWriteIndexZoneId())) {
            try {
                return ZoneId.of(rule.getEffectiveWriteIndexZoneId());
            } catch (Exception e) {
                if (warnedInvalidZoneIds.add(rule.getEffectiveWriteIndexZoneId())) {
                    log.warn("rule [{}]=[{}] 非法，降级到全局默认时区，错误=[{}]",
                            rule.getEffectiveWriteIndexZoneIdConfigName(),
                            rule.getEffectiveWriteIndexZoneId(), e.getMessage());
                }
            }
        }
        return globalWriteIndexZoneId != null ? globalWriteIndexZoneId : ZoneId.systemDefault();
    }

    @Override
    public void clearFormatterCache() {
        formatterCache.clear();
        log.info("DateTimeFormatter 缓存已清空");
    }

    @Override
    public int getFormatterCacheSize() {
        return formatterCache.size();
    }
}
