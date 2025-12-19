package io.github.surezzzzzz.sdk.elasticsearch.search.processor;

import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorMessages;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.IndexMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 索引路由处理器
 * 根据时间范围计算需要查询的索引列表
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleElasticsearchSearchComponent
public class IndexRouteProcessor {

    /**
     * 计算需要查询的索引列表
     *
     * @param metadata  索引元数据
     * @param dateRange 日期范围（可选）
     * @return 索引名称数组
     */
    public String[] route(IndexMetadata metadata, QueryRequest.DateRange dateRange) {
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
        String datePattern = metadata.getDatePattern();
        if (datePattern == null) {
            throw new IllegalStateException(
                    String.format(ErrorMessages.DATE_PATTERN_REQUIRED, metadata.getAlias()));  //
        }

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(datePattern);

            // 解析起止日期（只取日期部分，忽略时间）
            LocalDate fromDate = parseDate(dateRange.getFrom());
            LocalDate toDate = parseDate(dateRange.getTo());

            // 4. 计算日期范围内的所有索引
            List<String> indices = new ArrayList<>();
            String indexPrefix = extractIndexPrefix(metadata.getIndexName());

            LocalDate currentDate = fromDate;
            while (!currentDate.isAfter(toDate)) {
                String indexName = indexPrefix + currentDate.format(formatter);
                indices.add(indexName);
                currentDate = currentDate.plusDays(1);
            }

            if (indices.isEmpty()) {
                throw new IllegalArgumentException(
                        "No indices found for date range: " + dateRange.getFrom() + " ~ " + dateRange.getTo());
            }

            String[] result = indices.toArray(new String[0]);
            log.debug("Routed to {} indices for date range [{} ~ {}]: {}",
                    result.length, dateRange.getFrom(), dateRange.getTo(), String.join(",", result));

            return result;

        } catch (Exception e) {
            log.error("Index routing failed for [{}]", metadata.getAlias(), e);  //
            throw new RuntimeException("Index routing failed: " + e.getMessage(), e);
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
            throw new IllegalArgumentException("Invalid date format: " + dateStr, e);
        }
    }

    /**
     * 提取索引前缀（去掉通配符部分）
     * 例如：test_log_* → test_log_
     */
    private String extractIndexPrefix(String indexPattern) {
        if (indexPattern.endsWith("*")) {
            return indexPattern.substring(0, indexPattern.length() - 1);
        }
        return indexPattern;
    }
}
