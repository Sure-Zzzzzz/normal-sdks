package io.github.surezzzzzz.sdk.redis.route.matcher;

import io.github.surezzzzzz.sdk.redis.route.annotation.SimpleRedisRouteComponent;
import io.github.surezzzzzz.sdk.redis.route.constant.RouteMatchType;

import java.util.regex.Pattern;

/**
 * Redis 路由模式匹配器
 *
 * @author surezzzzzz
 */
@SimpleRedisRouteComponent
public class RedisRoutePatternMatcher {

    public boolean matches(String routeKey, RouteMatchType matchType, String pattern, Pattern compiledPattern) {
        if (routeKey == null || matchType == null || pattern == null) {
            return false;
        }
        switch (matchType) {
            case EXACT:
                return routeKey.equals(pattern);
            case PREFIX:
                return routeKey.startsWith(pattern);
            case SUFFIX:
                return routeKey.endsWith(pattern);
            case WILDCARD:
            case REGEX:
                return compiledPattern != null && compiledPattern.matcher(routeKey).matches();
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
