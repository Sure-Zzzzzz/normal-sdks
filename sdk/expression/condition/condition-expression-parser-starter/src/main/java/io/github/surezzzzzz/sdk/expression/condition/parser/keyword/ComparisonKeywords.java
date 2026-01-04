package io.github.surezzzzzz.sdk.expression.condition.parser.keyword;

import io.github.surezzzzzz.sdk.expression.condition.parser.annotation.ConditionExpressionParserComponent;
import io.github.surezzzzzz.sdk.expression.condition.parser.configuration.ConditionExpressionParserProperties;
import io.github.surezzzzzz.sdk.expression.condition.parser.constant.ComparisonOperator;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * 比较运算符关键字映射
 * 提供默认的关键字到运算符的映射，支持用户自定义扩展
 *
 * @author surezzzzzz
 */
@Slf4j
@ConditionExpressionParserComponent
public class ComparisonKeywords {

    private static final Map<String, ComparisonOperator> DEFAULT_KEYWORDS = new HashMap<>();

    static {
        // 等于
        register("=", ComparisonOperator.EQ);
        register("==", ComparisonOperator.EQ);
        register("等于", ComparisonOperator.EQ);

        // 不等于
        register("!=", ComparisonOperator.NE);
        register("<>", ComparisonOperator.NE);
        register("不等于", ComparisonOperator.NE);

        // 大于
        register(">", ComparisonOperator.GT);
        register("大于", ComparisonOperator.GT);
        register("晚于", ComparisonOperator.GT);

        // 大于等于
        register(">=", ComparisonOperator.GTE);
        register("大于等于", ComparisonOperator.GTE);
        register("不小于", ComparisonOperator.GTE);

        // 小于
        register("<", ComparisonOperator.LT);
        register("小于", ComparisonOperator.LT);
        register("早于", ComparisonOperator.LT);

        // 小于等于
        register("<=", ComparisonOperator.LTE);
        register("小于等于", ComparisonOperator.LTE);
        register("不大于", ComparisonOperator.LTE);
    }

    private static void register(String keyword, ComparisonOperator operator) {
        DEFAULT_KEYWORDS.put(keyword, operator);
    }

    private final Map<String, ComparisonOperator> keywordMap;

    public ComparisonKeywords(ConditionExpressionParserProperties properties) {
        // 复制默认映射
        this.keywordMap = new HashMap<>(DEFAULT_KEYWORDS);

        // Merge 用户自定义映射
        if (properties.getCustomComparisonOperators() != null) {
            properties.getCustomComparisonOperators().forEach((keyword, operatorName) -> {
                try {
                    ComparisonOperator operator = ComparisonOperator.valueOf(operatorName);
                    keywordMap.put(keyword, operator);
                    log.info("注册自定义比较运算符: {} -> {}", keyword, operator);
                } catch (IllegalArgumentException e) {
                    log.warn("无效的比较运算符配置: {} -> {}，已忽略", keyword, operatorName);
                }
            });
        }
    }

    /**
     * 根据关键字获取比较运算符
     *
     * @param keyword 关键字
     * @return 比较运算符，如果不存在则返回 null
     */
    public ComparisonOperator fromKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        return keywordMap.get(keyword);
    }

    /**
     * 判断是否为比较运算符关键字
     *
     * @param keyword 关键字
     * @return true 如果是比较运算符关键字
     */
    public boolean isKeyword(String keyword) {
        return keyword != null && keywordMap.containsKey(keyword);
    }

    /**
     * 获取所有关键字映射（只读）
     *
     * @return 关键字映射的副本
     */
    public Map<String, ComparisonOperator> getAllKeywords() {
        return new HashMap<>(keywordMap);
    }
}
