package io.github.surezzzzzz.sdk.elasticsearch.search.processor;

import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.*;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.IndexRouteException;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.SimpleElasticsearchSearchException;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.UnsupportedDowngradeLevelException;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.IndexMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 索引路由处理器
 * 根据时间范围计算需要查询的索引列表
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleElasticsearchSearchComponent
public class IndexRouteProcessor {

    private final SimpleElasticsearchSearchProperties properties;

    /**
     * 用于提取日期格式中分隔符的正则表达式
     * 匹配非字母数字字符（如 . - / 等）
     */
    private static final Pattern SEPARATOR_PATTERN = Pattern.compile("[^a-zA-Z0-9]");

    public IndexRouteProcessor(SimpleElasticsearchSearchProperties properties) {
        this.properties = properties;
    }

    /**
     * 计算需要查询的索引列表（带降级支持）
     *
     * @param metadata  索引元数据
     * @param dateRange 日期范围（可选）
     * @return 索引名称数组
     */
    public String[] route(IndexMetadata metadata, QueryRequest.DateRange dateRange) {
        // 如果启用了降级预估，则先尝试预估是否需要降级
        if (properties.getDowngrade().isEnableEstimate() && metadata.isDateSplit() && dateRange != null) {
            DowngradeLevel estimatedLevel = estimateDowngradeLevel(metadata, dateRange);
            if (estimatedLevel != DowngradeLevel.LEVEL_0) {
                log.info("Pre-estimated downgrade level: {} for index [{}]", estimatedLevel, metadata.getAlias());
                return routeWithDowngrade(metadata, dateRange, estimatedLevel);
            }
        }

        // 默认走 LEVEL_0（不降级）
        return routeWithDowngrade(metadata, dateRange, DowngradeLevel.LEVEL_0);
    }

    /**
     * 计算需要查询的索引列表（原始方法，保留兼容性）
     *
     * @param metadata  索引元数据
     * @param dateRange 日期范围（可选）
     * @return 索引名称数组
     */
    private String[] routeOriginal(IndexMetadata metadata, QueryRequest.DateRange dateRange) {
        // 1. 如果不是日期分割索引，直接返回索引名
        if (!metadata.isDateSplit()) {
            return new String[]{metadata.getIndexName()};
        }

        // 2. 如果是日期分割索引，但没有提供日期范围，返回通配符
        if (dateRange == null) {
            log.warn("Date-split index [{}] without date range, will query all indices",
                    metadata.getAlias());  //
            return new String[]{metadata.getIndexName()};
        }

        // 3. 解析日期范围
        try {
            String datePattern = metadata.getDatePattern();
            if (datePattern == null) {
                throw new IllegalStateException(
                        String.format(ErrorMessage.DATE_PATTERN_REQUIRED, metadata.getAlias()));  //
            }

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(datePattern);

            // 解析起止日期（只取日期部分，忽略时间）
            LocalDate fromDate = parseDate(dateRange.getFrom());
            LocalDate toDate = parseDate(dateRange.getTo());

            // 4. 根据日期格式判断分割粒度
            DateGranularity granularity = DateGranularity.detectFromPattern(datePattern);
            log.debug("Detected date granularity: {} for pattern: {}", granularity, datePattern);

            // 5. 计算日期范围内的所有索引
            List<String> indices = new ArrayList<>();
            String indexPrefix = extractIndexPrefix(metadata.getIndexName());

            LocalDate currentDate = fromDate;
            while (!currentDate.isAfter(toDate)) {
                String indexName = indexPrefix + currentDate.format(formatter);
                // 避免重复添加（例如按月分割时，同一个月的不同天会生成相同的索引名）
                if (indices.isEmpty() || !indices.get(indices.size() - 1).equals(indexName)) {
                    indices.add(indexName);
                }

                // 根据粒度递增日期
                currentDate = granularity.increment(currentDate);
            }

            if (indices.isEmpty()) {
                throw new SimpleElasticsearchSearchException(ErrorCode.DATE_PATTERN_REQUIRED,
                        "No indices found for date range: " + dateRange.getFrom() + " ~ " + dateRange.getTo());
            }

            String[] result = indices.toArray(new String[0]);
            log.debug("Routed to {} indices for date range [{} ~ {}]: {}",
                    result.length, dateRange.getFrom(), dateRange.getTo(), String.join(",", result));

            return result;

        } catch (Exception e) {
            log.error("Index routing failed for [{}]", metadata.getAlias(), e);  //
            throw new SimpleElasticsearchSearchException(ErrorCode.DATE_PATTERN_REQUIRED,
                    "Index routing failed: " + e.getMessage(), e);
        }
    }

