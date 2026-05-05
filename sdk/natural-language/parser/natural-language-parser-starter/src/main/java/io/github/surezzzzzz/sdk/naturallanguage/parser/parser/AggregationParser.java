package io.github.surezzzzzz.sdk.naturallanguage.parser.parser;

import io.github.surezzzzzz.sdk.naturallanguage.parser.annotation.NaturalLanguageParserComponent;
import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.AggType;
import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.IntentType;
import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.NLParserConstant;
import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.TokenType;
import io.github.surezzzzzz.sdk.naturallanguage.parser.keyword.KeywordRegistry;
import io.github.surezzzzzz.sdk.naturallanguage.parser.configuration.NLParserProperties;
import io.github.surezzzzzz.sdk.naturallanguage.parser.model.AggregationIntent;
import io.github.surezzzzzz.sdk.naturallanguage.parser.tokenizer.Token;
import org.springframework.core.annotation.Order;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 聚合解析插件
 *
 * @author surezzzzzz
 */
@NaturalLanguageParserComponent
@Order(15)
public class AggregationParser implements NLParserPlugin {

    // 聚合分隔词（parser 内部实现细节）
    private static final Set<String> AGG_SEPARATORS = new HashSet<>(Arrays.asList(
            "同时", "并且", "还有", "以及", "和", "另外", "再", "且", "及", ",", "，"
    ));

    // 桶聚合前缀词
    private static final Set<String> BUCKET_PREFIXES = new HashSet<>(Arrays.asList("按", "每", "by"));

    // TERMS 前缀词
    private static final Set<String> TERMS_PREFIXES = new HashSet<>(Arrays.asList("按", "by"));

    // DATE_HISTOGRAM 前缀词
    private static final Set<String> DATE_HISTOGRAM_PREFIXES = new HashSet<>(Arrays.asList("每"));

    // 嵌套指示词
    private static final Set<String> NESTED_INDICATORS = new HashSet<>(Arrays.asList(
            "统计", "计算", "求", "每组", "各个", "各", "的"
    ));

    // 时间间隔映射
    private static final Map<String, String> INTERVAL_MAP = new HashMap<>();

    // size 参数模式
    private static final Pattern SIZE_PATTERN = Pattern.compile("(前|限制|最多|取前|top)\\s*(\\d+)\\s*(个|条)?");

    static {
        INTERVAL_MAP.put("每天", "1d");
        INTERVAL_MAP.put("每日", "1d");
        INTERVAL_MAP.put("按天", "1d");
        INTERVAL_MAP.put("天", "1d");
        INTERVAL_MAP.put("每小时", "1h");
        INTERVAL_MAP.put("每时", "1h");
        INTERVAL_MAP.put("按小时", "1h");
        INTERVAL_MAP.put("小时", "1h");
        INTERVAL_MAP.put("每周", "1w");
        INTERVAL_MAP.put("周", "1w");
        INTERVAL_MAP.put("每月", "1M");
        INTERVAL_MAP.put("月", "1M");
        INTERVAL_MAP.put("每年", "1y");
        INTERVAL_MAP.put("年", "1y");
        INTERVAL_MAP.put("1d", "1d");
        INTERVAL_MAP.put("1h", "1h");
        INTERVAL_MAP.put("1w", "1w");
        INTERVAL_MAP.put("1M", "1M");
        INTERVAL_MAP.put("1y", "1y");
    }

    @Override
    public boolean supports(IntentType intentType) {
        return intentType == IntentType.ANALYTICS;
    }

    @Override
    public void parse(List<Token> tokens, KeywordRegistry keywordRegistry,
                      NLParserProperties properties, ParseResult result) {
        List<AggregationIntent> aggregations = doParse(tokens, keywordRegistry);
        if (aggregations != null && !aggregations.isEmpty()) {
            result.setAggregations(aggregations);
        }
    }

