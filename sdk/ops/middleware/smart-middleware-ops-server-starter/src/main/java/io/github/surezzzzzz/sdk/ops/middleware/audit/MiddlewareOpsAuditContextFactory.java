package io.github.surezzzzzz.sdk.ops.middleware.audit;

import io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.document.ElasticsearchDocumentQueryRequest;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.consumer.detail.KafkaConsumerGroupDetailRequest;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.consumer.lag.KafkaConsumerGroupLagListRequest;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.consumer.list.KafkaConsumerGroupListRequest;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.topic.config.KafkaTopicConfigRequest;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.topic.list.KafkaTopicListRequest;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.topic.runtime.KafkaTopicRuntimeRequest;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.query.MysqlExplainRequest;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.query.MysqlSelectRequest;
import io.github.surezzzzzz.sdk.ops.middleware.redis.key.metadata.RedisKeyMetadataRequest;
import io.github.surezzzzzz.sdk.ops.middleware.redis.key.read.RedisKeyReadRequest;
import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsRequest;

/**
 * 中间件运维审计参数快照工厂。
 *
 * @author surezzzzzz
 */
public final class MiddlewareOpsAuditContextFactory {

    private MiddlewareOpsAuditContextFactory() {
        throw new UnsupportedOperationException("审计参数快照工厂不能实例化");
    }

    /**
     * 从已校验的类型化请求提取允许持久化的完整操作参数。
     *
     * @param request 类型化运维请求
     * @return 审计参数快照
     */
    public static MiddlewareOpsAuditContext capture(MiddlewareOpsRequest request) {
        MiddlewareOpsAuditContext.MiddlewareOpsAuditContextBuilder builder = MiddlewareOpsAuditContext.builder();
        if (request instanceof ElasticsearchDocumentQueryRequest) {
            ElasticsearchDocumentQueryRequest value = (ElasticsearchDocumentQueryRequest) request;
            return builder.elasticsearchIndex(value.getIndex()).elasticsearchDsl(value.getDsl()).build();
        }
        if (request instanceof MysqlSelectRequest) {
            MysqlSelectRequest value = (MysqlSelectRequest) request;
            return builder.mysqlSql(value.getSql()).size(value.getSize()).build();
        }
        if (request instanceof MysqlExplainRequest) {
            return builder.mysqlSql(((MysqlExplainRequest) request).getSql()).build();
        }
        if (request instanceof RedisKeyMetadataRequest) {
            return builder.redisKey(((RedisKeyMetadataRequest) request).getKey()).build();
        }
        if (request instanceof RedisKeyReadRequest) {
            RedisKeyReadRequest value = (RedisKeyReadRequest) request;
            return builder.redisKey(value.getKey()).redisField(value.getField())
                    .offset(value.getOffset()).size(value.getSize()).build();
        }
        if (request instanceof KafkaTopicRuntimeRequest) {
            return builder.kafkaTopic(((KafkaTopicRuntimeRequest) request).getTopic()).build();
        }
        if (request instanceof KafkaTopicConfigRequest) {
            return builder.kafkaTopic(((KafkaTopicConfigRequest) request).getTopic()).build();
        }
        if (request instanceof KafkaConsumerGroupDetailRequest) {
            return builder.kafkaGroupId(((KafkaConsumerGroupDetailRequest) request).getGroupId()).build();
        }
        if (request instanceof KafkaConsumerGroupLagListRequest) {
            KafkaConsumerGroupLagListRequest value = (KafkaConsumerGroupLagListRequest) request;
            return builder.kafkaGroupId(value.getGroupId()).size(value.getSize()).build();
        }
        if (request instanceof KafkaTopicListRequest) {
            KafkaTopicListRequest value = (KafkaTopicListRequest) request;
            return builder.kafkaTopic(value.getPrefix()).size(value.getSize()).build();
        }
        if (request instanceof KafkaConsumerGroupListRequest) {
            KafkaConsumerGroupListRequest value = (KafkaConsumerGroupListRequest) request;
            return builder.kafkaGroupId(value.getPrefix()).size(value.getSize()).build();
        }
        return builder.build();
    }
}
