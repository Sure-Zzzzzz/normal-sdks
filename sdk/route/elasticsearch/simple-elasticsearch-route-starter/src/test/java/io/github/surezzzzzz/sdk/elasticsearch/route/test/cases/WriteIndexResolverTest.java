package io.github.surezzzzzz.sdk.elasticsearch.route.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.route.configuration.SimpleElasticsearchRouteProperties.RouteRule;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.DefaultWriteIndexResolver;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.RouteResolver;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.WriteIndexResolver;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * WriteIndexResolver 1.1.2 新增入口单测。
 *
 * <p>覆盖 {@code resolveWriteIndex(String)} 与 {@code resolveWriteIndex(RouteRule)} 的全部分支：
 * null 入参、未命中规则、模板为空 fallback、命中渲染、rule 级时区优先、非法时区降级、全局时区为 null 降级。
 *
 * <p>两个入口的 fallback 语义不同（String 版本返回 rawIndex，RouteRule 版本返回 null），本类显式验证该差异。
 * renderTemplate 与缓存行为由 {@link DateTimeFormatterCacheTest} / {@link DateShardingAndAsyncWriteTest} 覆盖。
 *
 * @author surezzzzzz
 * @since 1.1.2
 */
@Slf4j
public class WriteIndexResolverTest {

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private RouteResolver routeResolver;

    @BeforeEach
    void setUp() {
        routeResolver = mock(RouteResolver.class);
    }

    private WriteIndexResolver resolver(ZoneId globalZone) {
        return new DefaultWriteIndexResolver(routeResolver, globalZone);
    }

    private RouteRule ruleWithTemplate(String template, String zoneId) {
        RouteRule rule = new RouteRule();
        rule.getWriteIndex().setTemplate(template);
        if (zoneId != null) {
            rule.getWriteIndex().setZoneId(zoneId);
        }
        return rule;
    }

    // ===== resolveWriteIndex(String rawIndex) =====

    @Test
    @DisplayName("resolveWriteIndex(String) rawIndex 为 null 时返回 null，不调用 routeResolver")
    public void resolveWriteIndex_nullRawIndex_returnsNull() {
        log.info("=== resolveWriteIndex_nullRawIndex_returnsNull ===");
        WriteIndexResolver resolver = resolver(ZoneId.systemDefault());

        assertNull(resolver.resolveWriteIndex((String) null), "rawIndex 为 null 应返回 null");
    }

    @Test
    @DisplayName("resolveWriteIndex(String) 未命中任何规则时原样返回 rawIndex")
    public void resolveWriteIndex_noMatchingRule_returnsRawIndex() {
        log.info("=== resolveWriteIndex_noMatchingRule_returnsRawIndex ===");
        when(routeResolver.resolveRule("app-log.access")).thenReturn(null);
        WriteIndexResolver resolver = resolver(ZoneId.systemDefault());

        String result = resolver.resolveWriteIndex("app-log.access");

        assertEquals("app-log.access", result, "未命中规则应原样返回 rawIndex");
    }

    @Test
    @DisplayName("resolveWriteIndex(String) 命中规则但模板为空时原样返回 rawIndex")
    public void resolveWriteIndex_ruleWithoutTemplate_returnsRawIndex() {
        log.info("=== resolveWriteIndex_ruleWithoutTemplate_returnsRawIndex ===");
        RouteRule rule = new RouteRule();
        when(routeResolver.resolveRule("app-log.access")).thenReturn(rule);
        WriteIndexResolver resolver = resolver(ZoneId.systemDefault());

        String result = resolver.resolveWriteIndex("app-log.access");

        assertEquals("app-log.access", result, "命中规则但无模板应原样返回 rawIndex");
    }

    @Test
    @DisplayName("resolveWriteIndex(String) 命中规则且带模板时按全局时区渲染")
    public void resolveWriteIndex_ruleWithTemplate_rendersWithGlobalZone() {
        log.info("=== resolveWriteIndex_ruleWithTemplate_rendersWithGlobalZone ===");
        ZoneId global = ZoneId.of("UTC");
        RouteRule rule = ruleWithTemplate("app-log-{yyyy.MM.dd}", null);
        when(routeResolver.resolveRule("app-log.access")).thenReturn(rule);
        WriteIndexResolver resolver = resolver(global);
        String expected = "app-log-" + LocalDate.now(global).format(DAY_FMT);

        String result = resolver.resolveWriteIndex("app-log.access");

        assertEquals(expected, result, "命中带模板规则应按全局时区渲染");
    }

