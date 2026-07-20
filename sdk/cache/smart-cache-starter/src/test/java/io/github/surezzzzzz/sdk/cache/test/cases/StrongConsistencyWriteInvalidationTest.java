package io.github.surezzzzzz.sdk.cache.test.cases;

import io.github.surezzzzzz.sdk.cache.configuration.SmartCacheProperties;
import io.github.surezzzzzz.sdk.cache.constant.SmartCacheConstant;
import io.github.surezzzzzz.sdk.cache.layer.L1Cache;
import io.github.surezzzzzz.sdk.cache.layer.L2Cache;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import io.github.surezzzzzz.sdk.cache.pubsub.CacheInvalidationListener;
import io.github.surezzzzzz.sdk.cache.support.KeyHelper;
import io.github.surezzzzzz.sdk.cache.test.SmartCacheTestApplication;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 多路由强一致性写后失效端到端测试
 *
 * @author surezzzzzz
 */
@Slf4j
class StrongConsistencyWriteInvalidationTest {

    private static final String KEY_PREFIX = "strong-write-route-it";
    private static final String ME = "strong-write-route-group";
    private static final String PUBSUB_PREFIX = "strong-write-route-sync";
    private static final String DEFAULT_DATASOURCE = "default";
    private static final String CACHE_DATASOURCE = "cache";
    private static final String LOCK_DATASOURCE = "lock";
    private static final List<String> DATASOURCES = Arrays.asList(
            DEFAULT_DATASOURCE, CACHE_DATASOURCE, LOCK_DATASOURCE);
    private static final List<String> NON_LOCK_DATASOURCES = Arrays.asList(
            DEFAULT_DATASOURCE, CACHE_DATASOURCE);
    private static final String PUT_CACHE_NAME = "strong-write-route-put";
    private static final String PUT_ALL_CACHE_NAME = "strong-write-route-put-all";

    private boolean usesRedis7Cluster;

    @Test
    @DisplayName("三 standalone Redis 强一致性 put：Redis 5（16380）L2、Redis 3（16379）Pub/Sub")
    void shouldRoutePutThroughStandaloneCacheAndPubSubDatasources() throws Exception {
        assertPutRouteTopology("classpath:/application-route-strong-standalone.yml", "standalone", false);
    }

    @Test
    @DisplayName("三 Redis Cluster 强一致性 put：Redis 5 Cluster L2、Redis 3 Cluster Pub/Sub")
    void shouldRoutePutThroughClusterCacheAndPubSubDatasources() throws Exception {
        assertPutRouteTopology("classpath:/application-route-strong-cluster.yml", "cluster", true);
    }

    private void assertPutRouteTopology(String configLocation, String topology, boolean usesRedis7Cluster) throws Exception {
        this.usesRedis7Cluster = usesRedis7Cluster;
        try (ConfigurableApplicationContext writerContext = startContext("writer", configLocation);
             ConfigurableApplicationContext readerContext = startContext("reader", configLocation)) {
            CacheNode writer = cacheNode(writerContext);
            CacheNode reader = cacheNode(readerContext);
            String cacheName = PUT_CACHE_NAME;
            List<String> keys = Arrays.asList("target", "unrelated");

            try {
                cleanKeys(writer, reader, cacheName, keys);
                assertPubSubUsesDefaultDatasource(writer, reader);

                preloadReaderValue(reader, cacheName, "target", "old-value");
                preloadReaderValue(reader, cacheName, "unrelated", "unchanged-value");
                assertEquals("old-value", reader.l1Cache.get(cacheName, "target"), "reader 应预置目标旧 L1 数据");
                assertEquals("unchanged-value", reader.l1Cache.get(cacheName, "unrelated"), "reader 应预置无关 L1 数据");
                assertOnlyInDatasource(reader, cacheKey(reader, cacheName, "target"), CACHE_DATASOURCE);
                assertOnlyInDatasource(reader, cacheKey(reader, cacheName, "unrelated"), CACHE_DATASOURCE);

                writer.manager.put(cacheName, "target", "new-value");

                assertOnlyInDatasource(writer, cacheKey(writer, cacheName, "target"), CACHE_DATASOURCE);
                assertEquals("new-value", writer.l1Cache.get(cacheName, "target"), "写入实例应保留新 L1");
                assertTrue(waitUntil(() -> reader.l1Cache.get(cacheName, "target") == null, 5),
                        "Redis 3（16379）Pub/Sub 消息应在时限内驱动 peer target L1 精确失效");
                assertEquals("unchanged-value", reader.l1Cache.get(cacheName, "unrelated"),
                        "精确失效不得删除 peer 的无关 L1");
                assertEquals("new-value", reader.manager.get(cacheName, "target"),
                        "peer L1 失效后应从 Redis 5（16380）共享 L2 回读新值");
                assertEquals("new-value", reader.l1Cache.get(cacheName, "target"), "peer 应以新值回填 L1");
                assertEquals("unchanged-value", reader.manager.get(cacheName, "unrelated"),
                        "无关 key 应继续命中 peer L1");
                log.info("验证通过：{} put 完整经过 L2、Pub/Sub 与 peer L1 精确失效链路", topology);
            } finally {
                cleanKeys(writer, reader, cacheName, keys);
            }
        }
    }

