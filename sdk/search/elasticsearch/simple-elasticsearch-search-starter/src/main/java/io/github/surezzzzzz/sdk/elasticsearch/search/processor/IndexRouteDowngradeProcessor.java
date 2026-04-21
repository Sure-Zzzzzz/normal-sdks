package io.github.surezzzzzz.sdk.elasticsearch.search.processor;

import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.DateGranularity;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.DowngradeLevel;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.IndexRouteException;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.UnsupportedDowngradeLevelException;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.IndexMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.processor.downgrade.DowngradeStrategyRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.support.IndexDateHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.Arrays;

/**
 * 索引路由处理器
 * 根据时间范围和降级级别计算需要查询的索引列表
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleElasticsearchSearchComponent
@RequiredArgsConstructor
public class IndexRouteDowngradeProcessor {

    private final SimpleElasticsearchSearchProperties properties;
    private final DowngradeStrategyRegistry downgradeStrategyRegistry;

    /**
     * 计算需要查询的索引列表（带降级预估）
     *
     * @param metadata  索引元数据
     * @param dateRange 日期范围（可选）
     * @return 索引名称数组
     */
    public String[] route(IndexMetadata metadata, QueryRequest.DateRange dateRange) {
        if (properties.getDowngrade().isEnableEstimate() && metadata.isDateSplit() && dateRange != null) {
            DowngradeLevel estimatedLevel = estimateDowngradeLevel(metadata, dateRange);
            if (estimatedLevel != DowngradeLevel.LEVEL_0) {
                log.info("Pre-estimated downgrade level: {} for index [{}]", estimatedLevel, metadata.getAlias());
                return routeWithDowngrade(metadata, dateRange, estimatedLevel);
            }
        }
        return routeWithDowngrade(metadata, dateRange, DowngradeLevel.LEVEL_0);
    }

    /**
     * 带降级支持的索引路由
     *
     * @param metadata       索引元数据
     * @param dateRange      日期范围
     * @param downgradeLevel 降级级别
     * @return 索引名称数组
     */
    public String[] routeWithDowngrade(IndexMetadata metadata, QueryRequest.DateRange dateRange,
                                       DowngradeLevel downgradeLevel) {
        try {
            if (!metadata.isDateSplit()) {
                return new String[]{metadata.getIndexName()};
            }
            if (dateRange == null) {
                log.warn("Date-split index [{}] without date range, will query all indices", metadata.getAlias());
                return new String[]{metadata.getIndexName()};
            }

            String datePattern = metadata.getDatePattern();
            if (datePattern == null) {
                throw new IndexRouteException(
                        ErrorCode.DATE_PATTERN_REQUIRED,
                        String.format(ErrorMessage.DATE_PATTERN_REQUIRED, metadata.getAlias()),
                        metadata.getAlias()
                );
            }

            DateGranularity granularity = DateGranularity.detectFromPattern(datePattern);
            LocalDate fromDate = IndexDateHelper.parseDate(dateRange.getFrom());
            LocalDate toDate = IndexDateHelper.parseDate(dateRange.getTo());
            String indexPrefix = IndexDateHelper.extractIndexPrefix(metadata.getIndexName());

            String[] indices = downgradeStrategyRegistry.resolve(granularity)
                    .apply(indexPrefix, fromDate, toDate, datePattern, downgradeLevel);

            log.debug("Routed to {} indices for date range [{} ~ {}] with downgrade level {}: {}",
                    indices.length, dateRange.getFrom(), dateRange.getTo(), downgradeLevel,
                    String.join(",", indices));

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
     * 从索引数组中检测降级级别
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
        long wildcardCount = Arrays.stream(indices).filter(idx -> idx.contains("*")).count();
        if (wildcardCount == 1 && firstIndex.endsWith("*")) {
            return DowngradeLevel.LEVEL_3;
        } else if (firstIndex.matches(".*\\d{4}\\.\\*")) {
            return DowngradeLevel.LEVEL_2;
        } else if (firstIndex.matches(".*\\d{4}\\.\\d{2}\\.\\*")) {
            return DowngradeLevel.LEVEL_1;
        }
        return DowngradeLevel.LEVEL_0;
    }

    /**
     * 预估降级级别
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
            LocalDate fromDate = IndexDateHelper.parseDate(dateRange.getFrom());
            LocalDate toDate = IndexDateHelper.parseDate(dateRange.getTo());
            String indexPrefix = IndexDateHelper.extractIndexPrefix(metadata.getIndexName());

            for (DowngradeLevel level : DowngradeLevel.values()) {
                if (level.getValue() > properties.getDowngrade().getMaxLevel()) {
                    break;
                }
                try {
                    String[] indices = downgradeStrategyRegistry.resolve(granularity)
                            .apply(indexPrefix, fromDate, toDate, datePattern, level);

                    if (indices.length > properties.getDowngrade().getAutoDowngradeIndexCountThreshold()) {
                        continue;
                    }
                    if (estimateHttpLineLength(indices) > properties.getDowngrade().getMaxHttpLineLength()) {
                        continue;
                    }
                    return level;
                } catch (UnsupportedDowngradeLevelException e) {
                    return level == DowngradeLevel.LEVEL_0
                            ? DowngradeLevel.LEVEL_0
                            : DowngradeLevel.values()[level.getValue() - 1];
                }
            }

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
        int fixedLength = 25;
        int indicesLength = 0;
        for (int i = 0; i < indices.length; i++) {
            indicesLength += indices[i].length();
            if (i < indices.length - 1) {
                indicesLength += 1;
            }
        }
        return fixedLength + indicesLength;
    }
}
