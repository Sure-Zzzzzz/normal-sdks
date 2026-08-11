package io.github.surezzzzzz.sdk.ops.middleware.test.cases;

import io.github.surezzzzzz.sdk.ops.middleware.audit.MiddlewareOpsAuditContext;
import io.github.surezzzzzz.sdk.ops.middleware.audit.MiddlewareOpsAuditContextFactory;
import io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.ElasticsearchDocumentQueryRequest;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.KafkaConsumerGroupLagListRequest;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.KafkaConsumerGroupListRequest;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.KafkaTopicListRequest;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.KafkaTopicRuntimeRequest;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.MysqlSelectRequest;
import io.github.surezzzzzz.sdk.ops.middleware.redis.RedisKeyMetadataRequest;
import io.github.surezzzzzz.sdk.ops.middleware.redis.RedisKeyReadRequest;
import io.github.surezzzzzz.sdk.ops.middleware.redis.RedisSummaryRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 审计参数快照白名单测试。
 *
 * @author surezzzzzz
 */
class MiddlewareOpsAuditContextFactoryTest {

    @Test
    void shouldCaptureOnlyTypedElasticsearchQueryParameters() {
        MiddlewareOpsAuditContext context = MiddlewareOpsAuditContextFactory.capture(ElasticsearchDocumentQueryRequest.builder()
                .datasourceKey("primary").index("orders-v1").dsl("{\"query\":{\"match_all\":{}}}").page(2).size(50).build());

        assertEquals("orders-v1", context.getElasticsearchIndex());
        assertEquals("{\"query\":{\"match_all\":{}}}", context.getElasticsearchDsl());
        assertEquals(2, context.getPage());
        assertEquals(50, context.getSize());
        assertEmptyNonElasticsearchFields(context);
    }

    @Test
    void shouldCaptureOnlyTypedMysqlAndRedisParameters() {
        MiddlewareOpsAuditContext mysql = MiddlewareOpsAuditContextFactory.capture(MysqlSelectRequest.builder()
                .datasourceKey("mysql84-ops").sql("SELECT id FROM orders WHERE id = 7").size(1).build());
        MiddlewareOpsAuditContext metadata = MiddlewareOpsAuditContextFactory.capture(RedisKeyMetadataRequest.builder()
                .datasourceKey("redis7Standalone").key("order:7").build());
        MiddlewareOpsAuditContext read = MiddlewareOpsAuditContextFactory.capture(RedisKeyReadRequest.builder()
                .datasourceKey("redis7Standalone").key("order:7").field("status").offset(10L).size(20).build());

        assertEquals("SELECT id FROM orders WHERE id = 7", mysql.getMysqlSql());
        assertEquals(1, mysql.getSize());
        assertNull(mysql.getElasticsearchIndex());
        assertNull(mysql.getRedisKey());
        assertEquals("order:7", metadata.getRedisKey());
        assertNull(metadata.getRedisField());
        assertNull(metadata.getOffset());
        assertEquals("order:7", read.getRedisKey());
        assertEquals("status", read.getRedisField());
        assertEquals(10L, read.getOffset());
        assertEquals(20, read.getSize());
        assertNull(read.getMysqlSql());
    }

    @Test
    void shouldCaptureOnlyTypedKafkaParameters() {
        MiddlewareOpsAuditContext runtime = MiddlewareOpsAuditContextFactory.capture(KafkaTopicRuntimeRequest.builder()
                .datasourceKey("v37").topic("order-events").build());
        MiddlewareOpsAuditContext lag = MiddlewareOpsAuditContextFactory.capture(KafkaConsumerGroupLagListRequest.builder()
                .datasourceKey("v37").groupId("order-group").size(30).build());
        MiddlewareOpsAuditContext topics = MiddlewareOpsAuditContextFactory.capture(KafkaTopicListRequest.builder()
                .datasourceKey("v37").size(40).build());
        MiddlewareOpsAuditContext groups = MiddlewareOpsAuditContextFactory.capture(KafkaConsumerGroupListRequest.builder()
                .datasourceKey("v37").size(50).build());

        assertEquals("order-events", runtime.getKafkaTopic());
        assertNull(runtime.getKafkaGroupId());
        assertEquals("order-group", lag.getKafkaGroupId());
        assertEquals(30, lag.getSize());
        assertNull(lag.getKafkaTopic());
        assertEquals(40, topics.getSize());
        assertEquals(50, groups.getSize());
    }

    @Test
    void shouldKeepContextEmptyForOperationsWithoutAdditionalParameters() {
        MiddlewareOpsAuditContext context = MiddlewareOpsAuditContextFactory.capture(RedisSummaryRequest.builder()
                .datasourceKey("redis7Standalone").build());

        assertEmptyNonElasticsearchFields(context);
        assertNull(context.getElasticsearchIndex());
        assertNull(context.getElasticsearchDsl());
        assertNull(context.getPage());
        assertNull(context.getSize());
    }

    private void assertEmptyNonElasticsearchFields(MiddlewareOpsAuditContext context) {
        assertNull(context.getMysqlSql());
        assertNull(context.getRedisKey());
        assertNull(context.getRedisField());
        assertNull(context.getKafkaTopic());
        assertNull(context.getKafkaGroupId());
        assertNull(context.getOffset());
    }
}