    @Test
    @DisplayName("三 standalone Redis 强一致性 putAll：Redis 5（16380）pipeline、Redis 3（16379）Pub/Sub")
    void shouldRoutePutAllThroughStandaloneCacheAndPubSubDatasources() throws Exception {
        assertPutAllRouteTopology("classpath:/application-route-strong-standalone.yml", "standalone", false);
    }

    @Test
    @DisplayName("三 Redis Cluster 强一致性 putAll：Redis 5 Cluster pipeline、Redis 3 Cluster Pub/Sub")
    void shouldRoutePutAllThroughClusterCacheAndPubSubDatasources() throws Exception {
        assertPutAllRouteTopology("classpath:/application-route-strong-cluster.yml", "cluster", true);
    }

    private void assertPutAllRouteTopology(String configLocation, String topology, boolean usesRedis7Cluster) throws Exception {
        this.usesRedis7Cluster = usesRedis7Cluster;
        try (ConfigurableApplicationContext writerContext = startContext("writer", configLocation);
             ConfigurableApplicationContext readerContext = startContext("reader", configLocation)) {
            CacheNode writer = cacheNode(writerContext);
            CacheNode reader = cacheNode(readerContext);
            String cacheName = PUT_ALL_CACHE_NAME;
            List<String> keys = Arrays.asList("first", "second", "null-entry", "unrelated");

            try {
                cleanKeys(writer, reader, cacheName, keys);
                assertPubSubUsesDefaultDatasource(writer, reader);

                preloadReaderValue(reader, cacheName, "first", "old-first");
                preloadReaderValue(reader, cacheName, "second", "old-second");
                preloadReaderValue(reader, cacheName, "null-entry", "old-null-entry");
                preloadReaderValue(reader, cacheName, "unrelated", "unchanged-value");
                assertEquals("old-first", reader.l1Cache.get(cacheName, "first"), "reader 应预置第一条旧 L1 数据");
                assertEquals("old-second", reader.l1Cache.get(cacheName, "second"), "reader 应预置第二条旧 L1 数据");
                assertEquals("old-null-entry", reader.l1Cache.get(cacheName, "null-entry"), "reader 应预置空值更新目标的旧 L1 数据");
                assertEquals("unchanged-value", reader.l1Cache.get(cacheName, "unrelated"), "reader 应预置无关 L1 数据");

                Map<String, Object> updates = new LinkedHashMap<>();
                updates.put("first", "new-first");
                updates.put("second", "new-second");
                updates.put("null-entry", null);
                writer.manager.putAll(cacheName, updates);

                assertOnlyInDatasource(writer, cacheKey(writer, cacheName, "first"), CACHE_DATASOURCE);
                assertOnlyInDatasource(writer, cacheKey(writer, cacheName, "second"), CACHE_DATASOURCE);
                assertOnlyInDatasource(writer, cacheKey(writer, cacheName, "null-entry"), CACHE_DATASOURCE);
                assertEquals("new-first", writer.l1Cache.get(cacheName, "first"), "写入实例应保留第一条新 L1");
                assertEquals("new-second", writer.l1Cache.get(cacheName, "second"), "写入实例应保留第二条新 L1");
                assertTrue(waitUntil(() -> reader.l1Cache.get(cacheName, "first") == null
                        && reader.l1Cache.get(cacheName, "second") == null, 5),
                        "Redis 3（16379）Pub/Sub 应在时限内逐 key 失效 peer 的全部非空目标 L1");
                assertEquals("old-null-entry", reader.l1Cache.get(cacheName, "null-entry"),
                        "空值条目不得覆盖 L2、删除 peer L1 或发布失效消息");
                assertEquals("unchanged-value", reader.l1Cache.get(cacheName, "unrelated"),
                        "批量精确失效不得删除 peer 的无关 L1");
                assertEquals("new-first", reader.manager.get(cacheName, "first"), "第一条应从 Redis 5（16380）共享 L2 回读新值");
                assertEquals("new-second", reader.manager.get(cacheName, "second"), "第二条应从 Redis 5（16380）共享 L2 回读新值");
                reader.l1Cache.evict(cacheName, "null-entry");
                assertEquals("old-null-entry", reader.manager.get(cacheName, "null-entry"),
                        "清除 peer L1 后，空值条目仍应从 Redis 5（16380）保留的旧 L2 回读");
                assertEquals("old-null-entry", reader.l1Cache.get(cacheName, "null-entry"),
                        "空值条目的 L2 回读应恢复 peer L1 旧值");
                assertEquals("unchanged-value", reader.manager.get(cacheName, "unrelated"),
                        "无关 key 应继续命中 peer L1");
                log.info("验证通过：{} putAll 完整经过 pipeline 与 Pub/Sub 逐 key 失效链路", topology);
            } finally {
                cleanKeys(writer, reader, cacheName, keys);
            }
        }
    }

