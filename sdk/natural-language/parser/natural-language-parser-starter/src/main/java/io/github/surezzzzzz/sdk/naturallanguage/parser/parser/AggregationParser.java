package io.github.surezzzzzz.sdk.naturallanguage.parser.parser;

import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.AggType;
import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.TokenType;
import io.github.surezzzzzz.sdk.naturallanguage.parser.keyword.AggKeywords;
import io.github.surezzzzzz.sdk.naturallanguage.parser.keyword.SortKeywords;
import io.github.surezzzzzz.sdk.naturallanguage.parser.model.AggregationIntent;
import io.github.surezzzzzz.sdk.naturallanguage.parser.tokenizer.Token;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 聚合解析器
 * <p>
 * 支持的功能：
 * - 简单指标聚合：统计平均年龄
 * - 桶聚合：按城市分组
 * - 嵌套聚合：按城市分组统计平均年龄
 * - 多个并行聚合：按城市分组，同时按创建时间每天统计
 * - 参数：前10个、每天
 * <p>
 * 线程安全：无状态，线程安全
 *
 * @author surezzzzzz
 */
public class AggregationParser {

    // ==================== 常量定义 ====================

    /**
     * 聚合字段向前查找的最大距离
     */
    private static final int MAX_FIELD_LOOKAHEAD_DISTANCE = 5;

    /**
     * 排序模式检测的向前查找距离
     */
    private static final int SORT_PATTERN_LOOKAHEAD = 4;

    // ==================== 公共方法 ====================

    /**
     * 解析聚合表达式
     *
     * @param tokens token列表
     * @return 聚合意图列表，如果没有聚合则返回空列表
     */
    public List<AggregationIntent> parse(List<Token> tokens) {
        // 参数验证
        if (tokens == null || tokens.isEmpty()) {
            return Collections.emptyList();
        }

        // Step 1: 按分隔词分段
        List<AggSegment> segments = segmentByDelimiters(tokens);
        if (segments.isEmpty()) {
            return Collections.emptyList();
        }

        // Step 2: 解析每个段
        List<AggregationIntent> aggregations = new ArrayList<>();
        for (AggSegment segment : segments) {
            AggregationIntent agg = parseSegment(segment);
            if (agg != null) {
                aggregations.add(agg);
            }
        }

        return aggregations;
    }

    // ==================== 分段逻辑 ====================

    /**
     * 将tokens按聚合分隔词分段
     * 分隔词：同时、并且、还有等
     */
    private List<AggSegment> segmentByDelimiters(List<Token> tokens) {
        List<AggSegment> segments = new ArrayList<>();
        int startIndex = 0;

        for (int i = 0; i < tokens.size(); i++) {
            if (isAggregationDelimiter(tokens.get(i))) {
                // 找到分隔词，切出前一段
                if (i > startIndex) {
                    segments.add(new AggSegment(tokens, startIndex, i));
                }
                startIndex = i + 1; // 下一段从分隔词后开始
            }
        }

        // 添加最后一段
        if (startIndex < tokens.size()) {
            segments.add(new AggSegment(tokens, startIndex, tokens.size()));
        }

        return segments;
    }

    /**
     * 判断token是否为聚合分隔词
     */
    private boolean isAggregationDelimiter(Token token) {
        return token != null && AggKeywords.isAggSeparator(token.getText());
    }

    // ==================== 段解析 ====================

    /**
     * 解析单个聚合段
     */
    private AggregationIntent parseSegment(AggSegment segment) {
        if (segment == null || !segment.isValid()) {
            return null;
        }

        // 识别桶聚合和指标聚合
        BucketAggInfo bucketInfo = identifyBucketAgg(segment);
        MetricAggInfo metricInfo = identifyMetricAgg(segment);

        // 根据识别结果构建聚合
        return buildAggregation(bucketInfo, metricInfo);
    }

    /**
     * 根据桶聚合和指标聚合信息构建聚合意图
     */
    private AggregationIntent buildAggregation(BucketAggInfo bucketInfo, MetricAggInfo metricInfo) {
        if (bucketInfo != null && metricInfo != null) {
            // 桶聚合 + 嵌套指标聚合
            return buildNestedAggregation(bucketInfo, metricInfo);
        } else if (bucketInfo != null) {
            // 纯桶聚合
            return buildBucketAggregation(bucketInfo);
        } else if (metricInfo != null) {
            // 纯指标聚合
            return buildMetricAggregation(metricInfo);
        }
        return null;
    }

