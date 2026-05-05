package io.github.surezzzzzz.sdk.naturallanguage.parser.keyword;

import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.*;

import java.util.Set;

/**
 * 关键字注册表接口
 *
 * @author surezzzzzz
 */
public interface KeywordRegistry {

    // ==================== 操作符关键字 ====================

    /**
     * 添加操作符关键字
     *
     * @param type     操作符类型
     * @param keywords 关键字
     */
    void addOperatorKeywords(OperatorType type, String... keywords);

    /**
     * 移除操作符关键字
     *
     * @param type     操作符类型
     * @param keywords 关键字
     */
    void removeOperatorKeywords(OperatorType type, String... keywords);

    /**
     * 获取操作符关键字
     *
     * @param type 操作符类型
     * @return 关键字集合
     */
    Set<String> getOperatorKeywords(OperatorType type);

    // ==================== 聚合关键字 ====================

    void addAggKeywords(AggType type, String... keywords);

    void removeAggKeywords(AggType type, String... keywords);

    Set<String> getAggKeywords(AggType type);

    // ==================== 折叠关键字 ====================

    void addCollapseKeywords(String... keywords);

    void removeCollapseKeywords(String... keywords);

    Set<String> getCollapseKeywords();

    // ==================== 时间范围关键字 ====================

    void addTimeRangeKeywords(TimeRange range, String... keywords);

    void removeTimeRangeKeywords(TimeRange range, String... keywords);

    Set<String> getTimeRangeKeywords(TimeRange range);

    // ==================== 逻辑关键字 ====================

    void addLogicKeywords(LogicType type, String... keywords);

    void removeLogicKeywords(LogicType type, String... keywords);

    Set<String> getLogicKeywords(LogicType type);

    // ==================== 排序关键字 ====================

    void addSortKeywords(SortOrder order, String... keywords);

    void removeSortKeywords(SortOrder order, String... keywords);

    Set<String> getSortKeywords(SortOrder order);

    // ==================== 分页关键字 ====================

    void addPaginationKeywords(String... keywords);

    void removePaginationKeywords(String... keywords);

    Set<String> getPaginationKeywords();

    // ==================== 索引指示词 ====================

    void addIndexIndicators(String... keywords);

    void removeIndexIndicators(String... keywords);

    Set<String> getIndexIndicators();

    boolean isIndexIndicator(String word);

    // ==================== 范围关键字 ====================

    void addRangeKeywords(String... keywords);

    void removeRangeKeywords(String... keywords);

    Set<String> getRangeKeywords();

    boolean isRangeKeyword(String word);

    // ==================== 介词 ====================

    void addPrepositions(String... words);

    void removePrepositions(String... words);

    Set<String> getPrepositions();

    // ==================== 停用词 ====================

    void addStopWords(String... words);

    void removeStopWords(String... words);

    Set<String> getStopWords();

    // ==================== 查询方法 ====================

    /**
     * 解析操作符关键字
     *
     * @param keyword 关键字
     * @return 操作符类型，找不到返回 null
     */
    OperatorType resolveOperator(String keyword);

    /**
     * 解析聚合类型关键字
     *
     * @param keyword 关键字
     * @return 聚合类型，找不到返回 null
     */
    AggType resolveAggType(String keyword);

    /**
     * 是否为折叠关键字
     *
     * @param keyword 关键字
     * @return true 是，false 否
     */
    boolean isCollapseKeyword(String keyword);

    /**
     * 解析时间范围关键字
     *
     * @param keyword 关键字
     * @return 时间范围，找不到返回 null
     */
    TimeRange resolveTimeRange(String keyword);

    /**
     * 解析逻辑关键字
     *
     * @param keyword 关键字
     * @return 逻辑类型，找不到返回 null
     */
    LogicType resolveLogic(String keyword);

    /**
     * 解析排序关键字
     *
     * @param keyword 关键字
     * @return 排序方向，找不到返回 null
     */
    SortOrder resolveSortOrder(String keyword);

    /**
     * 是否为分页关键字
     *
     * @param keyword 关键字
     * @return true 是，false 否
     */
    boolean isPaginationKeyword(String keyword);

    /**
     * 是否为介词
     *
     * @param word 词
     * @return true 是，false 否
     */
    boolean isPreposition(String word);

    /**
     * 是否为停用词
     *
     * @param word 词
     * @return true 是，false 否
     */
    boolean isStopWord(String word);
}
