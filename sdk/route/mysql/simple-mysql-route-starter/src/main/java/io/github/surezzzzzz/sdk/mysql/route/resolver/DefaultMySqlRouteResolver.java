package io.github.surezzzzzz.sdk.mysql.route.resolver;

import io.github.surezzzzzz.sdk.mysql.route.configuration.SimpleMysqlRouteProperties;
import io.github.surezzzzzz.sdk.mysql.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.mysql.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.mysql.route.constant.RouteMatchType;
import io.github.surezzzzzz.sdk.mysql.route.exception.SimpleMysqlRouteException;
import io.github.surezzzzzz.sdk.mysql.route.matcher.MySqlRoutePatternMatcher;
import io.github.surezzzzzz.sdk.mysql.route.support.MySqlRouteStringHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 默认 MySQL Route 解析器。
 *
 * @author surezzzzzz
 */
public class DefaultMySqlRouteResolver implements MySqlRouteResolver {

    private final MySqlRoutePatternMatcher patternMatcher;
    private final List<CompiledRule> rules;

    /**
     * 基于已校验配置构建规则解析器。
     *
     * @param properties     MySQL Route 配置
     * @param patternMatcher 路由规则匹配器
     */
    public DefaultMySqlRouteResolver(SimpleMysqlRouteProperties properties, MySqlRoutePatternMatcher patternMatcher) {
        this.patternMatcher = patternMatcher;
        this.rules = compile(properties.getRules());
    }

    /**
     * 按优先级和声明顺序将业务路由键解析为数据源名称。
     *
     * @param routeKey 调用方提供的业务路由键
     * @return 已注册的数据源名称
     */
    @Override
    public String resolve(String routeKey) {
        if (!MySqlRouteStringHelper.hasText(routeKey)) {
            throw new SimpleMysqlRouteException(ErrorCode.ROUTE_KEY_INVALID, ErrorMessage.ROUTE_KEY_INVALID);
        }
        for (CompiledRule rule : rules) {
            if (patternMatcher.matches(routeKey, rule.matchType, rule.pattern, rule.compiledPattern)) {
                return rule.datasource;
            }
        }
        throw new SimpleMysqlRouteException(ErrorCode.ROUTE_NOT_FOUND,
                String.format(ErrorMessage.ROUTE_NOT_FOUND, routeKey));
    }

    private List<CompiledRule> compile(List<SimpleMysqlRouteProperties.RouteRule> configuredRules) {
        if (configuredRules == null || configuredRules.isEmpty()) {
            return Collections.emptyList();
        }
        List<IndexedRule> enabledRules = new ArrayList<>();
        for (int index = 0; index < configuredRules.size(); index++) {
            SimpleMysqlRouteProperties.RouteRule rule = configuredRules.get(index);
            if (rule != null && rule.isEnable()) {
                enabledRules.add(new IndexedRule(rule, index));
            }
        }
        Collections.sort(enabledRules, new Comparator<IndexedRule>() {
            @Override
            public int compare(IndexedRule left, IndexedRule right) {
                int priority = Integer.compare(right.rule.getPriority(), left.rule.getPriority());
                return priority == 0 ? Integer.compare(left.index, right.index) : priority;
            }
        });
        List<CompiledRule> compiled = new ArrayList<>();
        for (IndexedRule indexed : enabledRules) {
            RouteMatchType matchType = RouteMatchType.fromCode(indexed.rule.getMatchType());
            compiled.add(new CompiledRule(indexed.rule.getPattern(), matchType,
                    indexed.rule.getDatasource(), patternMatcher.compile(matchType, indexed.rule.getPattern())));
        }
        return Collections.unmodifiableList(compiled);
    }

    private static final class IndexedRule {
        private final SimpleMysqlRouteProperties.RouteRule rule;
        private final int index;

        private IndexedRule(SimpleMysqlRouteProperties.RouteRule rule, int index) {
            this.rule = rule;
            this.index = index;
        }
    }

    private static final class CompiledRule {
        private final String pattern;
        private final RouteMatchType matchType;
        private final String datasource;
        private final Pattern compiledPattern;

        private CompiledRule(String pattern, RouteMatchType matchType, String datasource, Pattern compiledPattern) {
            this.pattern = pattern;
            this.matchType = matchType;
            this.datasource = datasource;
            this.compiledPattern = compiledPattern;
        }
    }
}
