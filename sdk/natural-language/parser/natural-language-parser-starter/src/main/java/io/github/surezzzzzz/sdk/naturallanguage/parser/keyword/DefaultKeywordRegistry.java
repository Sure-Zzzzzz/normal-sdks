package io.github.surezzzzzz.sdk.naturallanguage.parser.keyword;

import io.github.surezzzzzz.sdk.naturallanguage.parser.configuration.NLParserProperties;
import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.*;
import io.github.surezzzzzz.sdk.naturallanguage.parser.exception.NLKeywordConflictException;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 默认关键字注册表实现
 * 内置默认关键字 + YAML 扩展 + KeywordContributor Bean 扩展
 *
 * @author surezzzzzz
 */
@Slf4j
public class DefaultKeywordRegistry implements KeywordRegistry {

    // ==================== 正向映射 ====================

    private final Map<OperatorType, Set<String>> operatorKeywords = new EnumMap<>(OperatorType.class);
    private final Map<AggType, Set<String>> aggKeywords = new EnumMap<>(AggType.class);
    private final Set<String> collapseKeywords = new LinkedHashSet<>();
    private final Map<TimeRange, Set<String>> timeRangeKeywords = new EnumMap<>(TimeRange.class);
    private final Map<LogicType, Set<String>> logicKeywords = new EnumMap<>(LogicType.class);
    private final Map<SortOrder, Set<String>> sortKeywords = new EnumMap<>(SortOrder.class);
    private final Set<String> paginationKeywords = new LinkedHashSet<>();
    private final Set<String> indexIndicators = new LinkedHashSet<>();
    private final Set<String> rangeKeywords = new LinkedHashSet<>();
    private final Set<String> prepositions = new LinkedHashSet<>();
    private final Set<String> stopWords = new LinkedHashSet<>();

    /**
     * 反向映射：关键字 → 分类信息（用于快速 resolve 和冲突检测）
     */
    private final Map<String, String> keywordCategory = new LinkedHashMap<>();

    private boolean frozen = false;

    public DefaultKeywordRegistry() {
        loadDefaults();
    }

    // ==================== 加载内置默认关键字 ====================