    // ==================== 桶聚合识别 ====================

    /**
     * 识别桶聚合信息
     * 优先级：DATE_HISTOGRAM > TERMS > 前缀形式
     */
    private BucketAggInfo identifyBucketAgg(AggSegment segment) {
        // 优先识别 DATE_HISTOGRAM（更具体）
        BucketAggInfo dateHistogram = identifyDateHistogram(segment);
        if (dateHistogram != null) {
            return dateHistogram;
        }

        // 识别其他桶聚合类型
        BucketAggInfo bucketAgg = identifyTypedBucketAgg(segment);
        if (bucketAgg != null) {
            return bucketAgg;
        }

        // 识别前缀形式的桶聚合
        return identifyPrefixBucketAgg(segment);
    }

    /**
     * 识别 DATE_HISTOGRAM 聚合（"每天"、"每小时"等）
     */
    private BucketAggInfo identifyDateHistogram(AggSegment segment) {
        for (int i = segment.startIndex; i < segment.endIndex; i++) {
            String text = segment.tokens.get(i).getText();

            if (AggKeywords.isIntervalKeyword(text)) {
                BucketAggInfo info = new BucketAggInfo();
                info.type = AggType.DATE_HISTOGRAM;
                info.tokenIndex = i;
                info.interval = AggKeywords.getInterval(text);
                info.groupByField = findFieldBeforeToken(segment, i);
                return info;
            }
        }
        return null;
    }

    /**
     * 识别已标记的桶聚合类型（TOKEN类型为AGGREGATION且是桶类型）
     */
    private BucketAggInfo identifyTypedBucketAgg(AggSegment segment) {
        for (int i = segment.startIndex; i < segment.endIndex; i++) {
            Token token = segment.tokens.get(i);

            if (token.getType() == TokenType.AGGREGATION) {
                AggType aggType = token.getAggType();
                if (aggType != null && aggType.isBucket()) {
                    BucketAggInfo info = new BucketAggInfo();
                    info.type = aggType;
                    info.tokenIndex = i;
                    info.groupByField = findGroupByField(segment, i);
                    info.size = extractSizeParam(segment);
                    info.interval = extractIntervalParam(segment);
                    return info;
                }
            }
        }
        return null;
    }

    /**
     * 识别前缀形式的桶聚合（"按X分组"、"每天"）
     */
    private BucketAggInfo identifyPrefixBucketAgg(AggSegment segment) {
        for (int i = segment.startIndex; i < segment.endIndex; i++) {
            Token token = segment.tokens.get(i);
            String text = token.getText();

            // 检查TERMS前缀词（"按"、"by"）
            if (AggKeywords.isTermsPrefix(text)) {
                // 排除排序模式
                if (isSortPattern(segment, i)) {
                    continue;
                }

                BucketAggInfo info = parseTermsBucketAgg(segment, i);
                if (info != null) {
                    return info;
                }
            }

            // 检查DATE_HISTOGRAM前缀词（"每"）
            if (AggKeywords.isDateHistogramPrefix(text)) {
                BucketAggInfo info = parseDateHistogramBucketAgg(segment, i);
                if (info != null) {
                    return info;
                }
            }
        }
        return null;
    }

    /**
     * 解析TERMS类型的桶聚合（"按X分组"）
     */
    private BucketAggInfo parseTermsBucketAgg(AggSegment segment, int prefixIndex) {
        BucketAggInfo info = new BucketAggInfo();
        info.type = AggType.TERMS;
        info.tokenIndex = prefixIndex;
        info.groupByField = findFieldAfterToken(segment, prefixIndex);
        info.size = extractSizeParam(segment);
        return info.groupByField != null ? info : null;
    }

