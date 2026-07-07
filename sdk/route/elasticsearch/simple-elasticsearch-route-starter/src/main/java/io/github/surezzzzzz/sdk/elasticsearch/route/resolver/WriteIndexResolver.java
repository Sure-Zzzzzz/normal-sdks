package io.github.surezzzzzz.sdk.elasticsearch.route.resolver;

import io.github.surezzzzzz.sdk.elasticsearch.route.configuration.SimpleElasticsearchRouteProperties.RouteRule;

import java.time.ZoneId;

/**
 * 写索引渲染能力契约。
 *
 * <p>把 raw index（如 {@code app-log.access}）按命中的 {@link RouteRule} 渲染成当日写索引
 * （如 {@code app-log-2026.07.03}）。route 的 AOP 拦截器和外部 SDK（persistence-starter）
 * 共用同一实例，保证渲染结果一致。
 *
 * <p>用户可通过自定义实现 {@code WriteIndexResolver} 类型的 bean 替换默认行为
 * （默认实现 {@code DefaultWriteIndexResolver} 由 {@code @ConditionalOnMissingBean} 注册）。
 *
 * @author surezzzzzz
 * @since 1.1.2
 */
public interface WriteIndexResolver {

    /**
     * 给 raw index name，先 resolveRule 再渲染写索引。未命中规则或模板为空时返回原值。
     *
     * @param rawIndex 原始索引名
     * @return 渲染后的写索引名；未命中规则或模板为空时返回原值
     */
    String resolveWriteIndex(String rawIndex);

    /**
     * 给已命中的 rule，直接渲染写索引，避免重复 resolveRule。
     *
     * @param rule 命中的路由规则
     * @return 渲染后的写索引名；rule 为 null 或模板为空时返回 null
     */
    String resolveWriteIndex(RouteRule rule);

    /**
     * 核心渲染：按模板 + 时区算出索引名。
     *
     * @param template 模板字符串，如 {@code "a-{yyyy.MM.dd}"}
     * @param zoneId   时区
     * @return 渲染后的索引名，如 {@code "a-2026.07.03"}
     */
    String renderTemplate(String template, ZoneId zoneId);

    /**
     * 渲染日期模板（使用 JVM 默认时区）。
     *
     * @param template 模板字符串
     * @return 渲染后的索引名
     */
    String renderTemplate(String template);

    /**
     * 清除 DateTimeFormatter 缓存。
     */
    void clearFormatterCache();

    /**
     * 获取 DateTimeFormatter 缓存大小。
     *
     * @return 缓存大小
     */
    int getFormatterCacheSize();
}