    private ConfigurableApplicationContext startContext(String instanceName, String configLocation) {
        return SpringApplication.run(
                SmartCacheTestApplication.class,
                "--spring.main.banner-mode=off",
                "--spring.main.web-application-type=none",
                "--spring.main.allow-bean-definition-overriding=true",
                "--spring.config.additional-location=" + configLocation,
                "--spring.application.name=strong-write-route-" + instanceName
        );
    }

    private CacheNode cacheNode(ConfigurableApplicationContext context) {
        return new CacheNode(
                context.getBean(SmartCacheManager.class),
                context.getBean(L1Cache.class),
                context.getBean(L2Cache.class),
                context.getBean(SmartCacheProperties.class),
                context.getBean(RedisRouteTemplate.class),
                context.getBean(CacheInvalidationListener.class)
        );
    }

    private void preloadReaderValue(CacheNode reader, String cacheName, String key, String value) {
        reader.l2Cache.put(cacheName, key, value);
        reader.l1Cache.put(cacheName, key, value);
    }

    private void assertPubSubUsesDefaultDatasource(CacheNode writer, CacheNode reader) throws Exception {
        assertNotEquals(writer.invalidationListener.getInstanceId(), reader.invalidationListener.getInstanceId(),
                "两个独立 Context 必须拥有不同的 Pub/Sub sender 标识");
        String probeChannel = KeyHelper.buildPubSubChannel(PUBSUB_PREFIX, ME,
                SmartCacheConstant.PUBSUB_ROUTE_PROBE_KEY);
        assertPubSubUsesDefaultDatasource(writer, probeChannel, "writer");
        assertPubSubUsesDefaultDatasource(reader, probeChannel, "reader");
    }

