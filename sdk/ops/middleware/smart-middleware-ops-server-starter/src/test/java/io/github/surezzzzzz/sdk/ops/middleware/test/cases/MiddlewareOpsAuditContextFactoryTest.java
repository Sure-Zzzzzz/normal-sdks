package io.github.surezzzzzz.sdk.ops.middleware.test.cases;

import io.github.surezzzzzz.sdk.ops.middleware.audit.MiddlewareOpsAuditContext;
import io.github.surezzzzzz.sdk.ops.middleware.audit.MiddlewareOpsAuditContextFactory;
import io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.document.ElasticsearchDocumentQueryRequest;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.consumer.detail.KafkaConsumerGroupDetailRequest;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.consumer.lag.KafkaConsumerGroupLagListRequest;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.consumer.list.KafkaConsumerGroupListRequest;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.topic.config.KafkaTopicConfigRequest;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.topic.list.KafkaTopicListRequest;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.topic.runtime.KafkaTopicRuntimeRequest;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.query.MysqlExplainRequest;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.query.MysqlSelectRequest;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.table.MysqlTableColumnsRequest;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.table.MysqlTableIndexesRequest;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.table.MysqlTableListRequest;
import io.github.surezzzzzz.sdk.ops.middleware.redis.key.discovery.RedisKeyDiscoveryRequest;
import io.github.surezzzzzz.sdk.ops.middleware.redis.key.metadata.RedisKeyMetadataRequest;
import io.github.surezzzzzz.sdk.ops.middleware.redis.key.read.RedisKeyReadRequest;
import io.github.surezzzzzz.sdk.ops.middleware.redis.summary.RedisSummaryRequest;
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
        String dsl = "{\"query\":{\"match_all\":{}},\"from\":50,\"size\":50}";
        MiddlewareOpsAuditContext context = MiddlewareOpsAuditContextFactory.capture(ElasticsearchDocumentQueryRequest.builder()
                .datasourceKey("primary").index("orders-v1").dsl(dsl).build());

        assertEquals("orders-v1", context.getElasticsearchIndex());
        assertEquals(dsl, context.getElasticsearchDsl());
        assertNull(context.getPage());
        assertNull(context.getSize());
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

        MiddlewareOpsAuditContext explain = MiddlewareOpsAuditContextFactory.capture(MysqlExplainRequest.builder()
                .datasourceKey("mysql84-ops").sql("SELECT id FROM orders WHERE id = 7").build());
        assertEquals("SELECT id FROM orders WHERE id = 7", explain.getMysqlSql());
        assertNull(explain.getSize());

        MiddlewareOpsAuditContext tableList = MiddlewareOpsAuditContextFactory.capture(MysqlTableListRequest.builder()
                .datasourceKey("mysql84-ops").prefix("orders").size(20).build());
        MiddlewareOpsAuditContext columns = MiddlewareOpsAuditContextFactory.capture(MysqlTableColumnsRequest.builder()
                .datasourceKey("mysql84-ops").table("orders").build());
        MiddlewareOpsAuditContext indexes = MiddlewareOpsAuditContextFactory.capture(MysqlTableIndexesRequest.builder()
                .datasourceKey("mysql84-ops").table("orders").build());
        assertEmptyNonElasticsearchFields(tableList);
        assertNull(tableList.getSize());
        assertEmptyNonElasticsearchFields(columns);
        assertNull(columns.getSize());
        assertEmptyNonElasticsearchFields(indexes);
        assertNull(indexes.getSize());
    }

    @Test
    void shouldCaptureOnlyTypedKafkaParameters() {
        MiddlewareOpsAuditContext runtime = MiddlewareOpsAuditContextFactory.capture(KafkaTopicRuntimeRequest.builder()
                .datasourceKey("v37").topic("order-events").build());
        MiddlewareOpsAuditContext config = MiddlewareOpsAuditContextFactory.capture(KafkaTopicConfigRequest.builder()
                .datasourceKey("v37").topic("order-events").build());
        MiddlewareOpsAuditContext detail = MiddlewareOpsAuditContextFactory.capture(KafkaConsumerGroupDetailRequest.builder()
                .datasourceKey("v37").groupId("order-group").build());
        MiddlewareOpsAuditContext lag = MiddlewareOpsAuditContextFactory.capture(KafkaConsumerGroupLagListRequest.builder()
                .datasourceKey("v37").groupId("order-group").size(30).build());
        MiddlewareOpsAuditContext topics = MiddlewareOpsAuditContextFactory.capture(KafkaTopicListRequest.builder()
                .datasourceKey("v37").size(40).build());
        MiddlewareOpsAuditContext groups = MiddlewareOpsAuditContextFactory.capture(KafkaConsumerGroupListRequest.builder()
                .datasourceKey("v37").size(50).build());

        assertEquals("order-events", runtime.getKafkaTopic());
        assertNull(runtime.getKafkaGroupId());
        assertEquals("order-events", config.getKafkaTopic());
        assertNull(config.getKafkaGroupId());
        assertEquals("order-group", detail.getKafkaGroupId());
        assertNull(detail.getKafkaTopic());
        assertNull(detail.getSize());
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
        MiddlewareOpsAuditContext discovery = MiddlewareOpsAuditContextFactory.capture(RedisKeyDiscoveryRequest.builder()
                .datasourceKey("redis7Standalone").prefix("private:prefix:").size(20).build());

        assertEmptyNonElasticsearchFields(context);
        assertEmptyNonElasticsearchFields(discovery);
        assertEmptyDiscoveryContext(discovery);
        assertNull(context.getElasticsearchIndex());
        assertNull(context.getElasticsearchDsl());
        assertNull(context.getPage());
        assertNull(context.getSize());
    }

    private void assertEmptyDiscoveryContext(MiddlewareOpsAuditContext context) {
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