    private void loadDefaults() {
        // 操作符
        addOperatorKeywords(OperatorType.EQ, "等于", "是", "=", "==", "eq");
        addOperatorKeywords(OperatorType.NE, "不等于", "不是", "!=", "≠", "ne");
        addOperatorKeywords(OperatorType.GT, "大于", ">", "gt");
        addOperatorKeywords(OperatorType.GTE, "大于等于", "不小于", ">=", "≥", "gte");
        addOperatorKeywords(OperatorType.LT, "小于", "<", "lt");
        addOperatorKeywords(OperatorType.LTE, "小于等于", "不大于", "<=", "≤", "lte");
        addOperatorKeywords(OperatorType.IN, "在...中", "in");
        addOperatorKeywords(OperatorType.NOT_IN, "不在...中", "not in");
        addOperatorKeywords(OperatorType.BETWEEN, "在...之间", "介于", "between");
        addOperatorKeywords(OperatorType.LIKE, "包含", "模糊", "like");
        addOperatorKeywords(OperatorType.NOT_LIKE, "不包含", "not_like");
        addOperatorKeywords(OperatorType.PREFIX, "开头是", "前缀", "prefix");
        addOperatorKeywords(OperatorType.SUFFIX, "结尾是", "后缀", "suffix");
        addOperatorKeywords(OperatorType.REGEX, "正则", "regex");
        addOperatorKeywords(OperatorType.EXISTS, "存在", "有", "exists");
        addOperatorKeywords(OperatorType.NOT_EXISTS, "不存在", "没有", "not exists");
        addOperatorKeywords(OperatorType.IS_NULL, "为空", "is null");
        addOperatorKeywords(OperatorType.IS_NOT_NULL, "不为空", "is not null");

        // 聚合
        addAggKeywords(AggType.AVG, "平均", "平均值", "avg");
        addAggKeywords(AggType.SUM, "总和", "求和", "sum");
        addAggKeywords(AggType.MIN, "最小", "最小值", "min");
        addAggKeywords(AggType.MAX, "最大", "最大值", "max");
        addAggKeywords(AggType.COUNT, "计数", "数量", "count", "统计");
        addAggKeywords(AggType.CARDINALITY, "去重计数", "唯一值计数", "cardinality");
        addAggKeywords(AggType.STATS, "全面统计", "stats");
        addAggKeywords(AggType.EXTENDED_STATS, "扩展统计", "extended_stats");
        addAggKeywords(AggType.PERCENTILES, "百分位数", "百分位", "percentiles", "P50", "P90", "P95", "P99");
        addAggKeywords(AggType.PERCENTILE_RANKS, "百分位排名", "达标率", "percentile_ranks");
        addAggKeywords(AggType.TERMS, "分组", "分类", "terms");
        addAggKeywords(AggType.DATE_HISTOGRAM, "按日期分组", "日期直方图", "date_histogram");
        addAggKeywords(AggType.HISTOGRAM, "按数值分组", "数值直方图", "histogram");
        addAggKeywords(AggType.RANGE, "按范围分组", "range");
        addAggKeywords(AggType.DATE_RANGE, "按日期范围分组", "date_range");
        addAggKeywords(AggType.IP_RANGE, "按IP范围分组", "ip_range");
        addAggKeywords(AggType.FILTER, "过滤器", "filter");
        addAggKeywords(AggType.FILTERS, "多过滤器", "filters");
        addAggKeywords(AggType.MISSING, "缺失值", "空值统计", "missing");
        addAggKeywords(AggType.BUCKET_SORT, "桶排序", "Top N", "bucket_sort");
        addAggKeywords(AggType.BUCKET_SELECTOR, "桶选择", "HAVING", "bucket_selector");

        // 折叠
        addCollapseKeywords("去重", "消重", "折叠", "唯一", "不重复", "collapse", "distinct", "unique", "deduplicate", "dedup");

        // 时间范围
        addTimeRangeKeywords(TimeRange.LAST_5_MINUTES, "近5分钟", "过去5分钟");
        addTimeRangeKeywords(TimeRange.LAST_HOUR, "近1小时", "过去1小时");
        addTimeRangeKeywords(TimeRange.LAST_24_HOURS, "近24小时", "最近一天");
        addTimeRangeKeywords(TimeRange.LAST_7_DAYS, "近7天", "过去7天", "最近一周");
        addTimeRangeKeywords(TimeRange.LAST_30_DAYS, "近30天", "最近一月");
        addTimeRangeKeywords(TimeRange.LAST_3_MONTHS, "近3个月");
        addTimeRangeKeywords(TimeRange.LAST_YEAR, "近一年");
        addTimeRangeKeywords(TimeRange.TODAY, "今天");
        addTimeRangeKeywords(TimeRange.YESTERDAY, "昨天");
        addTimeRangeKeywords(TimeRange.THIS_WEEK, "本周");
        addTimeRangeKeywords(TimeRange.THIS_MONTH, "本月");

        // 逻辑
        addLogicKeywords(LogicType.AND, "并且", "同时", "而且", "且", "AND", "and", "&&");
        addLogicKeywords(LogicType.OR, "或者", "或", "还是", "OR", "or", "||");
        addLogicKeywords(LogicType.NOT, "非", "不", "排除", "NOT", "not", "!");

        // 排序
        addSortKeywords(SortOrder.ASC, "升序", "从小到大", "递增", "ASC", "asc");
        addSortKeywords(SortOrder.DESC, "降序", "从大到小", "递减", "最新", "DESC", "desc");

        // 分页
        addPaginationKeywords("限制", "最多", "前", "返回", "取", "limit", "page", "size", "top", "first");

        // 索引指示词
        addIndexIndicators("索引", "表", "index", "table", "collection");

        // 范围连接词
        addRangeKeywords("到", "至", "到第", "至第", "~", "-");

        // 介词
        addPrepositions("按", "对", "根据", "把");

        // 停用词（仅添加不影响分词结构的语气词/助词）
        addStopWords("的", "了", "吗", "呢", "吧", "啊", "嗯", "一下", "就行", "数据");
    }

