package io.github.surezzzzzz.sdk.expression.condition.parser.keyword;

import io.github.surezzzzzz.sdk.expression.condition.parser.annotation.ConditionExpressionParserComponent;
import io.github.surezzzzzz.sdk.expression.condition.parser.configuration.ConditionExpressionParserProperties;
import io.github.surezzzzzz.sdk.expression.condition.parser.constant.MatchOperator;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * 匹配运算符关键字映射
 * 提供默认的关键字到运算符的映射，支持用户自定义扩展
 *
 * @author surezzzzzz
 */
@Slf4j
@ConditionExpressionParserComponent
public class MatchKeywords {

    private static final Map<String, MatchOperator> DEFAULT_KEYWORDS = new HashMap<>();

    static {
        // LIKE
        register("LIKE", MatchOperator.LIKE);
        register("like", MatchOperator.LIKE);
        register("模糊匹配", MatchOperator.LIKE);
        register("包含", MatchOperator.LIKE);

        // PREFIX
        register("PREFIX", MatchOperator.PREFIX);
        register("prefix", MatchOperator.PREFIX);
        register("前缀", MatchOperator.PREFIX);
        register("前缀匹配", MatchOperator.PREFIX);

        // SUFFIX
        register("SUFFIX", MatchOperator.SUFFIX);
        register("suffix", MatchOperator.SUFFIX);
        register("后缀", MatchOperator.SUFFIX);
        register("后缀匹配", MatchOperator.SUFFIX);

        // NOT LIKE
        register("NOT LIKE", MatchOperator.NOT_LIKE);
        register("not like", MatchOperator.NOT_LIKE);
        register("不包含", MatchOperator.NOT_LIKE);
        register("不匹配", MatchOperator.NOT_LIKE);
    }

    private static void register(String keyword, MatchOperator operator) {
        DEFAULT_KEYWORDS.put(keyword, operator);
    }

    private final Map<String, MatchOperator> keywordMap;

    public MatchKeywords(ConditionExpressionParserProperties properties) {
        // 复制默认映射
        this.keywordMap = new HashMap<>(DEFAULT_KEYWORDS);

        // Merge 用户自定义映射
        if (properties.getCustomMatchOperators() != null) {
            properties.getCustomMatchOperators().forEach((keyword, operatorName) -> {
                try {
                    MatchOperator operator = MatchOperator.valueOf(operatorName);
                    keywordMap.put(keyword, operator);
                    log.info("注册自定义匹配运算符: {} -> {}", keyword, operator);
                } catch (IllegalArgumentException e) {
                    log.warn("无效的匹配运算符配置: {} -> {}，已忽略", keyword, operatorName);
                }
            });
        }
    }

    /**
     * 根据关键字获取匹配运算符
     *
     * @param keyword 关键字
     * @return 匹配运算符，如果不存在则返回 null
     */
    public MatchOperator fromKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        return keywordMap.get(keyword);
    }

    /**
     * 判断是否为匹配运算符关键字
     *
     * @param keyword 关键字
     * @return true 如果是匹配运算符关键字
     */
    public boolean isKeyword(String keyword) {
        return keyword != null && keywordMap.containsKey(keyword);
    }

    /**
     * 获取所有关键字映射（只读）
     *
     * @return 关键字映射的副本
     */
    public Map<String, MatchOperator> getAllKeywords() {
        return new HashMap<>(keywordMap);
    }
}
