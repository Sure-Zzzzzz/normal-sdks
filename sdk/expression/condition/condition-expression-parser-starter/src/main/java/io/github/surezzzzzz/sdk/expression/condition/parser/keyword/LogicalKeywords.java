package io.github.surezzzzzz.sdk.expression.condition.parser.keyword;

import io.github.surezzzzzz.sdk.expression.condition.parser.annotation.ConditionExpressionParserComponent;
import io.github.surezzzzzz.sdk.expression.condition.parser.configuration.ConditionExpressionParserProperties;
import io.github.surezzzzzz.sdk.expression.condition.parser.constant.LogicalOperator;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * 逻辑运算符关键字映射
 * 提供默认的关键字到运算符的映射，支持用户自定义扩展
 *
 * @author surezzzzzz
 */
@Slf4j
@ConditionExpressionParserComponent
public class LogicalKeywords {

    private static final Map<String, LogicalOperator> DEFAULT_KEYWORDS = new HashMap<>();

    static {
        // AND
        register("AND", LogicalOperator.AND);
        register("and", LogicalOperator.AND);
        register("且", LogicalOperator.AND);
        register("并且", LogicalOperator.AND);

        // OR
        register("OR", LogicalOperator.OR);
        register("or", LogicalOperator.OR);
        register("或", LogicalOperator.OR);
        register("或者", LogicalOperator.OR);
    }

    private static void register(String keyword, LogicalOperator operator) {
        DEFAULT_KEYWORDS.put(keyword, operator);
    }

    private final Map<String, LogicalOperator> keywordMap;

    public LogicalKeywords(ConditionExpressionParserProperties properties) {
        // 复制默认映射
        this.keywordMap = new HashMap<>(DEFAULT_KEYWORDS);

        // Merge 用户自定义映射
        if (properties.getCustomLogicalOperators() != null) {
            properties.getCustomLogicalOperators().forEach((keyword, operatorName) -> {
                try {
                    LogicalOperator operator = LogicalOperator.valueOf(operatorName);
                    keywordMap.put(keyword, operator);
                    log.info("注册自定义逻辑运算符: {} -> {}", keyword, operator);
                } catch (IllegalArgumentException e) {
                    log.warn("无效的逻辑运算符配置: {} -> {}，已忽略", keyword, operatorName);
                }
            });
        }
    }

    /**
     * 根据关键字获取逻辑运算符
     *
     * @param keyword 关键字
     * @return 逻辑运算符，如果不存在则返回 null
     */
    public LogicalOperator fromKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        return keywordMap.get(keyword);
    }

    /**
     * 判断是否为逻辑运算符关键字
     *
     * @param keyword 关键字
     * @return true 如果是逻辑运算符关键字
     */
    public boolean isKeyword(String keyword) {
        return keyword != null && keywordMap.containsKey(keyword);
    }

    /**
     * 获取所有关键字映射（只读）
     *
     * @return 关键字映射的副本
     */
    public Map<String, LogicalOperator> getAllKeywords() {
        return new HashMap<>(keywordMap);
    }
}
