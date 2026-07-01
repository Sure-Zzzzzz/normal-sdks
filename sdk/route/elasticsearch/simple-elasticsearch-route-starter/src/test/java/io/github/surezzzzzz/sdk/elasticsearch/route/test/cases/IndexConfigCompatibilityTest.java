package io.github.surezzzzzz.sdk.elasticsearch.route.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.route.configuration.SimpleElasticsearchRouteProperties;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * write-index / read-index 新旧配置兼容测试
 *
 * @author surezzzzzz
 * @since 1.1.1
 */
@Slf4j
public class IndexConfigCompatibilityTest {

    @Test
    public void groupedGlobalWriteIndexZoneIdTakesEffect() {
        log.info("=== groupedGlobalWriteIndexZoneIdTakesEffect ===");
        SimpleElasticsearchRouteProperties properties = new SimpleElasticsearchRouteProperties();
        properties.getWriteIndex().setZoneId("Asia/Shanghai");

        assertEquals("Asia/Shanghai", properties.getEffectiveWriteIndexZoneId(),
                "全局 write-index.zone-id 应作为生效时区");
    }

    @Test
    public void legacyGlobalWriteIndexZoneIdFallbackWorks() {
        log.info("=== legacyGlobalWriteIndexZoneIdFallbackWorks ===");
        SimpleElasticsearchRouteProperties properties = new SimpleElasticsearchRouteProperties();
        properties.setWriteIndexZoneId("UTC");

        assertEquals("UTC", properties.getEffectiveWriteIndexZoneId(),
                "未配置 write-index.zone-id 时应兼容旧 write-index-zone-id");
    }

    @Test
    public void groupedGlobalWriteIndexZoneIdOverridesLegacy() {
        log.info("=== groupedGlobalWriteIndexZoneIdOverridesLegacy ===");
        SimpleElasticsearchRouteProperties properties = new SimpleElasticsearchRouteProperties();
        properties.setWriteIndexZoneId("UTC");
        properties.getWriteIndex().setZoneId("Asia/Shanghai");

        assertEquals("Asia/Shanghai", properties.getEffectiveWriteIndexZoneId(),
                "新分组配置应优先于旧平铺配置");
    }

    @Test
    public void groupedRuleIndexConfigTakesEffect() {
        log.info("=== groupedRuleIndexConfigTakesEffect ===");
        SimpleElasticsearchRouteProperties.RouteRule rule = new SimpleElasticsearchRouteProperties.RouteRule();
        rule.getWriteIndex().setTemplate("log-{yyyy.MM.dd}");
        rule.getWriteIndex().setZoneId("Asia/Shanghai");
        rule.getReadIndex().setPattern("log-*");

        assertEquals("log-{yyyy.MM.dd}", rule.getEffectiveWriteIndexTemplate(),
                "write-index.template 应作为生效写模板");
        assertEquals("Asia/Shanghai", rule.getEffectiveWriteIndexZoneId(),
                "write-index.zone-id 应作为生效 rule 级时区");
        assertEquals("log-*", rule.getEffectiveReadIndexPattern(),
                "read-index.pattern 应作为生效读索引模式");
    }

    @Test
    public void legacyRuleIndexConfigFallbackWorks() {
        log.info("=== legacyRuleIndexConfigFallbackWorks ===");
        SimpleElasticsearchRouteProperties.RouteRule rule = new SimpleElasticsearchRouteProperties.RouteRule();
        rule.setWriteIndexTemplate("legacy-{yyyy.MM.dd}");
        rule.setWriteIndexZoneId("UTC");
        rule.setReadIndexPattern("legacy-*");

        assertEquals("legacy-{yyyy.MM.dd}", rule.getEffectiveWriteIndexTemplate(),
                "未配置 write-index.template 时应兼容旧 write-index-template");
        assertEquals("UTC", rule.getEffectiveWriteIndexZoneId(),
                "未配置 write-index.zone-id 时应兼容旧 write-index-zone-id");
        assertEquals("legacy-*", rule.getEffectiveReadIndexPattern(),
                "未配置 read-index.pattern 时应兼容旧 read-index-pattern");
    }

    @Test
    public void groupedRuleIndexConfigOverridesLegacy() {
        log.info("=== groupedRuleIndexConfigOverridesLegacy ===");
        SimpleElasticsearchRouteProperties.RouteRule rule = new SimpleElasticsearchRouteProperties.RouteRule();
        rule.setWriteIndexTemplate("legacy-{yyyy.MM.dd}");
        rule.setWriteIndexZoneId("UTC");
        rule.setReadIndexPattern("legacy-*");
        rule.getWriteIndex().setTemplate("new-{yyyy.MM.dd}");
        rule.getWriteIndex().setZoneId("Asia/Shanghai");
        rule.getReadIndex().setPattern("new-*");

        assertEquals("new-{yyyy.MM.dd}", rule.getEffectiveWriteIndexTemplate(),
                "write-index.template 应优先于旧 write-index-template");
        assertEquals("Asia/Shanghai", rule.getEffectiveWriteIndexZoneId(),
                "write-index.zone-id 应优先于旧 write-index-zone-id");
        assertEquals("new-*", rule.getEffectiveReadIndexPattern(),
                "read-index.pattern 应优先于旧 read-index-pattern");
    }
}