    private void assertPubSubUsesDefaultDatasource(CacheNode node, String probeChannel, String nodeName) throws Exception {
        RedisConnectionFactory expected = node.redisRouteTemplate.connectionFactory(DEFAULT_DATASOURCE);
        assertSame(expected, node.redisRouteTemplate.connectionFactoryByKey(probeChannel),
                nodeName + " 的 Pub/Sub probe 必须路由到 db0/default datasource");
        assertSame(expected, readListenerConnectionFactory(node.invalidationListener),
                nodeName + " 的 listener container 必须绑定 db0/default connection factory");
    }

    private RedisConnectionFactory readListenerConnectionFactory(CacheInvalidationListener listener) throws Exception {
        Field containerField = CacheInvalidationListener.class.getDeclaredField("listenerContainer");
        containerField.setAccessible(true);
        Object container = containerField.get(listener);
        assertNotNull(container, "强一致性模式必须创建 Pub/Sub listener container");
        Field connectionFactoryField = container.getClass().getDeclaredField("connectionFactory");
        connectionFactoryField.setAccessible(true);
        return (RedisConnectionFactory) connectionFactoryField.get(container);
    }

    private String cacheKey(CacheNode node, String cacheName, String key) {
        return KeyHelper.buildCacheKey(node.properties.getL2().getKeyFormat(), node.properties.getKeyPrefix(),
                cacheName, node.properties.getMe(), key);
    }

    private void cleanKeys(CacheNode writer, CacheNode reader, String cacheName, List<String> keys) {
        writer.l1Cache.clear(cacheName);
        reader.l1Cache.clear(cacheName);
        for (String key : keys) {
            deleteThroughRouteAndAssertAbsent(writer, cacheKey(writer, cacheName, key));
        }
    }

    private void deleteThroughRouteAndAssertAbsent(CacheNode node, String redisKey) {
        node.redisRouteTemplate.execute(redisKey, template -> {
            template.delete(redisKey);
            return null;
        });
        assertAbsentFromAllDatasources(node, redisKey);
    }

    private void assertOnlyInDatasource(CacheNode node, String redisKey, String expectedDatasource) {
        for (String datasource : assertionDatasources(node)) {
            String value = template(node, datasource).opsForValue().get(redisKey);
            if (expectedDatasource.equals(datasource)) {
                assertNotNull(value, "Redis key 应存在于 datasource=" + datasource + "，key=" + redisKey);
            } else {
                assertNull(value, "Redis key 不应存在于 datasource=" + datasource + "，key=" + redisKey);
            }
        }
    }

    private void assertAbsentFromAllDatasources(CacheNode node, String redisKey) {
        for (String datasource : assertionDatasources(node)) {
            assertNull(template(node, datasource).opsForValue().get(redisKey),
                    "Redis key 不应残留在 datasource=" + datasource + "，key=" + redisKey);
        }
    }

    private List<String> assertionDatasources(CacheNode node) {
        return isUnsupportedRedis7ClusterTopology(node) ? NON_LOCK_DATASOURCES : DATASOURCES;
    }

    private boolean isUnsupportedRedis7ClusterTopology(CacheNode node) {
        return usesRedis7Cluster && "2.2.x".equals(System.getProperty("spring.profiles.active"));
    }

    private StringRedisTemplate template(CacheNode node, String datasource) {
        return node.redisRouteTemplate.stringTemplate(datasource);
    }

    private boolean waitUntil(BooleanSupplier condition, int timeoutSeconds) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return condition.getAsBoolean();
    }

    private static class CacheNode {
        private final SmartCacheManager manager;
        private final L1Cache l1Cache;
        private final L2Cache l2Cache;
        private final SmartCacheProperties properties;
        private final RedisRouteTemplate redisRouteTemplate;
        private final CacheInvalidationListener invalidationListener;

        private CacheNode(SmartCacheManager manager, L1Cache l1Cache, L2Cache l2Cache,
                          SmartCacheProperties properties, RedisRouteTemplate redisRouteTemplate,
                          CacheInvalidationListener invalidationListener) {
            this.manager = manager;
            this.l1Cache = l1Cache;
            this.l2Cache = l2Cache;
            this.properties = properties;
            this.redisRouteTemplate = redisRouteTemplate;
            this.invalidationListener = invalidationListener;
        }
    }
}
