package io.github.surezzzzzz.sdk.ops.middleware.audit;

import io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.ElasticsearchDocumentQueryRequest;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.KafkaConsumerGroupLagListRequest;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.KafkaConsumerGroupListRequest;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.KafkaTopicListRequest;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.KafkaTopicRuntimeRequest;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.MysqlSelectRequest;
import io.github.surezzzzzz.sdk.ops.middleware.redis.RedisKeyMetadataRequest;
import io.github.surezzzzzz.sdk.ops.middleware.redis.RedisKeyReadRequest;
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
            return builder.elasticsearchIndex(value.getIndex()).elasticsearchDsl(value.getDsl())
                    .page(value.getPage()).size(value.getSize()).build();
        }
        if (request instanceof MysqlSelectRequest) {
            MysqlSelectRequest value = (MysqlSelectRequest) request;
            return builder.mysqlSql(value.getSql()).size(value.getSize()).build();
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
        if (request instanceof KafkaConsumerGroupLagListRequest) {
            KafkaConsumerGroupLagListRequest value = (KafkaConsumerGroupLagListRequest) request;
            return builder.kafkaGroupId(value.getGroupId()).size(value.getSize()).build();
        }
        if (request instanceof KafkaTopicListRequest) {
            return builder.size(((KafkaTopicListRequest) request).getSize()).build();
        }
        if (request instanceof KafkaConsumerGroupListRequest) {
            return builder.size(((KafkaConsumerGroupListRequest) request).getSize()).build();
        }
        return builder.build();
    }
}
