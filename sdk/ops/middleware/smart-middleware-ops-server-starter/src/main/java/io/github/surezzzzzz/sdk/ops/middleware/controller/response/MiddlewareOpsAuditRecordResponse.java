package io.github.surezzzzzz.sdk.ops.middleware.controller.response;

import lombok.Builder;
import lombok.Getter;

/**
 * 脱敏后的 Middleware Ops 审计记录响应。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class MiddlewareOpsAuditRecordResponse {

    /**
     * 请求标识。
     */
    private final String id;
    /**
     * 发生时间。
     */
    private final String occurredAt;
    /**
     * 查询主体。
     */
    private final String subject;
    /**
     * 固定能力标识。
     */
    private final String capability;
    /**
     * 中间件类型。
     */
    private final String middlewareType;
    /**
     * 数据源标识。
     */
    private final String datasourceKey;
    /**
     * 查询发生时的数据源展示标签。
     */
    private final String clusterTag;
    /**
     * 资源摘要。
     */
    private final String resourceDigest;
    /**
     * HTTP 状态。
     */
    private final Integer httpStatus;
    /**
     * 执行时长毫秒数。
     */
    private final Long durationMillis;
    /**
     * 已脱敏的 Elasticsearch 索引。
     */
    private final String elasticsearchIndex;
    /**
     * 由 Search 脱敏策略决定是否返回的 Elasticsearch DSL。
     */
    private final String elasticsearchDsl;
    /**
     * 由 Search 脱敏策略决定是否返回的 MySQL SQL。
     */
    private final String mysqlSql;
    /**
     * 已脱敏的 Redis key。
     */
    private final String redisKey;
    /**
     * 已脱敏的 Redis field。
     */
    private final String redisField;
    /**
     * 已脱敏的 Kafka topic。
     */
    private final String kafkaTopic;
    /**
     * 已脱敏的 Kafka 消费组。
     */
    private final String kafkaGroupId;
    /**
     * 请求页码。
     */
    private final Integer page;
    /**
     * 请求结果上限。
     */
    private final Integer size;
    /**
     * Redis 读取偏移量。
     */
    private final Long offset;
}
