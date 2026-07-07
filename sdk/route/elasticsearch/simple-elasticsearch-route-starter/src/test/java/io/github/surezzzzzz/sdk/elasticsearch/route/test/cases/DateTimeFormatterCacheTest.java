package io.github.surezzzzzz.sdk.elasticsearch.route.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.DefaultWriteIndexResolver;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.WriteIndexResolver;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * DateTimeFormatter 缓存专项测试
 *
 * @author surezzzzzz
 * @since 1.1.1
 */
@Slf4j
public class DateTimeFormatterCacheTest {

    private WriteIndexResolver newResolver() {
        return new DefaultWriteIndexResolver(null, ZoneId.systemDefault());
    }

    @Test
    public void formatterCacheHitOnSamePattern() {
        log.info("=== formatterCacheHitOnSamePattern ===");
        WriteIndexResolver resolver = newResolver();
        resolver.renderTemplate("idx-{yyyy.MM.dd}", ZoneId.systemDefault());
        resolver.renderTemplate("idx-{yyyy.MM.dd}", ZoneId.systemDefault());

        assertEquals(1, resolver.getFormatterCacheSize(),
                "相同 pattern 两次渲染后缓存 size 应为 1");
    }

    @Test
    public void formatterCacheIsolatedByPattern() {
        log.info("=== formatterCacheIsolatedByPattern ===");
        WriteIndexResolver resolver = newResolver();
        resolver.renderTemplate("a-{yyyy}", ZoneId.systemDefault());
        resolver.renderTemplate("b-{yyyy.MM}", ZoneId.systemDefault());
        resolver.renderTemplate("c-{yyyy.MM.dd}", ZoneId.systemDefault());

        assertEquals(3, resolver.getFormatterCacheSize(),
                "三种不同 pattern 各渲染一次后缓存 size 应为 3");
    }

    @Test
    public void invalidLocalDatePatternReturnsOriginalAndNotCached() {
        log.info("=== invalidLocalDatePatternReturnsOriginalAndNotCached ===");
        WriteIndexResolver resolver = newResolver();
        String template = "idx-{HH}";

        String result = resolver.renderTemplate(template, ZoneId.systemDefault());

        assertEquals(template, result, "LocalDate 不支持的 pattern 应原样返回");
        assertEquals(0, resolver.getFormatterCacheSize(), "不可渲染的 pattern 不应留在缓存中");
    }

    @Test
    public void clearFormatterCacheResetsSize() {
        log.info("=== clearFormatterCacheResetsSize ===");
        WriteIndexResolver resolver = newResolver();
        resolver.renderTemplate("a-{yyyy}", ZoneId.systemDefault());
        resolver.renderTemplate("b-{yyyy.MM}", ZoneId.systemDefault());
        assertEquals(2, resolver.getFormatterCacheSize(), "清空前应有 2 个缓存");

        resolver.clearFormatterCache();
        assertEquals(0, resolver.getFormatterCacheSize(), "clearFormatterCache 后 size 应为 0");

        resolver.renderTemplate("a-{yyyy}", ZoneId.systemDefault());
        assertEquals(1, resolver.getFormatterCacheSize(), "清空后重新渲染应重新缓存，size 应为 1");
    }

    @Test
    public void formatterCacheNotSharedAcrossInstances() {
        log.info("=== formatterCacheNotSharedAcrossInstances ===");
        WriteIndexResolver i1 = newResolver();
        WriteIndexResolver i2 = newResolver();

        i1.renderTemplate("a-{yyyy}", ZoneId.systemDefault());

        assertEquals(1, i1.getFormatterCacheSize(), "i1 缓存 size 应为 1");
        assertEquals(0, i2.getFormatterCacheSize(), "i2 缓存与 i1 隔离，size 应为 0");
    }
}