    /**
     * 解析DATE_HISTOGRAM类型的桶聚合（"每天"、"每小时"）
     */
    private BucketAggInfo parseDateHistogramBucketAgg(AggSegment segment, int prefixIndex) {
        // 检查下一个token是否为时间单位
        if (prefixIndex + 1 >= segment.endIndex) {
            return null;
        }

        String prefix = segment.tokens.get(prefixIndex).getText();
        String timeUnit = segment.tokens.get(prefixIndex + 1).getText();
        String combinedText = prefix + timeUnit;
        String interval = AggKeywords.getInterval(combinedText);

        if (interval == null) {
            return null;
        }

        BucketAggInfo info = new BucketAggInfo();
        info.type = AggType.DATE_HISTOGRAM;
        info.tokenIndex = prefixIndex;
        info.interval = interval;
        info.groupByField = findFieldBeforeToken(segment, prefixIndex);
        return info;
    }

    /**
     * 检查是否为排序模式（"按...降序/升序"）
     */
    private boolean isSortPattern(AggSegment segment, int prefixIndex) {
        int endIndex = Math.min(segment.endIndex, prefixIndex + SORT_PATTERN_LOOKAHEAD);

        for (int j = prefixIndex + 1; j < endIndex; j++) {
            String text = segment.tokens.get(j).getText();
            if (SortKeywords.isSortKeyword(text)) {
                return true;
            }
        }

        return false;
    }

    // ==================== 指标聚合识别 ====================

    /**
     * 识别指标聚合信息（"统计平均年龄"、"求和金额"）
     */
    private MetricAggInfo identifyMetricAgg(AggSegment segment) {
        for (int i = segment.startIndex; i < segment.endIndex; i++) {
            Token token = segment.tokens.get(i);

            if (token.getType() == TokenType.AGGREGATION) {
                AggType aggType = token.getAggType();
                if (aggType != null && aggType.isMetric()) {
                    MetricAggInfo info = new MetricAggInfo();
                    info.type = aggType;
                    info.tokenIndex = i;
                    info.field = findFieldAfterToken(segment, i, MAX_FIELD_LOOKAHEAD_DISTANCE);
                    return info;
                }
            }
        }
        return null;
    }

    // ==================== 字段查找逻辑 ====================

    /**
     * 查找分组字段（双向查找）
     */
    private String findGroupByField(AggSegment segment, int aggTokenIndex) {
        // 优先向前查找
        String backwardField = findFieldBeforeToken(segment, aggTokenIndex);
        if (backwardField != null) {
            return backwardField;
        }

        // 向后查找
        return findFieldAfterToken(segment, aggTokenIndex);
    }

    /**
     * 在指定位置之前查找字段
     * 支持合并连续的字段候选tokens
     */
    private String findFieldBeforeToken(AggSegment segment, int tokenIndex) {
        List<String> fieldParts = new ArrayList<>();

        for (int j = tokenIndex - 1; j >= segment.startIndex; j--) {
            Token token = segment.tokens.get(j);
            if (isFieldCandidate(token)) {
                fieldParts.add(0, token.getText()); // 插入到开头保持顺序
            } else {
                break; // 遇到非字段候选，停止
            }
        }

        return fieldParts.isEmpty() ? null : String.join("", fieldParts);
    }

    /**
     * 在指定位置之后查找字段（使用默认距离）
     */
    private String findFieldAfterToken(AggSegment segment, int tokenIndex) {
        return findFieldAfterToken(segment, tokenIndex, segment.endIndex - tokenIndex - 1);
    }

    /**
     * 在指定位置之后查找字段（指定最大距离）
     */
    private String findFieldAfterToken(AggSegment segment, int tokenIndex, int maxDistance) {
        int endIndex = Math.min(segment.endIndex, tokenIndex + maxDistance + 1);

        for (int j = tokenIndex + 1; j < endIndex; j++) {
            Token token = segment.tokens.get(j);
            if (isFieldCandidate(token)) {
                return token.getText();
            }
            // 遇到聚合关键词，停止查找
            if (isAggregationKeyword(token)) {
                break;
            }
        }

        return null;
    }

    /**
     * 判断token是否可能是字段名
     */
    private boolean isFieldCandidate(Token token) {
        if (token == null) {
            return false;
        }

        TokenType type = token.getType();
        if (type == TokenType.FIELD_CANDIDATE) {
            return true;
        }

        if (type == TokenType.UNKNOWN) {
            String text = token.getText();
            // 排除各种关键词
            return !AggKeywords.isAggKeyword(text) &&
                    !AggKeywords.isBucketPrefix(text) &&
                    !AggKeywords.isAggSeparator(text) &&
                    !AggKeywords.isNestedIndicator(text);
        }

        return false;
    }

