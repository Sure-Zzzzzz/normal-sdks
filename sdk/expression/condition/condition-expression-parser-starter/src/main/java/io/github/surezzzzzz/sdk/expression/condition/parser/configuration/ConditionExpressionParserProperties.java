package io.github.surezzzzzz.sdk.expression.condition.parser.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * 条件表达式解析器配置类
 * 提供完整的默认配置，支持用户自定义扩展
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(prefix = "io.github.surezzzzzz.sdk.expression.condition.parser")
public class ConditionExpressionParserProperties {

    /**
     * 是否启用条件表达式解析器
     */
    private boolean enabled = true;

    /**
     * 自定义比较运算符映射（会 merge 到默认映射中）
     * <p>
     * 默认已支持：=, ==, !=, <>, >, <, >=, <=, 等于, 不等于, 大于, 小于, 晚于, 早于 等
     * <p>
     * 示例配置：
     * <pre>
     * custom-comparison-operators:
     *   "等同于": "EQ"
     *   "大过": "GT"
     * </pre>
     */
    private Map<String, String> customComparisonOperators = new HashMap<>();

    /**
     * 自定义逻辑运算符映射（会 merge 到默认映射中）
     * <p>
     * 默认已支持：AND, OR, and, or, 且, 或, 并且, 或者
     * <p>
     * 示例配置：
     * <pre>
     * custom-logical-operators:
     *   "而且": "AND"
     *   "要么": "OR"
     * </pre>
     */
    private Map<String, String> customLogicalOperators = new HashMap<>();

    /**
     * 自定义时间范围表达式映射（会 merge 到默认映射中）
     * <p>
     * 默认已支持：近1个月, 近三个月, 近半年, 一个月, 三个月, 半年, 一年 等
     * <p>
     * 示例配置：
     * <pre>
     * custom-time-ranges:
     *   "最近一月": "LAST_1_MONTH"
     *   "最近一季": "LAST_3_MONTHS"
     * </pre>
     */
    private Map<String, String> customTimeRanges = new HashMap<>();

    /**
     * 自定义匹配运算符映射（会 merge 到默认映射中）
     * <p>
     * 默认已支持：LIKE, PREFIX, SUFFIX, NOT LIKE, 模糊匹配, 前缀, 后缀, 前缀匹配, 后缀匹配, 包含, 不包含 等
     * <p>
     * 示例配置：
     * <pre>
     * custom-match-operators:
     *   "相似": "LIKE"
     *   "开头是": "PREFIX"
     * </pre>
     */
    private Map<String, String> customMatchOperators = new HashMap<>();
}
