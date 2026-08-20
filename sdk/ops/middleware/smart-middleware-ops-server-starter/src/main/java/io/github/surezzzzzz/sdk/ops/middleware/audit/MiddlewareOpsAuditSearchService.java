package io.github.surezzzzzz.sdk.ops.middleware.audit;

import io.github.surezzzzzz.sdk.elasticsearch.search.constant.QueryOperator;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.executor.QueryExecutor;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.PaginationInfo;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryResponse;
import io.github.surezzzzzz.sdk.ops.middleware.annotation.SmartMiddlewareOpsServerComponent;
import io.github.surezzzzzz.sdk.ops.middleware.configuration.SmartMiddlewareOpsServerProperties;
import io.github.surezzzzzz.sdk.ops.middleware.constant.SmartMiddlewareOpsServerConstant;
import io.github.surezzzzzz.sdk.ops.middleware.controller.response.MiddlewareOpsAuditPageResponse;
import io.github.surezzzzzz.sdk.ops.middleware.controller.response.MiddlewareOpsAuditRecordResponse;
import io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException;
import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 通过 search-starter 读取固定审计索引模式的受限服务。
 *
 * @author surezzzzzz
 */
@SmartMiddlewareOpsServerComponent
public class MiddlewareOpsAuditSearchService {

    private static final List<String> AUDIT_FIELDS = Arrays.asList(
            SmartMiddlewareOpsServerConstant.AUDIT_FIELD_ID,
            SmartMiddlewareOpsServerConstant.AUDIT_FIELD_OCCURRED_AT,
            SmartMiddlewareOpsServerConstant.AUDIT_FIELD_SUBJECT,
            SmartMiddlewareOpsServerConstant.AUDIT_FIELD_CAPABILITY,
            SmartMiddlewareOpsServerConstant.AUDIT_FIELD_MIDDLEWARE_TYPE,
            SmartMiddlewareOpsServerConstant.AUDIT_FIELD_DATASOURCE_KEY,
            SmartMiddlewareOpsServerConstant.AUDIT_FIELD_CLUSTER_TAG,
            SmartMiddlewareOpsServerConstant.AUDIT_FIELD_RESOURCE_DIGEST,
            SmartMiddlewareOpsServerConstant.AUDIT_FIELD_HTTP_STATUS,
            SmartMiddlewareOpsServerConstant.AUDIT_FIELD_DURATION_MILLIS,
            SmartMiddlewareOpsServerConstant.AUDIT_FIELD_ELASTICSEARCH_INDEX,
            SmartMiddlewareOpsServerConstant.AUDIT_FIELD_ELASTICSEARCH_DSL,
            SmartMiddlewareOpsServerConstant.AUDIT_FIELD_MYSQL_SQL,
            SmartMiddlewareOpsServerConstant.AUDIT_FIELD_REDIS_KEY,
            SmartMiddlewareOpsServerConstant.AUDIT_FIELD_REDIS_FIELD,
            SmartMiddlewareOpsServerConstant.AUDIT_FIELD_KAFKA_TOPIC,
            SmartMiddlewareOpsServerConstant.AUDIT_FIELD_KAFKA_GROUP_ID,
            SmartMiddlewareOpsServerConstant.AUDIT_FIELD_PAGE,
            SmartMiddlewareOpsServerConstant.AUDIT_FIELD_SIZE,
            SmartMiddlewareOpsServerConstant.AUDIT_FIELD_OFFSET);

    private final ObjectProvider<QueryExecutor> queryExecutorProvider;
    private final SmartMiddlewareOpsServerProperties properties;

    /**
     * 创建固定审计读服务。
     *
     * @param queryExecutorProvider search-starter 查询执行器提供者
     * @param properties            Server 配置
     */
    public MiddlewareOpsAuditSearchService(ObjectProvider<QueryExecutor> queryExecutorProvider,
                                           SmartMiddlewareOpsServerProperties properties) {
        this.queryExecutorProvider = queryExecutorProvider;
        this.properties = properties;
    }