    // ==================== 应用 YAML 配置 ====================

    /**
     * 应用 YAML 配置
     *
     * @param config 关键字配置
     */
    public void applyConfig(NLParserProperties.KeywordsConfig config) {
        checkFrozen();
        if (config == null) {
            return;
        }

        if (config.getOperators() != null) {
            for (Map.Entry<String, List<String>> entry : config.getOperators().entrySet()) {
                OperatorType type = OperatorType.fromCode(entry.getKey());
                if (type == null) {
                    throw new NLKeywordConflictException(ErrorCode.KEYWORD_INVALID_ENUM,
                            String.format(ErrorMessage.KEYWORD_INVALID_ENUM, entry.getKey(),
                                    Arrays.toString(OperatorType.getAllCodes())));
                }
                for (String keyword : entry.getValue()) {
                    addOperatorKeywords(type, keyword);
                }
            }
        }

        if (config.getAggregations() != null) {
            for (Map.Entry<String, List<String>> entry : config.getAggregations().entrySet()) {
                AggType type = AggType.fromCode(entry.getKey());
                if (type == null) {
                    throw new NLKeywordConflictException(ErrorCode.KEYWORD_INVALID_ENUM,
                            String.format(ErrorMessage.KEYWORD_INVALID_ENUM, entry.getKey(),
                                    Arrays.toString(AggType.getAllCodes())));
                }
                for (String keyword : entry.getValue()) {
                    addAggKeywords(type, keyword);
                }
            }
        }

        if (config.getCollapse() != null) {
            for (String keyword : config.getCollapse()) {
                addCollapseKeywords(keyword);
            }
        }

        if (config.getTimeRanges() != null) {
            for (Map.Entry<String, List<String>> entry : config.getTimeRanges().entrySet()) {
                TimeRange range = TimeRange.fromCode(entry.getKey());
                if (range == null) {
                    throw new NLKeywordConflictException(ErrorCode.KEYWORD_INVALID_ENUM,
                            String.format(ErrorMessage.KEYWORD_INVALID_ENUM, entry.getKey(),
                                    Arrays.toString(TimeRange.getAllCodes())));
                }
                for (String keyword : entry.getValue()) {
                    addTimeRangeKeywords(range, keyword);
                }
            }
        }

        if (config.getLogic() != null) {
            for (Map.Entry<String, List<String>> entry : config.getLogic().entrySet()) {
                LogicType type = LogicType.fromCode(entry.getKey());
                if (type == null) {
                    throw new NLKeywordConflictException(ErrorCode.KEYWORD_INVALID_ENUM,
                            String.format(ErrorMessage.KEYWORD_INVALID_ENUM, entry.getKey(),
                                    Arrays.toString(LogicType.getAllCodes())));
                }
                for (String keyword : entry.getValue()) {
                    addLogicKeywords(type, keyword);
                }
            }
        }

        if (config.getSort() != null) {
            for (Map.Entry<String, List<String>> entry : config.getSort().entrySet()) {
                SortOrder order = SortOrder.fromCode(entry.getKey());
                if (order == null) {
                    throw new NLKeywordConflictException(ErrorCode.KEYWORD_INVALID_ENUM,
                            String.format(ErrorMessage.KEYWORD_INVALID_ENUM, entry.getKey(),
                                    Arrays.toString(SortOrder.getAllCodes())));
                }
                for (String keyword : entry.getValue()) {
                    addSortKeywords(order, keyword);
                }
            }
        }

        if (config.getPagination() != null) {
            for (String keyword : config.getPagination()) {
                addPaginationKeywords(keyword);
            }
        }

        if (config.getPrepositions() != null) {
            for (String word : config.getPrepositions()) {
                addPrepositions(word);
            }
        }

        if (config.getStopWords() != null) {
            for (String word : config.getStopWords()) {
                addStopWords(word);
            }
        }
    }

