package io.github.surezzzzzz.sdk.elasticsearch.route.resolver;

import io.github.surezzzzzz.sdk.elasticsearch.route.annotation.SimpleElasticsearchRouteComponent;
import io.github.surezzzzzz.sdk.elasticsearch.route.configuration.SimpleElasticsearchRouteProperties;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.route.exception.RouteException;
import io.github.surezzzzzz.sdk.elasticsearch.route.matcher.RoutePatternMatcher;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import java.util.*;
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
    private final Map<String, SimpleElasticsearchRouteProperties.RouteRule> routingCache = new ConcurrentHashMap<>();

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
            log.debug("未配置路由规则");
        } else {
            this.sortedEnabledRules = rules.stream()
                    .filter(SimpleElasticsearchRouteProperties.RouteRule::isEnable)
                    .sorted(Comparator.comparingInt(SimpleElasticsearchRouteProperties.RouteRule::getPriority))
                    .collect(Collectors.toList());

            log.info("已缓存启用的路由规则，数量={}，按 priority 升序匹配", sortedEnabledRules.size());
        }
    }

    /**
     * 解析索引对应的数据源
     *
     * @param indexName 索引名称
     * @return 数据源key
     */
    public String resolveDataSource(String indexName) {
        SimpleElasticsearchRouteProperties.RouteRule rule = resolveRule(indexName);
        return rule != null ? rule.getDatasource() : properties.getDefaultSource();
    }

    /**
     * 根据本次调用涉及的索引判定唯一数据源。
     *
     * <p>同一次 Spring Data 调用不能被拆成多个底层模板请求；因此只要候选索引命中不同
     * 数据源，必须在发出网络请求前失败，不能选择第一个索引或回退默认数据源。</p>
     *
     * @param indexNames 本次调用涉及的索引名
     * @return 唯一的数据源 key；未给出索引时返回默认数据源
     */
    public String resolveDataSourceOrThrow(String[] indexNames) {
        if (indexNames == null || indexNames.length == 0) {
            return properties.getDefaultSource();
        }
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (String indexName : indexNames) {
            if (indexName != null && !indexName.trim().isEmpty()) {
                String datasource = resolveDataSource(indexName);
                grouped.computeIfAbsent(datasource, key -> new ArrayList<>()).add(indexName);
            }
        }
        if (grouped.isEmpty()) {
            return properties.getDefaultSource();
        }
        if (grouped.size() == 1) {
            return grouped.keySet().iterator().next();
        }
        throw new RouteException(ErrorCode.ROUTE_CROSS_DATASOURCE,
                String.format(ErrorMessage.ROUTE_CROSS_DATASOURCE, grouped.keySet(), grouped));
    }

    /**
     * 解析索引对应的路由规则
     *
     * @param indexName 索引名称
     * @return 命中的规则，null 表示未命中任何规则
     */
    public SimpleElasticsearchRouteProperties.RouteRule resolveRule(String indexName) {
        if (indexName == null) {
            return null;
        }
        return routingCache.computeIfAbsent(indexName, this::doResolve);
    }

    /**
     * 执行路由解析，返回命中的规则或 null
     */
    private SimpleElasticsearchRouteProperties.RouteRule doResolve(String indexName) {
        if (sortedEnabledRules.isEmpty()) {
            log.debug("未配置路由规则，index=[{}] 使用默认数据源 [{}]",
                    indexName, properties.getDefaultSource());
            return null;
        }

        // 遍历规则进行匹配
        for (SimpleElasticsearchRouteProperties.RouteRule rule : sortedEnabledRules) {
            if (patternMatcher.matches(indexName, rule)) {
                log.debug("索引命中路由规则，index=[{}]，pattern=[{}]，type=[{}]，priority=[{}]，datasource=[{}]",
                        indexName, rule.getPattern(), rule.getMatchType().getDescription(),
                        rule.getPriority(), rule.getDatasource());
                return rule;
            }
        }

        // 没有匹配的规则
        log.debug("索引未命中路由规则，index=[{}] 使用默认数据源 [{}]",
                indexName, properties.getDefaultSource());
        return null;
    }

    /**
     * 清除路由缓存
     */
    public void clearCache() {
        routingCache.clear();
        log.info("路由缓存已清空");
    }

    /**
     * 清除指定索引的缓存
     *
     * @param indexName 索引名称
     */
    public void clearCache(String indexName) {
        routingCache.remove(indexName);
        log.debug("指定索引路由缓存已清空，index=[{}]", indexName);
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