    /**
     * 读取指定工作区类型的脱敏审计记录。
     *
     * @param middlewareType 固定工作区中间件类型
     * @param page           页码，从 1 开始
     * @param size           页大小
     * @return 受限审计分页结果
     */
    public MiddlewareOpsAuditPageResponse search(MiddlewareType middlewareType, int page, int size) {
        return search(middlewareType, page, size, null);
    }

    /**
     * 在指定时间范围内读取指定工作区类型的脱敏审计记录。
     *
     * @param middlewareType 固定工作区中间件类型
     * @param page           页码，从 1 开始
     * @param size           页大小
     * @param timeRange      已校验的时间范围；为空时由 Search Starter 使用默认范围
     * @return 受限审计分页结果
     */
    public MiddlewareOpsAuditPageResponse search(MiddlewareType middlewareType, int page, int size,
                                                 MiddlewareOpsAuditTimeRange timeRange) {
        if (middlewareType == null) {
            throw new MiddlewareOpsException(HttpStatus.BAD_REQUEST, "审计工作区类型无效");
        }
        if (!Boolean.TRUE.equals(properties.getAudit().getEnabled())) {
            return empty(page, size, timeRange);
        }
        QueryExecutor queryExecutor = queryExecutorProvider.getIfAvailable();
        if (queryExecutor == null) {
            throw unavailable();
        }
        QueryRequest request = request(middlewareType, page, size, timeRange);
        try {
            QueryResponse response = queryExecutor.execute(request);
            return MiddlewareOpsAuditPageResponse.builder().total(response.getTotal()).page(response.getPage())
                    .size(response.getSize()).hasMore(hasMore(response.getTotal(), page, size))
                    .from(rangeFrom(request)).to(rangeTo(request)).items(toRecords(response.getItems())).build();
        } catch (RuntimeException e) {
            throw unavailable(e);
        }
    }

    private QueryRequest request(MiddlewareType middlewareType, int page, int size,
                                 MiddlewareOpsAuditTimeRange timeRange) {
        QueryRequest request = QueryRequest.builder().index(SmartMiddlewareOpsServerConstant.AUDIT_READ_INDEX_PATTERN)
                .query(QueryCondition.builder().field(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_MIDDLEWARE_TYPE)
                        .op(QueryOperator.EQ.getOperator()).value(middlewareType.getCode()).build())
                .pagination(PaginationInfo.builder().type(SmartMiddlewareOpsServerConstant.AUDIT_PAGINATION_TYPE)
                        .page(page).size(size).sort(Arrays.asList(PaginationInfo.SortField.builder()
                                .field(SmartMiddlewareOpsServerConstant.AUDIT_SORT_FIELD_OCCURRED_AT)
                                .order(SmartMiddlewareOpsServerConstant.AUDIT_SORT_ORDER_DESC).build()))
                        .build()).fields(AUDIT_FIELDS).build();
        if (timeRange != null) {
            request.setDateRange(QueryRequest.DateRange.builder().from(timeRange.getFrom()).to(timeRange.getTo()).build());
        }
        return request;
    }

    private String rangeFrom(QueryRequest request) {
        return request.getDateRange() == null ? null : request.getDateRange().getFrom();
    }

    private String rangeTo(QueryRequest request) {
        return request.getDateRange() == null ? null : request.getDateRange().getTo();
    }

    private Boolean hasMore(Long total, int page, int size) {
        return total != null && total > (long) page * size;
    }

