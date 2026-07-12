package io.github.surezzzzzz.sdk.kafka.route.resolver;

import io.github.surezzzzzz.sdk.kafka.route.configuration.SimpleKafkaRouteProperties;
import io.github.surezzzzzz.sdk.kafka.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.kafka.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.kafka.route.constant.RouteMatchType;
import io.github.surezzzzzz.sdk.kafka.route.exception.RouteException;
import io.github.surezzzzzz.sdk.kafka.route.matcher.KafkaRoutePatternMatcher;
import io.github.surezzzzzz.sdk.kafka.route.model.KafkaRouteContext;
import io.github.surezzzzzz.sdk.kafka.route.support.KafkaRouteStringHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 默认 Kafka 路由解析器
 *
 * @author surezzzzzz
 */
public class DefaultKafkaRouteResolver implements KafkaRouteResolver {

    private final SimpleKafkaRouteProperties properties;
    private final KafkaRoutePatternMatcher patternMatcher;
    private final List<CompiledRouteRule> compiledRules;

    public DefaultKafkaRouteResolver(SimpleKafkaRouteProperties properties, KafkaRoutePatternMatcher patternMatcher) {
        this.properties = properties;
        this.patternMatcher = patternMatcher;
        this.compiledRules = compileRules(properties.getRules());
    }

    @Override
    public String resolveDataSource(KafkaRouteContext context) {
        SimpleKafkaRouteProperties.RouteRule rule = resolveRule(context);
        if (rule != null) {
            return rule.getDatasource();
        }
        return properties.getDefaultSource();
    }

    @Override
    public SimpleKafkaRouteProperties.RouteRule resolveRule(KafkaRouteContext context) {
        String routeInput = context == null ? null : context.getRouteInput();
        if (!KafkaRouteStringHelper.hasText(routeInput)) {
            throw new RouteException(ErrorCode.KAFKA_ROUTE_008, ErrorMessage.ROUTE_INPUT_EMPTY);
        }
        for (CompiledRouteRule rule : compiledRules) {
            if (patternMatcher.matches(routeInput, rule.getMatchType(), rule.getRule().getPattern(), rule.getPattern())) {
                return rule.getRule();
            }
        }
        return null;
    }

    private List<CompiledRouteRule> compileRules(List<SimpleKafkaRouteProperties.RouteRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return Collections.emptyList();
        }
        List<RuleWithIndex> enabledRules = new ArrayList<>();
        for (int i = 0; i < rules.size(); i++) {
            SimpleKafkaRouteProperties.RouteRule rule = rules.get(i);
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
        private final SimpleKafkaRouteProperties.RouteRule rule;
        private final int index;

        RuleWithIndex(SimpleKafkaRouteProperties.RouteRule rule, int index) {
            this.rule = rule;
            this.index = index;
        }

        SimpleKafkaRouteProperties.RouteRule getRule() {
            return rule;
        }

        int getIndex() {
            return index;
        }
    }

    private static class CompiledRouteRule {
        private final SimpleKafkaRouteProperties.RouteRule rule;
        private final RouteMatchType matchType;
        private final Pattern pattern;

        CompiledRouteRule(SimpleKafkaRouteProperties.RouteRule rule, RouteMatchType matchType, Pattern pattern) {
            this.rule = rule;
            this.matchType = matchType;
            this.pattern = pattern;
        }

        SimpleKafkaRouteProperties.RouteRule getRule() {
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
