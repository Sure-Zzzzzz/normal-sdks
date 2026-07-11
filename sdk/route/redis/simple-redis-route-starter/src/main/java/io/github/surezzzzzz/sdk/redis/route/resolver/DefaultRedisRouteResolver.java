package io.github.surezzzzzz.sdk.redis.route.resolver;

import io.github.surezzzzzz.sdk.redis.route.configuration.SimpleRedisRouteProperties;
import io.github.surezzzzzz.sdk.redis.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.redis.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.redis.route.constant.RouteMatchType;
import io.github.surezzzzzz.sdk.redis.route.exception.RouteException;
import io.github.surezzzzzz.sdk.redis.route.matcher.RedisRoutePatternMatcher;
import io.github.surezzzzzz.sdk.redis.route.support.RedisRouteStringHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 默认 Redis 路由解析器
 *
 * @author surezzzzzz
 */
public class DefaultRedisRouteResolver implements RedisRouteResolver {

    private final SimpleRedisRouteProperties properties;
    private final RedisRoutePatternMatcher patternMatcher;
    private final List<CompiledRouteRule> compiledRules;

    public DefaultRedisRouteResolver(SimpleRedisRouteProperties properties, RedisRoutePatternMatcher patternMatcher) {
        this.properties = properties;
        this.patternMatcher = patternMatcher;
        this.compiledRules = compileRules(properties.getRules());
    }

    @Override
    public String resolveDataSource(String routeKey) {
        SimpleRedisRouteProperties.RouteRule rule = resolveRule(routeKey);
        if (rule != null) {
            return rule.getDatasource();
        }
        return properties.getDefaultSource();
    }

    @Override
    public SimpleRedisRouteProperties.RouteRule resolveRule(String routeKey) {
        if (!RedisRouteStringHelper.hasText(routeKey)) {
            throw new RouteException(ErrorCode.REDIS_ROUTE_008, ErrorMessage.ROUTE_KEY_EMPTY);
        }
        for (CompiledRouteRule rule : compiledRules) {
            if (patternMatcher.matches(routeKey, rule.getMatchType(), rule.getRule().getPattern(), rule.getPattern())) {
                return rule.getRule();
            }
        }
        return null;
    }

    private List<CompiledRouteRule> compileRules(List<SimpleRedisRouteProperties.RouteRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return Collections.emptyList();
        }
        List<RuleWithIndex> enabledRules = new ArrayList<>();
        for (int i = 0; i < rules.size(); i++) {
            SimpleRedisRouteProperties.RouteRule rule = rules.get(i);
            if (rule != null && rule.isEnable()) {
                enabledRules.add(new RuleWithIndex(rule, i));
            }
        }
        Collections.sort(enabledRules, new Comparator<RuleWithIndex>() {
            @Override
            public int compare(RuleWithIndex left, RuleWithIndex right) {
                int priorityCompare = Integer.compare(left.getRule().getPriority(), right.getRule().getPriority());
                if (priorityCompare != 0) {
                    return priorityCompare;
                }
                return Integer.compare(left.getIndex(), right.getIndex());
            }
        });
        List<CompiledRouteRule> result = new ArrayList<>();
        for (RuleWithIndex item : enabledRules) {
            RouteMatchType matchType = RouteMatchType.fromCode(item.getRule().getType());
            Pattern pattern = patternMatcher.compile(matchType, item.getRule().getPattern());
            result.add(new CompiledRouteRule(item.getRule(), matchType, pattern));
        }
        return Collections.unmodifiableList(result);
    }

    private static class RuleWithIndex {
        private final SimpleRedisRouteProperties.RouteRule rule;
        private final int index;

        RuleWithIndex(SimpleRedisRouteProperties.RouteRule rule, int index) {
            this.rule = rule;
            this.index = index;
        }

        SimpleRedisRouteProperties.RouteRule getRule() {
            return rule;
        }

        int getIndex() {
            return index;
        }
    }

    private static class CompiledRouteRule {
        private final SimpleRedisRouteProperties.RouteRule rule;
        private final RouteMatchType matchType;
        private final Pattern pattern;

        CompiledRouteRule(SimpleRedisRouteProperties.RouteRule rule, RouteMatchType matchType, Pattern pattern) {
            this.rule = rule;
            this.matchType = matchType;
            this.pattern = pattern;
        }

        SimpleRedisRouteProperties.RouteRule getRule() {
            return rule;
        }

        RouteMatchType getMatchType() {
            return matchType;
        }

        Pattern getPattern() {
            return pattern;
        }
    }
}