    /**
     * 解析日期字符串（支持多种格式）
     */
    private LocalDate parseDate(String dateStr) {
        try {
            // 尝试解析 ISO 格式：2025-12-18T00:00:00
            if (dateStr.contains("T")) {
                return LocalDateTime.parse(dateStr).toLocalDate();
            }
            // 尝试解析纯日期：2025-12-18
            return LocalDate.parse(dateStr);
        } catch (Exception e) {
            throw new SimpleElasticsearchSearchException(ErrorCode.DATE_PATTERN_REQUIRED,
                    "Invalid date format: " + dateStr, e);
        }
    }

    /**
     * 提取索引前缀（去掉通配符部分）
     * 例如：test_log_* → test_log_
     */
    private String extractIndexPrefix(String indexPattern) {
        if (indexPattern.endsWith(SimpleElasticsearchSearchConstant.WILDCARD_STAR)) {
            return indexPattern.substring(0, indexPattern.length() - 1);
        }
        return indexPattern;
    }

    /**
     * 从索引数组中检测降级级别
     * 根据通配符模式判断当前使用的降级级别
     *
     * @param indices 索引名称数组
     * @return 检测到的降级级别
     */
    public DowngradeLevel detectDowngradeLevelFromIndices(String[] indices) {
        if (indices == null || indices.length == 0) {
            return DowngradeLevel.LEVEL_0;
        }

        String firstIndex = indices[0];
        if (!firstIndex.contains("*")) {
            return DowngradeLevel.LEVEL_0;
        }

        // 统计包含通配符的索引数量
        long wildcardCount = java.util.Arrays.stream(indices)
                .filter(idx -> idx.contains("*"))
                .count();

        // 全通配符：只有一个索引且以 * 结尾
        if (wildcardCount == 1 && firstIndex.endsWith("*")) {
            return DowngradeLevel.LEVEL_3;
        }
        // 年级通配符：yyyy.*
        else if (firstIndex.matches(".*\\d{4}\\.\\*")) {
            return DowngradeLevel.LEVEL_2;
        }
        // 月级通配符：yyyy.MM.*
        else if (firstIndex.matches(".*\\d{4}\\.\\d{2}\\.\\*")) {
            return DowngradeLevel.LEVEL_1;
        }

        return DowngradeLevel.LEVEL_0;
    }

    // ========== 降级相关方法 ==========

    /**
     * 带降级支持的索引路由
     *
     * @param metadata       索引元数据
     * @param dateRange      日期范围
     * @param downgradeLevel 降级级别
     * @return 索引名称数组
     */
    public String[] routeWithDowngrade(IndexMetadata metadata, QueryRequest.DateRange dateRange, DowngradeLevel downgradeLevel) {
        try {
            // 1. 如果不是日期分割索引，直接返回索引名
            if (!metadata.isDateSplit()) {
                return new String[]{metadata.getIndexName()};
            }

            // 2. 如果是日期分割索引，但没有提供日期范围，返回通配符
            if (dateRange == null) {
                log.warn("Date-split index [{}] without date range, will query all indices", metadata.getAlias());
                return new String[]{metadata.getIndexName()};
            }

            // 3. 检查日期格式
            String datePattern = metadata.getDatePattern();
            if (datePattern == null) {
                throw new IndexRouteException(
                        ErrorCode.DATE_PATTERN_REQUIRED,
                        String.format(ErrorMessage.DATE_PATTERN_REQUIRED, metadata.getAlias()),
                        metadata.getAlias()
                );
            }

            // 4. 检测日期粒度
            DateGranularity granularity = DateGranularity.detectFromPattern(datePattern);
            log.debug("Detected date granularity: {} for pattern: {}", granularity, datePattern);

            // 5. 解析日期范围
            LocalDate fromDate = parseDate(dateRange.getFrom());
            LocalDate toDate = parseDate(dateRange.getTo());

            // 6. 提取索引前缀
            String indexPrefix = extractIndexPrefix(metadata.getIndexName());

            // 7. 根据粒度和降级级别生成索引列表
            String[] indices = applyDowngrade(indexPrefix, fromDate, toDate, datePattern, granularity, downgradeLevel);

            log.debug("Routed to {} indices for date range [{} ~ {}] with downgrade level {}: {}",
                    indices.length, dateRange.getFrom(), dateRange.getTo(), downgradeLevel, String.join(",", indices));

            return indices;

        } catch (IndexRouteException | UnsupportedDowngradeLevelException e) {
            throw e;
        } catch (Exception e) {
            log.error("Index routing failed for [{}]", metadata.getAlias(), e);
            throw new IndexRouteException(
                    ErrorCode.INDEX_ROUTE_FAILED,
                    String.format(ErrorMessage.INDEX_ROUTE_FAILED, metadata.getAlias()),
                    metadata.getAlias(),
                    e
            );
        }
    }

