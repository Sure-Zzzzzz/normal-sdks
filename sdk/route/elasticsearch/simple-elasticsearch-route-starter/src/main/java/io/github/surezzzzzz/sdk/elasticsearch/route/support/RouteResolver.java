package io.github.surezzzzzz.sdk.elasticsearch.route.support;

import io.github.surezzzzzz.sdk.elasticsearch.route.annotation.SimpleElasticsearchRouteComponent;
import io.github.surezzzzzz.sdk.elasticsearch.route.configuration.SimpleElasticsearchRouteProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 路由解析器
 * 负责根据索引名称解析到对应的数据源
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleElasticsearchRouteComponent
@RequiredArgsConstructor
public class RouteResolver {

    private final SimpleElasticsearchRouteProperties properties;
    private final RoutePatternMatcher patternMatcher;
    private final Map<String, String> routingCache = new ConcurrentHashMap<>();

    /**
     * 排序后的启用规则列表（缓存）
     */
    private List<SimpleElasticsearchRouteProperties.RouteRule> sortedEnabledRules;

    @PostConstruct
    public void init() {
        // 缓存排序后的启用规则
        List<SimpleElasticsearchRouteProperties.RouteRule> rules = properties.getRules();

        if (CollectionUtils.isEmpty(rules)) {
            this.sortedEnabledRules = new ArrayList<>();
            log.debug("No route rules configured");
        } else {
            this.sortedEnabledRules = rules.stream()
                    .filter(SimpleElasticsearchRouteProperties.RouteRule::isEnable)
                    .sorted(Comparator.comparingInt(SimpleElasticsearchRouteProperties.RouteRule::getPriority))
                    .collect(Collectors.toList());

            log.info("Cached {} enabled route rules (sorted by priority)", sortedEnabledRules.size());
        }
    }

    /**
     * 解析索引对应的数据源
     *
     * @param indexName 索引名称
     * @return 数据源key
     */
    public String resolveDataSource(String indexName) {
        if (indexName == null) {
            log.trace("Index name is null, using default datasource [{}]", properties.getDefaultSource());
            return properties.getDefaultSource();
        }

        return routingCache.computeIfAbsent(indexName, this::doResolve);
    }

    /**
     * 执行路由解析
     */
    private String doResolve(String indexName) {
        if (sortedEnabledRules.isEmpty()) {
            log.debug("No route rules configured, index [{}] using default datasource [{}]",
                    indexName, properties.getDefaultSource());
            return properties.getDefaultSource();
        }

        // 遍历规则进行匹配
        for (SimpleElasticsearchRouteProperties.RouteRule rule : sortedEnabledRules) {
            if (patternMatcher.matches(indexName, rule)) {
                log.debug("Index [{}] matched rule [pattern={}, type={}, priority={}], routing to datasource [{}]",
                        indexName, rule.getPattern(), rule.getMatchType().getDescription(),
                        rule.getPriority(), rule.getDatasource());
                return rule.getDatasource();
            }
        }

        // 没有匹配的规则，使用默认数据源
        log.debug("Index [{}] no matching rule, using default datasource [{}]",
                indexName, properties.getDefaultSource());
        return properties.getDefaultSource();
    }

    /**
     * 清除路由缓存
     */
    public void clearCache() {
        routingCache.clear();
        log.info("Route cache cleared");
    }

    /**
     * 清除指定索引的缓存
     *
     * @param indexName 索引名称
     */
    public void clearCache(String indexName) {
        routingCache.remove(indexName);
        log.debug("Route cache cleared for index [{}]", indexName);
    }

    /**
     * 获取缓存统计信息
     *
     * @return 缓存大小
     */
    public int getCacheSize() {
        return routingCache.size();
    }
}
