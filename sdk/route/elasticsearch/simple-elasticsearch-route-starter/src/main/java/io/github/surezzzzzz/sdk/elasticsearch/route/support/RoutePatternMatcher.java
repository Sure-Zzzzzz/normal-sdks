package io.github.surezzzzzz.sdk.elasticsearch.route.support;

import io.github.surezzzzzz.sdk.elasticsearch.route.annotation.SimpleElasticsearchRouteComponent;
import io.github.surezzzzzz.sdk.elasticsearch.route.configuration.SimpleElasticsearchRouteProperties;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.RouteMatchType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

import java.util.regex.Pattern;

/**
 * 路由模式匹配器
 * 负责判断索引名称是否匹配路由规则
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleElasticsearchRouteComponent
public class RoutePatternMatcher {

    private final PathMatcher pathMatcher = new AntPathMatcher();

    /**
     * 判断索引名称是否匹配规则
     *
     * @param indexName 索引名称
     * @param rule      路由规则
     * @return 是否匹配
     */
    public boolean matches(String indexName, SimpleElasticsearchRouteProperties.RouteRule rule) {
        if (indexName == null || rule == null) {
            return false;
        }

        String pattern = rule.getPattern();
        RouteMatchType matchType = rule.getMatchType();

        try {
            switch (matchType) {
                case EXACT:
                    return indexName.equals(pattern);

                case PREFIX:
                    return indexName.startsWith(pattern);

                case SUFFIX:
                    return indexName.endsWith(pattern);

                case WILDCARD:
                    return pathMatcher.match(pattern, indexName);

                case REGEX:
                    return Pattern.matches(pattern, indexName);

                default:
                    log.warn("Unsupported route match type [{}], treating as exact match", matchType);
                    return indexName.equals(pattern);
            }
        } catch (Exception e) {
            log.error("Error matching index [{}] with rule [pattern={}, type={}]",
                    indexName, pattern, matchType, e);
            return false;
        }
    }
}
