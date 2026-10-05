package io.github.surezzzzzz.sdk.elasticsearch.persistence.support;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.exception.PersistenceExecutionException;
import io.github.surezzzzzz.sdk.elasticsearch.route.configuration.SimpleElasticsearchRouteProperties;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.RouteResolver;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.WriteIndexResolver;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

import static io.github.surezzzzzz.sdk.elasticsearch.persistence.constant.PersistenceProtocolConstant.*;

/**
 * 写入目标的唯一解析边界，先校验再借用 Route 的客户端。
 */
@RequiredArgsConstructor
public class PersistenceTargetResolver {
    private final RouteResolver routes;
    private final WriteIndexResolver writes;
    private final SimpleElasticsearchRouteProperties properties;

    /**
     * 限制 endpoint 片段，避免索引表达式注入路径或查询参数。
     */
    public static void requireIndex(String index, boolean allowWildcard) {
        if (index == null || index.length() > MAX_INDEX_LENGTH || index.startsWith(INDEX_DISALLOWED_DASH_PREFIX) || index.startsWith(INDEX_DISALLOWED_UNDERSCORE_PREFIX) || !index.matches(REGEX_INDEX)
                || DOT.equals(index) || PARENT_DOT.equals(index)
                || (!allowWildcard && wildcard(index))) throw invalid();
    }

    /**
     * 判断是否为通配目标。
     */
    public static boolean wildcard(String index) {
        return index != null && (index.contains(WILDCARD_STAR) || index.contains(WILDCARD_SINGLE));
    }

    private static String literalPrefix(String index) {
        if (index == null) return EMPTY;
        int end = index.length();
        for (char ch : new char[]{CHAR_WILDCARD_STAR, CHAR_WILDCARD_SINGLE}) {
            int at = index.indexOf(ch);
            if (at >= 0) end = Math.min(end, at);
        }
        return index.substring(0, end);
    }

    private static boolean wildcardMatches(String pattern, String value) {
        StringBuilder regex = new StringBuilder();
        for (char ch : pattern.toCharArray()) {
            if (ch == CHAR_WILDCARD_STAR) regex.append(REGEX_MATCH_ANY);
            else if (ch == CHAR_WILDCARD_SINGLE) regex.append(CHAR_DOT);
            else regex.append(java.util.regex.Pattern.quote(String.valueOf(ch)));
        }
        return value.matches(regex.toString());
    }

    /**
     * 参数拒绝使用固定信息，避免将业务索引和请求正文写入异常。
     */
    public static PersistenceExecutionException invalid() {
        return new PersistenceExecutionException(ErrorCode.REQUEST_VALIDATION_FAILED,
                ErrorMessage.REQUEST_VALIDATION_FAILED);
    }

    /**
     * 验证 task 显式数据源，不在集群间广播或回退。
     */
    public boolean hasDatasource(String key) {
        return key != null && properties.getSources().containsKey(key);
    }

    /**
     * index/create 按逻辑索引渲染，并核对物理目标的数据源。
     */
    public String writeIndex(String raw) {
        requireIndex(raw, false);
        String rendered = writes.resolveWriteIndex(raw);
        requireIndex(rendered, false);
        datasource(raw, rendered);
        return rendered;
    }

    /**
     * update/delete 只能操作调用方已定位的具体索引。
     */
    public String physicalIndex(String raw) {
        requireIndex(raw, false);
        SimpleElasticsearchRouteProperties.RouteRule rule = routes.resolveRule(raw);
        if (rule != null && rule.getEffectiveWriteIndexTemplate() != null) {
            String pattern = rule.getEffectiveReadIndexPattern();
            // 日期模板规则若覆盖物理索引，读模式必须能证明其属于历史分片族。
            if (pattern == null || !wildcardMatches(pattern, raw)
                    || raw.equals(rule.getPattern())) throw invalid();
        }
        return raw;
    }

    /**
     * 对全部索引确定唯一数据源，禁止 bulk 或 by-query 被拆成多集群操作。
     */
    public String datasource(String... indices) {
        if (indices == null || indices.length == 0) throw invalid();
        for (String index : indices) requireIndex(index, true);
        String key;
        try {
            key = routes.resolveDataSourceOrThrow(indices);
        } catch (RuntimeException error) {
            throw invalid();
        }
        if (!properties.getSources().containsKey(key)) throw invalid();
        // 通配符只能在单数据源或明确且无其他数据源规则干扰的范围内使用。
        if (properties.getSources().size() > 1) {
            for (String index : indices) {
                if (wildcard(index)) {
                    String prefix = literalPrefix(index);
                    // 多数据源只接受单个结尾星号；任意 glob/regex 的集合包含不能靠抽样证明。
                    if (prefix.isEmpty() || !index.equals(prefix + WILDCARD_STAR)) throw invalid();
                    for (SimpleElasticsearchRouteProperties.RouteRule rule : properties.getRules()) {
                        if (!rule.isEnable() || key.equals(rule.getDatasource())) continue;
                        String type = rule.getType();
                        if (!Arrays.asList(VALUE_EXACT, VALUE_PREFIX, VALUE_WILDCARD).contains(type)) throw invalid();
                        String other = literalPrefix(rule.getPattern());
                        if (prefix.startsWith(other) || other.startsWith(prefix)) throw invalid();
                    }
                    boolean covered = false;
                    for (SimpleElasticsearchRouteProperties.RouteRule owner : properties.getRules()) {
                        if (!owner.isEnable() || !key.equals(owner.getDatasource())) continue;
                        String rulePrefix = literalPrefix(owner.getPattern());
                        boolean prefixRule = VALUE_PREFIX.equals(owner.getType());
                        boolean trailingGlob = VALUE_WILDCARD.equals(owner.getType()) && owner.getPattern().equals(rulePrefix + WILDCARD_STAR);
                        if ((prefixRule || trailingGlob) && prefix.startsWith(rulePrefix)) covered = true;
                    }
                    if (!covered) throw invalid();
                }
            }
        }
        return key;
    }
}