    private List<AggregationIntent> doParse(List<Token> tokens, KeywordRegistry keywordRegistry) {
        if (tokens == null || tokens.isEmpty()) {
            return Collections.emptyList();
        }

        List<AggSegment> segments = segmentByDelimiters(tokens);
        if (segments.isEmpty()) {
            return Collections.emptyList();
        }

        List<AggregationIntent> aggregations = new ArrayList<>();
        for (AggSegment segment : segments) {
            AggregationIntent agg = parseSegment(segment, keywordRegistry);
            if (agg != null) {
                aggregations.add(agg);
            }
        }

        return aggregations;
    }

    // ==================== 分段逻辑 ====================

    private List<AggSegment> segmentByDelimiters(List<Token> tokens) {
        List<AggSegment> segments = new ArrayList<>();
        int startIndex = 0;

        for (int i = 0; i < tokens.size(); i++) {
            if (isAggregationDelimiter(tokens.get(i))) {
                if (i > startIndex) {
                    segments.add(new AggSegment(tokens, startIndex, i));
                }
                startIndex = i + 1;
            }
        }

        if (startIndex < tokens.size()) {
            segments.add(new AggSegment(tokens, startIndex, tokens.size()));
        }

        return segments;
    }

    private boolean isAggregationDelimiter(Token token) {
        return token != null && AGG_SEPARATORS.contains(token.getText());
    }

    // ==================== 段解析 ====================

    private AggregationIntent parseSegment(AggSegment segment, KeywordRegistry keywordRegistry) {
        if (segment == null || !segment.isValid()) {
            return null;
        }

        BucketAggInfo bucketInfo = identifyBucketAgg(segment, keywordRegistry);
        MetricAggInfo metricInfo = identifyMetricAgg(segment, keywordRegistry);

        return buildAggregation(bucketInfo, metricInfo);
    }

    private AggregationIntent buildAggregation(BucketAggInfo bucketInfo, MetricAggInfo metricInfo) {
        if (bucketInfo != null && metricInfo != null) {
            return buildNestedAggregation(bucketInfo, metricInfo);
        } else if (bucketInfo != null) {
            return buildBucketAggregation(bucketInfo);
        } else if (metricInfo != null) {
            return buildMetricAggregation(metricInfo);
        }
        return null;
    }

    // ==================== 桶聚合识别 ====================

    private BucketAggInfo identifyBucketAgg(AggSegment segment, KeywordRegistry keywordRegistry) {
        BucketAggInfo dateHistogram = identifyDateHistogram(segment);
        if (dateHistogram != null) {
            return dateHistogram;
        }

        BucketAggInfo bucketAgg = identifyTypedBucketAgg(segment);
        if (bucketAgg != null) {
            return bucketAgg;
        }

        return identifyPrefixBucketAgg(segment, keywordRegistry);
    }

    private BucketAggInfo identifyDateHistogram(AggSegment segment) {
        for (int i = segment.startIndex; i < segment.endIndex; i++) {
            String text = segment.tokens.get(i).getText();

            if (isIntervalKeyword(text)) {
                BucketAggInfo info = new BucketAggInfo();
                info.type = AggType.DATE_HISTOGRAM;
                info.tokenIndex = i;
                info.interval = getInterval(text);
                info.groupByField = findFieldBeforeToken(segment, i);
                return info;
            }
        }
        return null;
    }

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

    private BucketAggInfo identifyPrefixBucketAgg(AggSegment segment, KeywordRegistry keywordRegistry) {
        for (int i = segment.startIndex; i < segment.endIndex; i++) {
            Token token = segment.tokens.get(i);
            String text = token.getText();

            if (TERMS_PREFIXES.contains(text)) {
                if (isSortPattern(segment, i, keywordRegistry)) {
                    continue;
                }
                if (isCollapsePattern(segment, i, keywordRegistry)) {
                    continue;
                }

                BucketAggInfo info = parseTermsBucketAgg(segment, i);
                if (info != null) {
                    return info;
                }
            }

            if (DATE_HISTOGRAM_PREFIXES.contains(text)) {
                BucketAggInfo info = parseDateHistogramBucketAgg(segment, i);
                if (info != null) {
                    return info;
                }
            }
        }
        return null;
    }

