package io.github.surezzzzzz.sdk.ops.middleware.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.kafka.route.registry.SimpleKafkaRouteRegistry;
import io.github.surezzzzzz.sdk.mysql.route.registry.SimpleMysqlRouteRegistry;
import io.github.surezzzzzz.sdk.ops.middleware.catalog.DatasourceCatalogExecutor;
import io.github.surezzzzzz.sdk.ops.middleware.catalog.DatasourceCatalogRequest;
import io.github.surezzzzzz.sdk.ops.middleware.catalog.DatasourceCatalogResponse;
import io.github.surezzzzzz.sdk.ops.middleware.configuration.SmartMiddlewareOpsServerProperties;
import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareType;
import io.github.surezzzzzz.sdk.redis.route.registry.SimpleRedisRouteRegistry;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 启动期数据源目录与自由标签快照测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class DatasourceCatalogExecutorTest {

    @Test
    void shouldReturnOnlyFixedWorkspaceSnapshotAndKeepTagsImmutable() {
        SimpleElasticsearchRouteRegistry elasticsearchRegistry = mock(SimpleElasticsearchRouteRegistry.class);
        SimpleRedisRouteRegistry redisRegistry = mock(SimpleRedisRouteRegistry.class);
        SimpleKafkaRouteRegistry kafkaRegistry = mock(SimpleKafkaRouteRegistry.class);
        SimpleMysqlRouteRegistry mysqlRegistry = mock(SimpleMysqlRouteRegistry.class);
        Map<String, Object> templates = new LinkedHashMap<>();
        templates.put("shared", null);
        when(elasticsearchRegistry.getTemplates()).thenReturn((Map) templates);
        LinkedHashSet<String> redisKeys = new LinkedHashSet<>(Arrays.asList("shared", "redis-untagged"));
        when(redisRegistry.getDatasourceKeys()).thenReturn(redisKeys);
        when(kafkaRegistry.getDatasourceKeys()).thenReturn(new LinkedHashSet<>(Arrays.asList("event")));
        LinkedHashSet<String> mysqlDatasources = new LinkedHashSet<>(Arrays.asList("ops-primary"));
        when(mysqlRegistry.getDatasources()).thenReturn(mysqlDatasources);

        SmartMiddlewareOpsServerProperties.DatasourceTags tags = tags();
        DatasourceCatalogExecutor executor = new DatasourceCatalogExecutor(elasticsearchRegistry, redisRegistry,
                kafkaRegistry, mysqlRegistry, tags);

        tags.getElasticsearch().put("shared", "变更后不应生效");
        redisKeys.add("added-later");
        mysqlDatasources.add("ops-secondary");
        DatasourceCatalogResponse elasticsearch = execute(executor, MiddlewareType.ELASTICSEARCH);
        DatasourceCatalogResponse redis = execute(executor, MiddlewareType.REDIS);
        DatasourceCatalogResponse kafka = execute(executor, MiddlewareType.KAFKA);
        DatasourceCatalogResponse mysql = execute(executor, MiddlewareType.MYSQL);
        log.info("工作区目录：elasticsearch={}，redis={}，kafka={}，mysql={}", elasticsearch.getItems().size(),
                redis.getItems().size(), kafka.getItems().size(), mysql.getItems().size());

        assertEquals(1, elasticsearch.getItems().size());
        assertEquals(2, redis.getItems().size());
        assertEquals(1, kafka.getItems().size());
        assertEquals(1, mysql.getItems().size());
        assertItemsBelongTo(elasticsearch, MiddlewareType.ELASTICSEARCH);
        assertItemsBelongTo(redis, MiddlewareType.REDIS);
        assertItemsBelongTo(kafka, MiddlewareType.KAFKA);
        assertItemsBelongTo(mysql, MiddlewareType.MYSQL);
        assertEquals("华东订单检索集群", executor.resolve(MiddlewareType.ELASTICSEARCH, "shared"));
        assertEquals("蓝色缓存集群", executor.resolve(MiddlewareType.REDIS, "shared"));
        assertEquals("数据平台 Kafka", executor.resolve(MiddlewareType.KAFKA, "event"));
        assertEquals("MySQL 只读库", executor.resolve(MiddlewareType.MYSQL, "ops-primary"));
        assertNull(executor.resolve(MiddlewareType.REDIS, "redis-untagged"));
        assertNull(executor.resolve(MiddlewareType.KAFKA, "unknown"));
        assertNull(executor.resolve(MiddlewareType.REDIS, "added-later"));
        assertNull(executor.resolve(MiddlewareType.MYSQL, "ops-secondary"));
    }

    @Test
    void shouldReturnEmptySnapshotOnlyForWorkspaceWithoutRouteRegistry() {
        SimpleRedisRouteRegistry redisRegistry = mock(SimpleRedisRouteRegistry.class);
        when(redisRegistry.getDatasourceKeys()).thenReturn(new LinkedHashSet<>(Arrays.asList("redis-default")));
        DatasourceCatalogExecutor executor = new DatasourceCatalogExecutor(null, redisRegistry, null, null, tags());

        DatasourceCatalogResponse elasticsearch = execute(executor, MiddlewareType.ELASTICSEARCH);
        DatasourceCatalogResponse redis = execute(executor, MiddlewareType.REDIS);
        DatasourceCatalogResponse kafka = execute(executor, MiddlewareType.KAFKA);
        DatasourceCatalogResponse mysql = execute(executor, MiddlewareType.MYSQL);
        log.info("缺失 Route 的目录：elasticsearch={}，redis={}，kafka={}，mysql={}", elasticsearch.getItems().size(),
                redis.getItems().size(), kafka.getItems().size(), mysql.getItems().size());

        assertEquals(0, elasticsearch.getItems().size());
        assertEquals(1, redis.getItems().size());
        assertEquals(0, kafka.getItems().size());
        assertEquals(0, mysql.getItems().size());
        assertItemsBelongTo(redis, MiddlewareType.REDIS);
    }

    private SmartMiddlewareOpsServerProperties.DatasourceTags tags() {
        SmartMiddlewareOpsServerProperties.DatasourceTags tags = new SmartMiddlewareOpsServerProperties.DatasourceTags();
        tags.getElasticsearch().put("shared", "华东订单检索集群");
        tags.getRedis().put("shared", "蓝色缓存集群");
        tags.getKafka().put("event", "数据平台 Kafka");
        tags.getMysql().put("ops-primary", "MySQL 只读库");
        tags.getKafka().put("unknown", "不应进入目录");
        return tags;
    }

    private DatasourceCatalogResponse execute(DatasourceCatalogExecutor executor, MiddlewareType middlewareType) {
        return executor.execute(new DatasourceCatalogRequest(middlewareType));
    }

    private void assertItemsBelongTo(DatasourceCatalogResponse response, MiddlewareType middlewareType) {
        assertTrue(response.getItems().stream()
                .allMatch(item -> item.getMiddlewareType() == middlewareType));
    }
}