    @Test
    @DisplayName("resolveWriteIndex(String) rule 级时区优先于全局时区")
    public void resolveWriteIndex_ruleZoneIdOverridesGlobal() {
        log.info("=== resolveWriteIndex_ruleZoneIdOverridesGlobal ===");
        ZoneId global = ZoneId.of("Asia/Shanghai");
        ZoneId ruleZone = ZoneId.of("UTC");
        RouteRule rule = ruleWithTemplate("app-log-{yyyy.MM.dd}", ruleZone.toString());
        when(routeResolver.resolveRule("app-log.access")).thenReturn(rule);
        WriteIndexResolver resolver = resolver(global);
        String expected = "app-log-" + LocalDate.now(ruleZone).format(DAY_FMT);

        String result = resolver.resolveWriteIndex("app-log.access");

        assertEquals(expected, result, "rule 级时区应优先于全局时区");
    }

    // ===== resolveWriteIndex(RouteRule rule) =====

    @Test
    @DisplayName("resolveWriteIndex(RouteRule) rule 为 null 时返回 null")
    public void resolveWriteIndex_nullRule_returnsNull() {
        log.info("=== resolveWriteIndex_nullRule_returnsNull ===");
        WriteIndexResolver resolver = resolver(ZoneId.systemDefault());

        assertNull(resolver.resolveWriteIndex((RouteRule) null), "rule 为 null 应返回 null");
    }

    @Test
    @DisplayName("resolveWriteIndex(RouteRule) 模板为空时返回 null（与 String 版本的 rawIndex fallback 不同）")
    public void resolveWriteIndex_ruleWithoutTemplate_returnsNull() {
        log.info("=== resolveWriteIndex_ruleWithoutTemplate_returnsNull ===");
        RouteRule rule = new RouteRule();
        WriteIndexResolver resolver = resolver(ZoneId.systemDefault());

        assertNull(resolver.resolveWriteIndex(rule), "rule 无模板时应返回 null（区别于 String 版本返回 rawIndex）");
    }

    @Test
    @DisplayName("resolveWriteIndex(RouteRule) 命中带模板规则时渲染")
    public void resolveWriteIndex_ruleWithTemplate_renders() {
        log.info("=== resolveWriteIndex_ruleWithTemplate_renders ===");
        ZoneId global = ZoneId.of("UTC");
        RouteRule rule = ruleWithTemplate("app-log-{yyyy.MM.dd}", null);
        WriteIndexResolver resolver = resolver(global);
        String expected = "app-log-" + LocalDate.now(global).format(DAY_FMT);

        String result = resolver.resolveWriteIndex(rule);

        assertEquals(expected, result, "rule 带模板应渲染");
    }

    @Test
    @DisplayName("resolveWriteIndex(RouteRule) rule 级时区非法时降级到全局时区且不抛异常")
    public void resolveWriteIndex_invalidRuleZoneId_fallsBackToGlobal() {
        log.info("=== resolveWriteIndex_invalidRuleZoneId_fallsBackToGlobal ===");
        ZoneId global = ZoneId.of("UTC");
        RouteRule rule = ruleWithTemplate("app-log-{yyyy.MM.dd}", "Foo/Bar");
        WriteIndexResolver resolver = resolver(global);
        String expected = "app-log-" + LocalDate.now(global).format(DAY_FMT);

        String result = resolver.resolveWriteIndex(rule);

        assertEquals(expected, result, "非法 rule 时区应降级到全局时区，不抛异常");
    }

    @Test
    @DisplayName("resolveWriteIndex(RouteRule) 同一非法时区重复调用仍能渲染（warn 去重不阻断）")
    public void resolveWriteIndex_invalidZoneIdRepeatCallStillRenders() {
        log.info("=== resolveWriteIndex_invalidZoneIdRepeatCallStillRenders ===");
        ZoneId global = ZoneId.of("UTC");
        RouteRule rule = ruleWithTemplate("app-log-{yyyy.MM.dd}", "Foo/Bar");
        WriteIndexResolver resolver = resolver(global);
        String expected = "app-log-" + LocalDate.now(global).format(DAY_FMT);

        String first = resolver.resolveWriteIndex(rule);
        String second = resolver.resolveWriteIndex(rule);

        assertEquals(expected, first, "首次非法时区调用应渲染");
        assertEquals(expected, second, "重复非法时区调用应仍渲染（warn 去重不影响结果）");
    }

    @Test
    @DisplayName("resolveWriteIndex(RouteRule) 全局时区为 null 时降级到 JVM 默认时区")
    public void resolveWriteIndex_globalNull_fallsBackToSystemDefault() {
        log.info("=== resolveWriteIndex_globalNull_fallsBackToSystemDefault ===");
        RouteRule rule = ruleWithTemplate("app-log-{yyyy.MM.dd}", null);
        WriteIndexResolver resolver = new DefaultWriteIndexResolver(routeResolver, null);
        String expected = "app-log-" + LocalDate.now(ZoneId.systemDefault()).format(DAY_FMT);

        String result = resolver.resolveWriteIndex(rule);

        assertEquals(expected, result, "全局时区为 null 应降级到 JVM 默认时区");
    }
}
