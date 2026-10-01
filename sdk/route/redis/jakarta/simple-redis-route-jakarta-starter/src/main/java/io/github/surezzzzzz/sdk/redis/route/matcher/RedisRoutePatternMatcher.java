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

    /**
     * 按指定匹配类型判断路由 key 是否匹配规则。
     *
     * @param routeKey        待路由的 key
     * @param matchType       匹配类型
     * @param pattern         原始匹配表达式
     * @param compiledPattern wildcard 或 regex 类型预编译的正则表达式
     * @return 匹配成功时返回 true；必要参数缺失或未匹配时返回 false
     */
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

    /**
     * 为 wildcard 或 regex 路由规则编译正则表达式。
     *
     * @param matchType 匹配类型
     * @param pattern   原始匹配表达式
     * @return wildcard 或 regex 类型的预编译正则；其他类型返回 null
     */
    public Pattern compile(RouteMatchType matchType, String pattern) {
        if (matchType == RouteMatchType.REGEX) {
            return Pattern.compile(pattern);
        }
        if (matchType == RouteMatchType.WILDCARD) {
            return Pattern.compile(toWildcardRegex(pattern));
        }
        return null;
    }

    /**
     * 将仅支持 {@code *} 与 {@code ?} 的通配符表达式转换为完整正则表达式。
     *
     * @param pattern 通配符表达式
     * @return 转换后的正则表达式
     */
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