    // ==================== 冻结和校验 ====================

    /**
     * 冻结注册表并执行启动校验
     */
    public void validateAndFreeze() {
        checkFrozen();
        frozen = true;
        log.info("KeywordRegistry 已冻结，共注册 {} 个关键字", keywordCategory.size());
    }

    private void checkFrozen() {
        if (frozen) {
            throw new IllegalStateException("KeywordRegistry 已冻结，不允许修改");
        }
    }

    // ==================== 冲突检测 ====================

    private void checkConflict(String keyword, String newCategory) {
        if (keyword == null || keyword.isEmpty()) {
            throw new NLKeywordConflictException(ErrorCode.KEYWORD_EMPTY, ErrorMessage.KEYWORD_EMPTY);
        }
        String normalized = keyword.toLowerCase();
        String existing = keywordCategory.get(normalized);
        if (existing != null && !existing.equals(newCategory)) {
            throw new NLKeywordConflictException(ErrorCode.KEYWORD_CONFLICT,
                    String.format(ErrorMessage.KEYWORD_CONFLICT, keyword, existing, newCategory,
                            "DefaultKeywordRegistry"));
        }
    }

    private void registerKeyword(String keyword, String category) {
        keywordCategory.put(keyword.toLowerCase(), category);
    }

    private void unregisterKeyword(String keyword) {
        if (keyword != null) {
            keywordCategory.remove(keyword.trim().toLowerCase());
        }
    }

    // ==================== 通用增删辅助方法 ====================

    /**
     * 向 EnumMap 中添加关键字（含冲突检测和反向索引注册）
     */
    private <T> void addToMap(Map<T, Set<String>> map, T type, String category, String... keywords) {
        checkFrozen();
        map.computeIfAbsent(type, k -> new LinkedHashSet<>());
        for (String keyword : keywords) {
            String trimmed = keyword.trim();
            checkConflict(trimmed, category);
            map.get(type).add(trimmed);
            registerKeyword(trimmed, category);
        }
    }

    /**
     * 从 EnumMap 中移除关键字
     */
    private <T> void removeFromMap(Map<T, Set<String>> map, T type, String... keywords) {
        checkFrozen();
        Set<String> set = map.get(type);
        if (set == null) return;
        for (String keyword : keywords) {
            set.remove(keyword.trim());
            unregisterKeyword(keyword);
        }
    }

    /**
     * 向 Set 中添加关键字（含冲突检测和反向索引注册）
     */
    private void addToSet(Set<String> set, String category, String... keywords) {
        checkFrozen();
        for (String keyword : keywords) {
            String trimmed = keyword.trim();
            checkConflict(trimmed, category);
            set.add(trimmed);
            registerKeyword(trimmed, category);
        }
    }

    /**
     * 从 Set 中移除关键字
     */
    private void removeFromSet(Set<String> set, String... keywords) {
        checkFrozen();
        for (String keyword : keywords) {
            set.remove(keyword.trim());
            unregisterKeyword(keyword);
        }
    }

    // ==================== 操作符关键字 ====================

    @Override
    public void addOperatorKeywords(OperatorType type, String... keywords) {
        addToMap(operatorKeywords, type, "OPERATOR:" + type.getCode(), keywords);
    }

    @Override
    public void removeOperatorKeywords(OperatorType type, String... keywords) {
        removeFromMap(operatorKeywords, type, keywords);
    }

    @Override
    public Set<String> getOperatorKeywords(OperatorType type) {
        Set<String> set = operatorKeywords.get(type);
        return set != null ? Collections.unmodifiableSet(set) : Collections.<String>emptySet();
    }