    /**
     * 预估降级级别
     * <p>
     * 在执行查询前预估需要的降级级别，避免无谓的重试。
     * 通过检查索引数量和HTTP请求行长度，从LEVEL_0开始逐级尝试，
     * 找到第一个满足条件（不超过阈值）的降级级别。
     * </p>
     * <p>
     * 预估逻辑：
     * <ul>
     *   <li>索引数量超过阈值 → 继续尝试更高级别</li>
     *   <li>HTTP请求行长度超限 → 继续尝试更高级别</li>
     *   <li>两者都满足 → 返回当前级别</li>
     *   <li>所有级别都不满足 → 返回最大级别</li>
     * </ul>
     * </p>
     *
     * @param metadata  索引元数据
     * @param dateRange 日期范围
     * @return 预估的降级级别
     */
    private DowngradeLevel estimateDowngradeLevel(IndexMetadata metadata, QueryRequest.DateRange dateRange) {
        try {
            String datePattern = metadata.getDatePattern();
            if (datePattern == null) {
                return DowngradeLevel.LEVEL_0;
            }

            DateGranularity granularity = DateGranularity.detectFromPattern(datePattern);
            LocalDate fromDate = parseDate(dateRange.getFrom());
            LocalDate toDate = parseDate(dateRange.getTo());
            String indexPrefix = extractIndexPrefix(metadata.getIndexName());

            // 尝试从 LEVEL_0 开始，找到第一个满足条件的降级级别
            for (DowngradeLevel level : DowngradeLevel.values()) {
                if (level.getValue() > properties.getDowngrade().getMaxLevel()) {
                    break;
                }

                try {
                    String[] indices = applyDowngrade(indexPrefix, fromDate, toDate, datePattern, granularity, level);

                    // 索引数量超过阈值，尝试更高级别的降级
                    if (indices.length > properties.getDowngrade().getAutoDowngradeIndexCountThreshold()) {
                        continue;
                    }

                    // HTTP 请求行长度超限，尝试更高级别的降级
                    int estimatedLength = estimateHttpLineLength(indices);
                    if (estimatedLength > properties.getDowngrade().getMaxHttpLineLength()) {
                        continue;
                    }

                    // 找到合适的降级级别
                    return level;

                } catch (UnsupportedDowngradeLevelException e) {
                    // 该粒度不支持此降级级别，返回已达到的最大级别
                    return level == DowngradeLevel.LEVEL_0 ? DowngradeLevel.LEVEL_0 : DowngradeLevel.values()[level.getValue() - 1];
                }
            }

            // 如果所有级别都不满足，返回最大级别
            return DowngradeLevel.fromValue(
                    Math.min(properties.getDowngrade().getMaxLevel(), DowngradeLevel.LEVEL_3.getValue())
            );

        } catch (Exception e) {
            log.warn("Failed to estimate downgrade level for [{}], using LEVEL_0", metadata.getAlias(), e);
            return DowngradeLevel.LEVEL_0;
        }
    }

    /**
     * 估算 HTTP 请求行长度
     *
     * @param indices 索引名称数组
     * @return 估算的长度（字节）
     */
    private int estimateHttpLineLength(String[] indices) {
        // HTTP 请求行格式: GET /index1,index2,index3/_search HTTP/1.1\r\n
        // 固定部分: "GET " + " /_search HTTP/1.1\r\n" ≈ 25 字节
        int fixedLength = 25;

        // 索引名部分
        int indicesLength = 0;
        for (int i = 0; i < indices.length; i++) {
            indicesLength += indices[i].length();
            if (i < indices.length - 1) {
                indicesLength += 1; // 逗号分隔符
            }
        }

        return fixedLength + indicesLength;
    }

