package io.github.surezzzzzz.sdk.kafka.route.matcher;

import io.github.surezzzzzz.sdk.kafka.route.annotation.SimpleKafkaRouteComponent;
import io.github.surezzzzzz.sdk.kafka.route.constant.RouteMatchType;

import java.util.regex.Pattern;

/**
 * Kafka 路由模式匹配器
 *
 * @author surezzzzzz
 */
@SimpleKafkaRouteComponent
public class KafkaRoutePatternMatcher {

    public boolean matches(String routeInput, RouteMatchType matchType, String pattern, Pattern compiledPattern) {
        if (routeInput == null || matchType == null || pattern == null) {
            return false;
        }
        switch (matchType) {
            case EXACT:
                return routeInput.equals(pattern);
            case PREFIX:
                return routeInput.startsWith(pattern);
            case SUFFIX:
                return routeInput.endsWith(pattern);
            case WILDCARD:
            case REGEX:
                return compiledPattern != null && compiledPattern.matcher(routeInput).matches();
            default:
                return false;
        }
    }

    public Pattern compile(RouteMatchType matchType, String pattern) {
        if (matchType == RouteMatchType.REGEX) {
            return Pattern.compile(pattern);
        }
        if (matchType == RouteMatchType.WILDCARD) {
            return Pattern.compile(toWildcardRegex(pattern));
        }
        return null;
    }

    public String toWildcardRegex(String pattern) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            char ch = pattern.charAt(i);
            if (ch == '*') {
                builder.append(".*");
            } else if (ch == '?') {
                builder.append('.');
            } else {
                appendEscaped(builder, ch);
            }
        }
        return builder.toString();
    }

    private void appendEscaped(StringBuilder builder, char ch) {
        if ("\\.[]{}()+-^$|".indexOf(ch) >= 0) {
            builder.append('\\');
        }
        builder.append(ch);
    }
}