    // ==================== 聚合关键字 ====================

    @Override
    public void addAggKeywords(AggType type, String... keywords) {
        addToMap(aggKeywords, type, "AGG:" + type.getCode(), keywords);
    }

    @Override
    public void removeAggKeywords(AggType type, String... keywords) {
        removeFromMap(aggKeywords, type, keywords);
    }

    @Override
    public Set<String> getAggKeywords(AggType type) {
        Set<String> set = aggKeywords.get(type);
        return set != null ? Collections.unmodifiableSet(set) : Collections.<String>emptySet();
    }

    // ==================== 折叠关键字 ====================

    @Override
    public void addCollapseKeywords(String... keywords) {
        addToSet(collapseKeywords, "COLLAPSE", keywords);
    }

    @Override
    public void removeCollapseKeywords(String... keywords) {
        removeFromSet(collapseKeywords, keywords);
    }

    @Override
    public Set<String> getCollapseKeywords() {
        return Collections.unmodifiableSet(collapseKeywords);
    }

    // ==================== 时间范围关键字 ====================

    @Override
    public void addTimeRangeKeywords(TimeRange range, String... keywords) {
        addToMap(timeRangeKeywords, range, "TIME_RANGE:" + range.getCode(), keywords);
    }

    @Override
    public void removeTimeRangeKeywords(TimeRange range, String... keywords) {
        removeFromMap(timeRangeKeywords, range, keywords);
    }

    @Override
    public Set<String> getTimeRangeKeywords(TimeRange range) {
        Set<String> set = timeRangeKeywords.get(range);
        return set != null ? Collections.unmodifiableSet(set) : Collections.<String>emptySet();
    }

    // ==================== 逻辑关键字 ====================

    @Override
    public void addLogicKeywords(LogicType type, String... keywords) {
        addToMap(logicKeywords, type, "LOGIC:" + type.getCode(), keywords);
    }

    @Override
    public void removeLogicKeywords(LogicType type, String... keywords) {
        removeFromMap(logicKeywords, type, keywords);
    }

    @Override
    public Set<String> getLogicKeywords(LogicType type) {
        Set<String> set = logicKeywords.get(type);
        return set != null ? Collections.unmodifiableSet(set) : Collections.<String>emptySet();
    }

    // ==================== 排序关键字 ====================

    @Override
    public void addSortKeywords(SortOrder order, String... keywords) {
        addToMap(sortKeywords, order, "SORT:" + order.getCode(), keywords);
    }

    @Override
    public void removeSortKeywords(SortOrder order, String... keywords) {
        removeFromMap(sortKeywords, order, keywords);
    }

    @Override
    public Set<String> getSortKeywords(SortOrder order) {
        Set<String> set = sortKeywords.get(order);
        return set != null ? Collections.unmodifiableSet(set) : Collections.<String>emptySet();
    }

    // ==================== 分页关键字 ====================

    @Override
    public void addPaginationKeywords(String... keywords) {
        addToSet(paginationKeywords, "PAGINATION", keywords);
    }

    @Override
    public void removePaginationKeywords(String... keywords) {
        removeFromSet(paginationKeywords, keywords);
    }

    @Override
    public Set<String> getPaginationKeywords() {
        return Collections.unmodifiableSet(paginationKeywords);
    }

    // ==================== 索引指示词 ====================

    @Override
    public void addIndexIndicators(String... keywords) {
        addToSet(indexIndicators, "INDEX_INDICATOR", keywords);
    }

    @Override
    public void removeIndexIndicators(String... keywords) {
        removeFromSet(indexIndicators, keywords);
    }

    @Override
    public Set<String> getIndexIndicators() {
        return Collections.unmodifiableSet(indexIndicators);
    }

