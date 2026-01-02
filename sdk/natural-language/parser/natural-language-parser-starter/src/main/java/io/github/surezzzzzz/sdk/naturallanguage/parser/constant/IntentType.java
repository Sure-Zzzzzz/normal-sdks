package io.github.surezzzzzz.sdk.naturallanguage.parser.constant;

/**
 * 意图类型
 *
 * @author surezzzzzz
 */
public enum IntentType {

    /**
     * 查询意图
     */
    QUERY("查询"),

    /**
     * 更新意图
     */
    UPDATE("更新"),

    /**
     * 删除意图
     */
    DELETE("删除"),

    /**
     * 插入意图
     */
    INSERT("插入"),

    /**
     * 分析/聚合意图
     */
    ANALYTICS("分析");

    private final String description;

    IntentType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