    /**
     * 应用降级策略
     *
     * @param indexPrefix    索引前缀
     * @param fromDate       起始日期
     * @param toDate         结束日期
     * @param datePattern    日期格式
     * @param granularity    日期粒度
     * @param downgradeLevel 降级级别
     * @return 索引名称数组
     */
    private String[] applyDowngrade(String indexPrefix, LocalDate fromDate, LocalDate toDate,
                                    String datePattern, DateGranularity granularity, DowngradeLevel downgradeLevel) {
        switch (granularity) {
            case DAY:
                return applyDailyDowngrade(indexPrefix, fromDate, toDate, datePattern, downgradeLevel);
            case MONTH:
                return applyMonthlyDowngrade(indexPrefix, fromDate, toDate, datePattern, downgradeLevel);
            case YEAR:
                return applyYearlyDowngrade(indexPrefix, fromDate, toDate, datePattern, downgradeLevel);
            default:
                throw new SimpleElasticsearchSearchException(
                        ErrorCode.INVALID_PARAMETER,
                        "Unsupported granularity: " + granularity
                );
        }
    }

    /**
     * 日粒度降级策略
     */
    private String[] applyDailyDowngrade(String indexPrefix, LocalDate fromDate, LocalDate toDate,
                                         String datePattern, DowngradeLevel downgradeLevel) {
        switch (downgradeLevel) {
            case LEVEL_0:
                // 具体日期索引
                return generateDailyIndices(indexPrefix, fromDate, toDate, datePattern);
            case LEVEL_1:
                // 月级通配符
                return generateMonthlyWildcards(indexPrefix, fromDate, toDate, datePattern);
            case LEVEL_2:
                // 年通配符
                return generateYearlyWildcards(indexPrefix, fromDate, toDate, datePattern);
            case LEVEL_3:
                // 全通配符
                return new String[]{indexPrefix + "*"};
            default:
                throw new UnsupportedDowngradeLevelException(
                        ErrorCode.UNSUPPORTED_DOWNGRADE_LEVEL,
                        String.format(ErrorMessage.UNSUPPORTED_DOWNGRADE_LEVEL, downgradeLevel, DateGranularity.DAY),
                        downgradeLevel,
                        DateGranularity.DAY
                );
        }
    }

    /**
     * 月粒度降级策略
     */
    private String[] applyMonthlyDowngrade(String indexPrefix, LocalDate fromDate, LocalDate toDate,
                                           String datePattern, DowngradeLevel downgradeLevel) {
        switch (downgradeLevel) {
            case LEVEL_0:
                // 具体月份索引
                return generateMonthlyIndices(indexPrefix, fromDate, toDate, datePattern);
            case LEVEL_1:
                // 年通配符
                return generateYearlyWildcards(indexPrefix, fromDate, toDate, datePattern);
            case LEVEL_2:
            case LEVEL_3:
                // 全通配符
                return new String[]{indexPrefix + "*"};
            default:
                throw new UnsupportedDowngradeLevelException(
                        ErrorCode.UNSUPPORTED_DOWNGRADE_LEVEL,
                        String.format(ErrorMessage.UNSUPPORTED_DOWNGRADE_LEVEL, downgradeLevel, DateGranularity.MONTH),
                        downgradeLevel,
                        DateGranularity.MONTH
                );
        }
    }

    /**
     * 年粒度降级策略
     */
    private String[] applyYearlyDowngrade(String indexPrefix, LocalDate fromDate, LocalDate toDate,
                                          String datePattern, DowngradeLevel downgradeLevel) {
        switch (downgradeLevel) {
            case LEVEL_0:
                // 具体年份索引
                return generateYearlyIndices(indexPrefix, fromDate, toDate, datePattern);
            case LEVEL_1:
            case LEVEL_2:
            case LEVEL_3:
                // 全通配符
                return new String[]{indexPrefix + "*"};
            default:
                throw new UnsupportedDowngradeLevelException(
                        ErrorCode.UNSUPPORTED_DOWNGRADE_LEVEL,
                        String.format(ErrorMessage.UNSUPPORTED_DOWNGRADE_LEVEL, downgradeLevel, DateGranularity.YEAR),
                        downgradeLevel,
                        DateGranularity.YEAR
                );
        }
    }

