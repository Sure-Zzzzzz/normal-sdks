package io.github.surezzzzzz.sdk.mysql.route.matcher;

import io.github.surezzzzzz.sdk.mysql.route.annotation.SimpleMysqlRouteComponent;
import io.github.surezzzzzz.sdk.mysql.route.constant.RouteMatchType;

import java.util.regex.Pattern;

/**
 * MySQL Route 规则匹配器。
 *
 * @author surezzzzzz
 */
@SimpleMysqlRouteComponent
public class MySqlRoutePatternMatcher {

    /**
     * 判断路由键是否满足指定的匹配规则。
     *
     * @param routeKey        待匹配的路由键
     * @param matchType       匹配类型
     * @param pattern         配置的匹配模式
     * @param compiledPattern 已编译的正则或通配符模式
     * @return 匹配时返回 true
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
     * 编译需要正则执行的匹配模式。
     *
     * @param matchType 匹配类型
     * @param pattern   配置的匹配模式
     * @return 正则或通配符模式的编译结果，其他匹配类型返回 null
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
     * 将 Route 通配符模式转换为完整匹配的正则表达式片段。
     *
     * @param pattern 通配符模式
     * @return 转换后的正则表达式片段
     */
    public String toWildcardRegex(String pattern) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < pattern.length(); index++) {
            char current = pattern.charAt(index);
            if (current == '*') {
                builder.append(".*");
            } else if (current == '?') {
                builder.append('.');
            } else {
                if ("\\.[]{}()+-^$|".indexOf(current) >= 0) {
                    builder.append('\\');
                }
                builder.append(current);
            }
        }
        return builder.toString();
    }
}