    @Override
    public boolean isIndexIndicator(String word) {
        if (word == null) return false;
        return "INDEX_INDICATOR".equals(keywordCategory.get(word.trim().toLowerCase()));
    }

    // ==================== 范围关键字 ====================

    @Override
    public void addRangeKeywords(String... keywords) {
        addToSet(rangeKeywords, "RANGE", keywords);
    }

    @Override
    public void removeRangeKeywords(String... keywords) {
        removeFromSet(rangeKeywords, keywords);
    }

    @Override
    public Set<String> getRangeKeywords() {
        return Collections.unmodifiableSet(rangeKeywords);
    }

    @Override
    public boolean isRangeKeyword(String word) {
        if (word == null) return false;
        return "RANGE".equals(keywordCategory.get(word.trim().toLowerCase()));
    }

    // ==================== 介词 ====================

    @Override
    public void addPrepositions(String... words) {
        addToSet(prepositions, "PREPOSITION", words);
    }

    @Override
    public void removePrepositions(String... words) {
        removeFromSet(prepositions, words);
    }

    @Override
    public Set<String> getPrepositions() {
        return Collections.unmodifiableSet(prepositions);
    }

    // ==================== 停用词 ====================

    @Override
    public void addStopWords(String... words) {
        addToSet(stopWords, "STOP_WORD", words);
    }

    @Override
    public void removeStopWords(String... words) {
        removeFromSet(stopWords, words);
    }

    @Override
    public Set<String> getStopWords() {
        return Collections.unmodifiableSet(stopWords);
    }

    // ==================== 查询方法（O(1) via keywordCategory 反向索引）====================

    @Override
    public OperatorType resolveOperator(String keyword) {
        if (keyword == null) return null;
        String category = keywordCategory.get(keyword.trim().toLowerCase());
        if (category == null || !category.startsWith("OPERATOR:")) return null;
        return OperatorType.fromCode(category.substring("OPERATOR:".length()));
    }

    @Override
    public AggType resolveAggType(String keyword) {
        if (keyword == null) return null;
        String category = keywordCategory.get(keyword.trim().toLowerCase());
        if (category == null || !category.startsWith("AGG:")) return null;
        return AggType.fromCode(category.substring("AGG:".length()));
    }

    @Override
    public boolean isCollapseKeyword(String keyword) {
        if (keyword == null) return false;
        return "COLLAPSE".equals(keywordCategory.get(keyword.trim().toLowerCase()));
    }

    @Override
    public TimeRange resolveTimeRange(String keyword) {
        if (keyword == null) return null;
        String category = keywordCategory.get(keyword.trim().toLowerCase());
        if (category == null || !category.startsWith("TIME_RANGE:")) return null;
        return TimeRange.fromCode(category.substring("TIME_RANGE:".length()));
    }

    @Override
    public LogicType resolveLogic(String keyword) {
        if (keyword == null) return null;
        String category = keywordCategory.get(keyword.trim().toLowerCase());
        if (category == null || !category.startsWith("LOGIC:")) return null;
        return LogicType.fromCode(category.substring("LOGIC:".length()));
    }

    @Override
    public SortOrder resolveSortOrder(String keyword) {
        if (keyword == null) return null;
        String category = keywordCategory.get(keyword.trim().toLowerCase());
        if (category == null || !category.startsWith("SORT:")) return null;
        return SortOrder.fromCode(category.substring("SORT:".length()));
    }

    @Override
    public boolean isPaginationKeyword(String keyword) {
        if (keyword == null) return false;
        return "PAGINATION".equals(keywordCategory.get(keyword.trim().toLowerCase()));
    }

    @Override
    public boolean isPreposition(String word) {
        if (word == null) return false;
        return "PREPOSITION".equals(keywordCategory.get(word.trim().toLowerCase()));
    }

    @Override
    public boolean isStopWord(String word) {
        if (word == null) return false;
        return "STOP_WORD".equals(keywordCategory.get(word.trim().toLowerCase()));
    }
}