    private BucketAggInfo parseTermsBucketAgg(AggSegment segment, int prefixIndex) {
        BucketAggInfo info = new BucketAggInfo();
        info.type = AggType.TERMS;
        info.tokenIndex = prefixIndex;
        info.groupByField = findFieldAfterToken(segment, prefixIndex);
        info.size = extractSizeParam(segment);
        return info.groupByField != null ? info : null;
    }

    private BucketAggInfo parseDateHistogramBucketAgg(AggSegment segment, int prefixIndex) {
        if (prefixIndex + 1 >= segment.endIndex) {
            return null;
        }

        String prefix = segment.tokens.get(prefixIndex).getText();
        String timeUnit = segment.tokens.get(prefixIndex + 1).getText();
        String combinedText = prefix + timeUnit;
        String interval = getInterval(combinedText);

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

    private boolean isSortPattern(AggSegment segment, int prefixIndex, KeywordRegistry keywordRegistry) {
        int endIndex = Math.min(segment.endIndex, prefixIndex + NLParserConstant.SORT_PATTERN_LOOKAHEAD);

        for (int j = prefixIndex + 1; j < endIndex; j++) {
            String text = segment.tokens.get(j).getText();
            if (keywordRegistry.resolveSortOrder(text) != null) {
                return true;
            }
        }

        return false;
    }

    private boolean isCollapsePattern(AggSegment segment, int prefixIndex, KeywordRegistry keywordRegistry) {
        int endIndex = Math.min(segment.endIndex, prefixIndex + NLParserConstant.SORT_PATTERN_LOOKAHEAD + 1);

        for (int j = prefixIndex + 1; j < endIndex; j++) {
            String text = segment.tokens.get(j).getText();

            if (keywordRegistry.isCollapseKeyword(text)) {
                return true;
            }

            if (j + 1 < segment.endIndex) {
                String combined = text + segment.tokens.get(j + 1).getText();
                if (keywordRegistry.isCollapseKeyword(combined)) {
                    return true;
                }
            }
        }

        return false;
    }

    // ==================== 指标聚合识别 ====================

    private MetricAggInfo identifyMetricAgg(AggSegment segment, KeywordRegistry keywordRegistry) {
        for (int i = segment.startIndex; i < segment.endIndex; i++) {
            Token token = segment.tokens.get(i);

            if (token.getType() == TokenType.AGGREGATION) {
                AggType aggType = token.getAggType();
                if (aggType != null && aggType.isMetric()) {
                    if (keywordRegistry.isCollapseKeyword(token.getText())) {
                        continue;
                    }

                    // "统计"等通用前缀词：如果后面紧跟更具体的指标聚合，跳过 COUNT
                    if (aggType == AggType.COUNT && hasMoreSpecificMetricAfter(segment, i)) {
                        continue;
                    }

                    MetricAggInfo info = new MetricAggInfo();
                    info.type = aggType;
                    info.tokenIndex = i;
                    info.field = findMetricField(segment, i);
                    return info;
                }
            }
        }
        return null;
    }

    /**
     * 判断指定位置之后是否还有更具体的指标聚合（AVG/SUM/MAX/MIN/CARDINALITY等）
     */
    private boolean hasMoreSpecificMetricAfter(AggSegment segment, int currentIndex) {
        for (int j = currentIndex + 1; j < segment.endIndex; j++) {
            Token nextToken = segment.tokens.get(j);
            if (nextToken.getType() == TokenType.AGGREGATION) {
                AggType nextType = nextToken.getAggType();
                if (nextType != null && nextType.isMetric() && nextType != AggType.COUNT) {
                    return true;
                }
            }
        }
        return false;
    }

    private String findMetricField(AggSegment segment, int aggTokenIndex) {
        String forwardField = findFieldAfterToken(segment, aggTokenIndex, NLParserConstant.MAX_FIELD_LOOKAHEAD_DISTANCE);
        if (forwardField != null) {
            return forwardField;
        }
        return findFieldBeforeToken(segment, aggTokenIndex);
    }

    // ==================== 字段查找逻辑 ====================

    private String findGroupByField(AggSegment segment, int aggTokenIndex) {
        String backwardField = findFieldBeforeToken(segment, aggTokenIndex);
        if (backwardField != null) {
            return backwardField;
        }
        return findFieldAfterToken(segment, aggTokenIndex);
    }

    private String findFieldBeforeToken(AggSegment segment, int tokenIndex) {
        List<String> fieldParts = new ArrayList<>();

        for (int j = tokenIndex - 1; j >= segment.startIndex; j--) {
            Token token = segment.tokens.get(j);
            if (isFieldCandidate(token)) {
                fieldParts.add(0, token.getText());
            } else {
                break;
            }
        }

        return fieldParts.isEmpty() ? null : String.join("", fieldParts);
    }

    private String findFieldAfterToken(AggSegment segment, int tokenIndex) {
        return findFieldAfterToken(segment, tokenIndex, segment.endIndex - tokenIndex - 1);
    }

    private String findFieldAfterToken(AggSegment segment, int tokenIndex, int maxDistance) {
        int endIndex = Math.min(segment.endIndex, tokenIndex + maxDistance + 1);

        for (int j = tokenIndex + 1; j < endIndex; j++) {
            Token token = segment.tokens.get(j);
            if (isFieldCandidate(token)) {
                return token.getText();
            }
            if (isAggregationKeyword(token)) {
                break;
            }
        }

        return null;
    }

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
            return !isAggKeyword(text) &&
                    !BUCKET_PREFIXES.contains(text) &&
                    !AGG_SEPARATORS.contains(text) &&
                    !NESTED_INDICATORS.contains(text);
        }

