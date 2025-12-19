package io.github.surezzzzzz.sdk.elasticsearch.search.constant;

/**
 * 敏感字段处理策略枚举
 *
 * @author surezzzzzz
 */
public enum SensitiveStrategy {

    /**
     * 禁止访问（查询时返回错误，结果中不返回）
     */
    FORBIDDEN("forbidden", "禁止访问"),

    /**
     * 脱敏返回（可查询，但返回时进行脱敏处理）
     */
    MASK("mask", "脱敏返回");

    private final String strategy;
    private final String description;

    SensitiveStrategy(String strategy, String description) {
        this.strategy = strategy;
        this.description = description;
    }

    public String getStrategy() {
        return strategy;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据字符串获取枚举值
     */
    public static SensitiveStrategy fromString(String strategy) {
        if (strategy == null) {
            return null;
        }
        for (SensitiveStrategy s : values()) {
            if (s.strategy.equalsIgnoreCase(strategy)) {
                return s;
            }
        }
        throw new IllegalArgumentException("不支持的敏感字段策略: " + strategy);
    }
}