    private List<MiddlewareOpsAuditRecordResponse> toRecords(List<Map<String, Object>> items) {
        List<MiddlewareOpsAuditRecordResponse> records = new ArrayList<>();
        if (items == null) {
            return records;
        }
        for (Map<String, Object> item : items) {
            records.add(MiddlewareOpsAuditRecordResponse.builder().id(text(item, SmartMiddlewareOpsServerConstant.AUDIT_FIELD_ID))
                    .occurredAt(text(item, SmartMiddlewareOpsServerConstant.AUDIT_FIELD_OCCURRED_AT))
                    .subject(text(item, SmartMiddlewareOpsServerConstant.AUDIT_FIELD_SUBJECT))
                    .capability(text(item, SmartMiddlewareOpsServerConstant.AUDIT_FIELD_CAPABILITY))
                    .middlewareType(text(item, SmartMiddlewareOpsServerConstant.AUDIT_FIELD_MIDDLEWARE_TYPE))
                    .datasourceKey(text(item, SmartMiddlewareOpsServerConstant.AUDIT_FIELD_DATASOURCE_KEY))
                    .clusterTag(text(item, SmartMiddlewareOpsServerConstant.AUDIT_FIELD_CLUSTER_TAG))
                    .resourceDigest(text(item, SmartMiddlewareOpsServerConstant.AUDIT_FIELD_RESOURCE_DIGEST))
                    .httpStatus(integer(item, SmartMiddlewareOpsServerConstant.AUDIT_FIELD_HTTP_STATUS))
                    .durationMillis(longValue(item, SmartMiddlewareOpsServerConstant.AUDIT_FIELD_DURATION_MILLIS))
                    .elasticsearchIndex(text(item, SmartMiddlewareOpsServerConstant.AUDIT_FIELD_ELASTICSEARCH_INDEX))
                    .elasticsearchDsl(text(item, SmartMiddlewareOpsServerConstant.AUDIT_FIELD_ELASTICSEARCH_DSL))
                    .mysqlSql(text(item, SmartMiddlewareOpsServerConstant.AUDIT_FIELD_MYSQL_SQL))
                    .redisKey(text(item, SmartMiddlewareOpsServerConstant.AUDIT_FIELD_REDIS_KEY))
                    .redisField(text(item, SmartMiddlewareOpsServerConstant.AUDIT_FIELD_REDIS_FIELD))
                    .kafkaTopic(text(item, SmartMiddlewareOpsServerConstant.AUDIT_FIELD_KAFKA_TOPIC))
                    .kafkaGroupId(text(item, SmartMiddlewareOpsServerConstant.AUDIT_FIELD_KAFKA_GROUP_ID))
                    .page(integer(item, SmartMiddlewareOpsServerConstant.AUDIT_FIELD_PAGE))
                    .size(integer(item, SmartMiddlewareOpsServerConstant.AUDIT_FIELD_SIZE))
                    .offset(longValue(item, SmartMiddlewareOpsServerConstant.AUDIT_FIELD_OFFSET)).build());
        }
        return records;
    }

    private MiddlewareOpsAuditPageResponse empty(int page, int size, MiddlewareOpsAuditTimeRange timeRange) {
        return MiddlewareOpsAuditPageResponse.builder().total(0L).page(page).size(size).hasMore(Boolean.FALSE)
                .from(timeRange == null ? null : timeRange.getFrom()).to(timeRange == null ? null : timeRange.getTo())
                .items(new ArrayList<MiddlewareOpsAuditRecordResponse>()).build();
    }

    private String text(Map<String, Object> item, String field) {
        Object value = item.get(field);
        return value == null ? null : String.valueOf(value);
    }

    private Integer integer(Map<String, Object> item, String field) {
        Object value = item.get(field);
        return value instanceof Number ? ((Number) value).intValue() : null;
    }

    private Long longValue(Map<String, Object> item, String field) {
        Object value = item.get(field);
        return value instanceof Number ? ((Number) value).longValue() : null;
    }

    private MiddlewareOpsException unavailable() {
        return new MiddlewareOpsException(HttpStatus.SERVICE_UNAVAILABLE, "审计查询暂不可用");
    }

    private MiddlewareOpsException unavailable(Throwable cause) {
        return new MiddlewareOpsException(HttpStatus.SERVICE_UNAVAILABLE, "审计查询暂不可用", cause);
    }
}