        return false;
    }

    private boolean isAggregationKeyword(Token token) {
        return token != null && isAggKeyword(token.getText());
    }

    private boolean isAggKeyword(String text) {
        return text != null && (AGG_SEPARATORS.contains(text) || BUCKET_PREFIXES.contains(text) ||
                NESTED_INDICATORS.contains(text));
    }

    // ==================== 参数提取 ====================

    private Integer extractSizeParam(AggSegment segment) {
        StringBuilder textBuilder = new StringBuilder();
        for (int i = segment.startIndex; i < segment.endIndex; i++) {
            textBuilder.append(segment.tokens.get(i).getText());
        }
        return extractSize(textBuilder.toString());
    }

    private String extractIntervalParam(AggSegment segment) {
        for (int i = segment.startIndex; i < segment.endIndex; i++) {
            String text = segment.tokens.get(i).getText();
            String interval = getInterval(text);
            if (interval != null) {
                return interval;
            }

            if (i + 1 < segment.endIndex) {
                String nextText = segment.tokens.get(i + 1).getText();
                String combined = text + nextText;
                interval = getInterval(combined);
                if (interval != null) {
                    return interval;
                }
            }
        }

        return null;
    }

    // ==================== interval / size 辅助 ====================

    private boolean isIntervalKeyword(String keyword) {
        return getInterval(keyword) != null;
    }

    private String getInterval(String keyword) {
        if (keyword == null) {
            return null;
        }
        return INTERVAL_MAP.get(keyword);
    }

    private Integer extractSize(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = SIZE_PATTERN.matcher(text);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(2));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    // ==================== 聚合构建 ====================

    private AggregationIntent buildNestedAggregation(BucketAggInfo bucketInfo, MetricAggInfo metricInfo) {
        AggregationIntent bucketAgg = buildBucketAggregation(bucketInfo);
        AggregationIntent metricAgg = buildMetricAggregation(metricInfo);

        if (bucketAgg != null && metricAgg != null) {
            bucketAgg.setSubAggs(Collections.singletonList(metricAgg));
        }

        return bucketAgg;
    }

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
                .nameHint(generateAggName(info.type, info.groupByField))
                .build();
    }

    private AggregationIntent buildMetricAggregation(MetricAggInfo info) {
        if (info == null || info.type == null) {
            return null;
        }

        return AggregationIntent.builder()
                .type(info.type)
                .fieldHint(info.field)
                .nameHint(generateAggName(info.type, info.field))
                .build();
    }

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

    private static class BucketAggInfo {
        AggType type;
        int tokenIndex;
        String groupByField;
        Integer size;
        String interval;
    }

    private static class MetricAggInfo {
        AggType type;
        int tokenIndex;
        String field;
    }
}