    /**
     * 生成日粒度索引列表
     */
    private String[] generateDailyIndices(String indexPrefix, LocalDate fromDate, LocalDate toDate, String datePattern) {
        // 计算总天数
        long totalDays = java.time.temporal.ChronoUnit.DAYS.between(fromDate, toDate) + 1;

        // 如果天数过多，记录警告日志（建议使用降级）
        if (totalDays > 365) {
            log.warn("Date range spans {} days, generating {} daily indices may impact performance. " +
                    "Consider using downgrade or reducing date range.", totalDays, totalDays);
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(datePattern);
        Set<String> indices = new LinkedHashSet<>();

        LocalDate currentDate = fromDate;
        while (!currentDate.isAfter(toDate)) {
            indices.add(indexPrefix + currentDate.format(formatter));
            currentDate = currentDate.plusDays(1);
        }

        return indices.toArray(new String[0]);
    }

    /**
     * 生成月粒度索引列表
     */
    private String[] generateMonthlyIndices(String indexPrefix, LocalDate fromDate, LocalDate toDate, String datePattern) {
        // 提取年月部分的格式（去掉日部分）
        String monthPattern = buildMonthPattern(datePattern);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(monthPattern);
        Set<String> indices = new LinkedHashSet<>();

        YearMonth currentMonth = YearMonth.from(fromDate);
        YearMonth endMonth = YearMonth.from(toDate);

        while (!currentMonth.isAfter(endMonth)) {
            indices.add(indexPrefix + currentMonth.format(formatter));
            currentMonth = currentMonth.plusMonths(1);
        }

        return indices.toArray(new String[0]);
    }

    /**
     * 生成年粒度索引列表
     */
    private String[] generateYearlyIndices(String indexPrefix, LocalDate fromDate, LocalDate toDate, String datePattern) {
        // 提取年部分的格式
        String yearPattern = buildYearPattern(datePattern);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(yearPattern);
        Set<String> indices = new LinkedHashSet<>();

        int currentYear = fromDate.getYear();
        int endYear = toDate.getYear();

        while (currentYear <= endYear) {
            LocalDate yearDate = LocalDate.of(currentYear, 1, 1);
            indices.add(indexPrefix + yearDate.format(formatter));
            currentYear++;
        }

        return indices.toArray(new String[0]);
    }

    /**
     * 生成月级通配符列表
     */
    private String[] generateMonthlyWildcards(String indexPrefix, LocalDate fromDate, LocalDate toDate, String datePattern) {
        String monthPattern = buildMonthPattern(datePattern);
        String separator = extractSeparator(datePattern);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(monthPattern);
        Set<String> wildcards = new LinkedHashSet<>();

        YearMonth currentMonth = YearMonth.from(fromDate);
        YearMonth endMonth = YearMonth.from(toDate);

        while (!currentMonth.isAfter(endMonth)) {
            wildcards.add(indexPrefix + currentMonth.format(formatter) + separator + "*");
            currentMonth = currentMonth.plusMonths(1);
        }

        return wildcards.toArray(new String[0]);
    }

    /**
     * 生成年通配符列表
     */
    private String[] generateYearlyWildcards(String indexPrefix, LocalDate fromDate, LocalDate toDate, String datePattern) {
        String yearPattern = buildYearPattern(datePattern);
        String separator = extractSeparator(datePattern);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(yearPattern);
        Set<String> wildcards = new LinkedHashSet<>();

        int currentYear = fromDate.getYear();
        int endYear = toDate.getYear();

        while (currentYear <= endYear) {
            LocalDate yearDate = LocalDate.of(currentYear, 1, 1);
            wildcards.add(indexPrefix + yearDate.format(formatter) + separator + "*");
            currentYear++;
        }

        return wildcards.toArray(new String[0]);
    }

    /**
     * 提取日期格式中的分隔符
     *
     * @param datePattern 日期格式（如 yyyy.MM.dd）
     * @return 分隔符（如 .）
     */
    private String extractSeparator(String datePattern) {
        Matcher matcher = SEPARATOR_PATTERN.matcher(datePattern);
        if (matcher.find()) {
            return matcher.group();
        }
        return "";
    }

    /**
     * 构建月级别的日期格式（去掉日部分）
     *
     * @param datePattern 原始日期格式（如 yyyy.MM.dd）
     * @return 月级别格式（如 yyyy.MM）
     */
    private String buildMonthPattern(String datePattern) {
        // 移除日相关的格式字符（d/dd）
        return datePattern.replaceAll("[dD]+[^a-zA-Z]*$", "").replaceAll("[^a-zA-Z0-9]+$", "");
    }

    /**
     * 构建年级别的日期格式（去掉月日部分）
     *
     * @param datePattern 原始日期格式（如 yyyy.MM.dd）
     * @return 年级别格式（如 yyyy）
     */
    private String buildYearPattern(String datePattern) {
        // 移除月日相关的格式字符
        return datePattern.replaceAll("[MmDd]+.*$", "").replaceAll("[^a-zA-Z0-9]+$", "");
    }
}