    /**
     * 判断token是否为聚合关键词
     */
    private boolean isAggregationKeyword(Token token) {
        return token != null && AggKeywords.isAggKeyword(token.getText());
    }

    // ==================== 参数提取 ====================

    /**
     * 提取size参数（"前10个"、"top 5"）
     */
    private Integer extractSizeParam(AggSegment segment) {
        // 合并segment的所有文本
        StringBuilder textBuilder = new StringBuilder();
        for (int i = segment.startIndex; i < segment.endIndex; i++) {
            textBuilder.append(segment.tokens.get(i).getText());
        }

        return AggKeywords.extractSize(textBuilder.toString());
    }

    /**
     * 提取interval参数（"每天"、"1d"）
     */
    private String extractIntervalParam(AggSegment segment) {
        // 检查单个token
        for (int i = segment.startIndex; i < segment.endIndex; i++) {
            String text = segment.tokens.get(i).getText();
            String interval = AggKeywords.getInterval(text);
            if (interval != null) {
                return interval;
            }

            // 检查组合token（"每" + "天"）
            if (i + 1 < segment.endIndex) {
                String nextText = segment.tokens.get(i + 1).getText();
                String combined = text + nextText;
                interval = AggKeywords.getInterval(combined);
                if (interval != null) {
                    return interval;
                }
            }
        }

        return null;
    }

    // ==================== 聚合构建 ====================

    /**
     * 构建嵌套聚合（桶+指标）
     */
    private AggregationIntent buildNestedAggregation(BucketAggInfo bucketInfo, MetricAggInfo metricInfo) {
        AggregationIntent bucketAgg = buildBucketAggregation(bucketInfo);
        AggregationIntent metricAgg = buildMetricAggregation(metricInfo);

        if (bucketAgg != null && metricAgg != null) {
            bucketAgg.setChildren(Collections.singletonList(metricAgg));
        }

        return bucketAgg;
    }

    /**
     * 构建桶聚合
     */
    private AggregationIntent buildBucketAggregation(BucketAggInfo info) {
        if (info == null || info.type == null) {
            return null;
        }

        return AggregationIntent.builder()
                .type(info.type)
                .groupByFieldHint(info.groupByField)
                .fieldHint(info.groupByField)
                .size(info.size)
                .interval(info.interval)
                .name(generateAggName(info.type, info.groupByField))
                .build();
    }

    /**
     * 构建指标聚合
     */
    private AggregationIntent buildMetricAggregation(MetricAggInfo info) {
        if (info == null || info.type == null) {
            return null;
        }

        return AggregationIntent.builder()
                .type(info.type)
                .fieldHint(info.field)
                .name(generateAggName(info.type, info.field))
                .build();
    }

    /**
     * 生成聚合名称
     */
    private String generateAggName(AggType type, String field) {
        if (type == null) {
            return "agg";
        }

        String typeName = type.getCode();
        if (field != null && !field.isEmpty()) {
            return typeName + "_" + field;
        }

        return typeName;
    }

    // ==================== 内部类 ====================

    /**
     * 聚合段
     */
    private static class AggSegment {
        final List<Token> tokens;
        final int startIndex;
        final int endIndex;

        AggSegment(List<Token> tokens, int startIndex, int endIndex) {
            this.tokens = tokens;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }

        boolean isValid() {
            return tokens != null && startIndex >= 0 && endIndex > startIndex && endIndex <= tokens.size();
        }
    }

    /**
     * 桶聚合信息
     */
    private static class BucketAggInfo {
        AggType type;           // 聚合类型（TERMS、DATE_HISTOGRAM等）
        int tokenIndex;         // 关键词位置
        String groupByField;    // 分组字段
        Integer size;           // size参数
        String interval;        // interval参数
    }

    /**
     * 指标聚合信息
     */
    private static class MetricAggInfo {
        AggType type;      // 聚合类型（AVG、SUM等）
        int tokenIndex;    // 关键词位置
        String field;      // 聚合字段
    }
}
