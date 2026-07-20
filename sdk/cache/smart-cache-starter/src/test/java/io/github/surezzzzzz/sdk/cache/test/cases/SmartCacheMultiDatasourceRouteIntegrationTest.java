package io.github.surezzzzzz.sdk.cache.test.cases;

import io.github.surezzzzzz.sdk.cache.CachePreloadHandler;
import io.github.surezzzzzz.sdk.cache.annotation.SmartCacheWarmUp;
import io.github.surezzzzzz.sdk.cache.configuration.SmartCacheProperties;
import io.github.surezzzzzz.sdk.cache.constant.ErrorCode;
import io.github.surezzzzzz.sdk.cache.constant.SmartCacheConstant;
import io.github.surezzzzzz.sdk.cache.exception.CacheRouteException;
import io.github.surezzzzzz.sdk.cache.layer.L1Cache;
import io.github.surezzzzzz.sdk.cache.layer.L2Cache;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import io.github.surezzzzzz.sdk.cache.pubsub.CacheInvalidationListener;
import io.github.surezzzzzz.sdk.cache.warmup.SmartCacheWarmUpProcessor;
import io.github.surezzzzzz.sdk.cache.support.KeyHelper;
import io.github.surezzzzzz.sdk.cache.test.BaseSmartCacheTest;
import io.github.surezzzzzz.sdk.cache.test.SmartCacheTestApplication;
import io.github.surezzzzzz.sdk.lock.redis.SimpleRedisLock;
import io.github.surezzzzzz.sdk.lock.redis.model.RedisLockLease;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smart Cache 真实多数据源路由集成测试
 *
 * <p>使用独立 Redis 3（16379/default）、Redis 5（16380/cache）、Redis 7（16381/lock）
 * 验证真实实例路由落点，避免只以 API 返回值推断路由行为。
 *
 * @author surezzzzzz
 */
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class SmartCacheMultiDatasourceRouteIntegrationTest extends BaseSmartCacheTest {

    private static final String KEY_PREFIX = "smart-cache-route-it";
    private static final String ME = "route-it";
    private static final String PUBSUB_PREFIX = "route-cache-sync";
    private static final String DEFAULT_DATASOURCE = "default";
    private static final String CACHE_DATASOURCE = "cache";
    private static final String LOCK_DATASOURCE = "lock";
    private static final String WARMUP_CACHE = "route-warmup";
    private static final String SCAN_CACHE = "route-scan";
    private static final String OTHER_ME = "route-other-it";
    private static final int SCAN_ENTRY_COUNT = 101;
    private static final List<String> DATASOURCES = Arrays.asList(
            DEFAULT_DATASOURCE, CACHE_DATASOURCE, LOCK_DATASOURCE);
    private static final List<String> NON_LOCK_DATASOURCES = Arrays.asList(
            DEFAULT_DATASOURCE, CACHE_DATASOURCE);

    @Autowired
    private SmartCacheManager cacheManager;

    @Autowired
    private L1Cache l1Cache;

    @Autowired
    private L2Cache l2Cache;

    @Autowired
    private SmartCacheProperties properties;

    @Autowired
    private RedisRouteTemplate redisRouteTemplate;

    @Autowired
    private SimpleRedisLock redisLock;

    @Autowired
    private CacheInvalidationListener invalidationListener;

    @Autowired
    private SmartCacheWarmUpProcessor warmUpProcessor;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private RoutePreloadHandler preloadHandler;

    @BeforeEach
    void setUp() {
        requireRedisAvailable();
        preloadHandler.reset();
    }

    @AfterEach
    void tearDown() {
        preloadHandler.releaseReload();
        cleanupCache("route-single", "data-key");
        cleanupCache("route-batch", "first-key", "second-key");
        cleanupCache("route-cross", "default-key", "cache-key");
        cleanupCache("route-lock", "lock-key");
        cleanupCache("route-preload", "preload-key");
        cleanupScanCache();
        if (!isUnsupportedRedis7ClusterTopology()) {
            deleteFromAllDatasources(KeyHelper.buildLockKey(KEY_PREFIX, "route-lease", ME, "lease-key"));
        }
    }

    @AfterAll
    void cleanUpWarmupData() {
        cleanupWarmupKeys();
    }

    private void skipRedis7ClusterLockOnSpringBoot22() {
        Assumptions.assumeFalse(isUnsupportedRedis7ClusterTopology(),
                "Spring Boot 2.2.x 的 Lettuce 5.2.2.RELEASE 不支持 Redis 7.2.6 Cluster，仅跳过依赖 Redis 7 Cluster 锁的 E2E");
    }

    private boolean isUnsupportedRedis7ClusterTopology() {
        return usesRedis7Cluster() && "2.2.x".equals(System.getProperty("spring.profiles.active"));
    }

    protected abstract boolean usesRedis7Cluster();

    @Test
    @DisplayName("单键 L2 数据仅路由到 cache datasource，读取、TTL 与删除保持同源")
    void shouldRouteSingleL2KeyToCacheDatasource() {
        String cacheName = "route-single";
        String key = "data-key";
        String redisKey = cacheKey(cacheName, key);

        l2Cache.put(cacheName, key, "route-value");

        assertOnlyInDatasource(redisKey, CACHE_DATASOURCE);
        assertEquals("route-value", l2Cache.get(cacheName, key, String.class), "应从 cache datasource 读回 L2 值");
        assertTrue(l2Cache.getTtl(cacheName, key) > 0, "TTL 查询应路由到 cache datasource 并返回正值");

        l2Cache.evict(cacheName, key);
        assertAbsentFromAllDatasources(redisKey);
    }

    @Test
    @DisplayName("同源批量读写通过真实 collection route 与 pipeline，仅写入 cache datasource")
    void shouldRouteSameDatasourceBatchToCacheDatasource() {
        String cacheName = "route-batch";
        Map<String, Object> entries = new LinkedHashMap<>();
        entries.put("first-key", "first-value");
        entries.put("second-key", "second-value");

        l2Cache.putAll(cacheName, entries);

        String firstRedisKey = cacheKey(cacheName, "first-key");
        String secondRedisKey = cacheKey(cacheName, "second-key");
        assertOnlyInDatasource(firstRedisKey, CACHE_DATASOURCE);
        assertOnlyInDatasource(secondRedisKey, CACHE_DATASOURCE);

        Map<String, String> values = l2Cache.getAll(cacheName,
                Arrays.asList("first-key", "second-key"), String.class);
        assertEquals("first-value", values.get("first-key"), "批量读取应返回第一条数据");
        assertEquals("second-value", values.get("second-key"), "批量读取应返回第二条数据");
        assertTrue(template(CACHE_DATASOURCE).getExpire(firstRedisKey, TimeUnit.SECONDS) > 0,
                "pipeline 写入的第一条数据应保留 TTL");
        assertTrue(template(CACHE_DATASOURCE).getExpire(secondRedisKey, TimeUnit.SECONDS) > 0,
                "pipeline 写入的第二条数据应保留 TTL");
    }

    @Test
    @DisplayName("跨 datasource 批量读写由真实 route 在 callback 前拒绝，且没有部分写入")
    void shouldRejectCrossDatasourceBatchWithoutPartialWrite() {
        String cacheName = "route-cross";
        String defaultKey = "default-key";
        String cacheKey = "cache-key";
        String defaultRedisKey = cacheKey(cacheName, defaultKey);
        String cacheRedisKey = cacheKey(cacheName, cacheKey);

        Map<String, Object> entries = new LinkedHashMap<>();
        entries.put(defaultKey, "default-value");
        entries.put(cacheKey, "cache-value");

        CacheRouteException putException = assertThrows(CacheRouteException.class,
                () -> l2Cache.putAll(cacheName, entries),
                "跨 datasource 批量写入必须被真实 route 拒绝");
        assertEquals(ErrorCode.SMART_CACHE_ROUTE_CROSS_DATASOURCE, putException.getErrorCode(),
                "跨 datasource 批量写入应使用 SMART_CACHE_003");
        assertAbsentFromAllDatasources(defaultRedisKey);
        assertAbsentFromAllDatasources(cacheRedisKey);

        CacheRouteException getException = assertThrows(CacheRouteException.class,
                () -> l2Cache.getAll(cacheName, Arrays.asList(defaultKey, cacheKey), String.class),
                "跨 datasource 批量读取必须被真实 route 拒绝");
        assertEquals(ErrorCode.SMART_CACHE_ROUTE_CROSS_DATASOURCE, getException.getErrorCode(),
                "跨 datasource 批量读取应使用 SMART_CACHE_003");
    }

    @Test
    @DisplayName("缓存击穿锁仅路由到 lock datasource，释放后 L2 数据仍在 cache datasource")
    void shouldRouteCacheStampedeLockToLockDatasource() throws Exception {
        skipRedis7ClusterLockOnSpringBoot22();
        String cacheName = "route-lock";
        String key = "lock-key";
        String lockKey = KeyHelper.buildLockKey(KEY_PREFIX, cacheName, ME, key);
        CountDownLatch loaderStarted = new CountDownLatch(1);
        CountDownLatch releaseLoader = new CountDownLatch(1);
        FutureTask<String> task = new FutureTask<>(() -> cacheManager.get(cacheName, key, () -> {
            loaderStarted.countDown();
            assertTrue(releaseLoader.await(5, TimeUnit.SECONDS), "测试应在超时前释放 loader");
            return "loaded-value";
        }));
        Thread thread = new Thread(task, "route-lock-test");
        thread.start();

        assertTrue(loaderStarted.await(5, TimeUnit.SECONDS), "loader 应在持有分布式锁后开始执行");
        assertOnlyInDatasource(lockKey, LOCK_DATASOURCE);

        releaseLoader.countDown();
        assertEquals("loaded-value", task.get(5, TimeUnit.SECONDS), "加载结果应正常返回");
        assertAbsentFromAllDatasources(lockKey);
        assertOnlyInDatasource(cacheKey(cacheName, key), CACHE_DATASOURCE);
    }

    @Test
    @DisplayName("显式租约获取、续租和关闭均路由到 lock datasource")
    void shouldRouteExplicitLeaseLifecycleToLockDatasource() {
        skipRedis7ClusterLockOnSpringBoot22();
        String lockKey = KeyHelper.buildLockKey(KEY_PREFIX, "route-lease", ME, "lease-key");
        Optional<RedisLockLease> optionalLease = redisLock.tryLockWithLease(
                lockKey, SmartCacheConstant.MIN_LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        log.info("显式 lease 获取结果：{}，lockKey：{}", optionalLease.isPresent(), lockKey);
        assertTrue(optionalLease.isPresent(), "应成功获取真实路由租约");
        RedisLockLease lease = optionalLease.get();
        try {
            log.info("验证显式 lease 获取后的 Redis 路由，lockKey：{}，预期 datasource：{}",
                    lockKey, LOCK_DATASOURCE);
            assertOnlyInDatasource(lockKey, LOCK_DATASOURCE);
            boolean renewed = lease.renew(SmartCacheConstant.MIN_LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.info("显式 lease 续租结果：{}，lockKey：{}，预期 datasource：{}",
                    renewed, lockKey, LOCK_DATASOURCE);
            assertTrue(renewed, "同一 lease 应在 lock datasource 上续租成功");
            assertOnlyInDatasource(lockKey, LOCK_DATASOURCE);
        } finally {
            lease.close();
        }

        log.info("显式 lease 关闭完成，验证 Redis key 已清理，lockKey：{}", lockKey);
        assertAbsentFromAllDatasources(lockKey);
    }

    @Test
    @DisplayName("预刷新锁仅路由到 lock datasource，reload 完成后的数据写回 cache datasource")
    void shouldRoutePreloadLockToLockDatasource() throws Exception {
        skipRedis7ClusterLockOnSpringBoot22();
        String cacheName = "route-preload";
        String key = "preload-key";
        String lockKey = KeyHelper.buildPreloadLockKey(KEY_PREFIX, cacheName, ME, key);

        preloadHandler.prepareBlockingReload("refreshed-value");
        String first = cacheManager.get(cacheName, key, () -> "old-value");
        assertEquals("old-value", first, "首次读取应加载旧值");
        l1Cache.evict(cacheName, key);

        String second = cacheManager.get(cacheName, key, () -> "unexpected-loader-value");
        assertEquals("old-value", second, "L2 命中触发预刷新时当前请求应继续返回旧值");
        assertTrue(preloadHandler.awaitReloadStarted(), "reload 应已在持有预刷新锁后开始执行");
        assertOnlyInDatasource(lockKey, LOCK_DATASOURCE);
        String otherGroupLockKey = KeyHelper.buildPreloadLockKey(KEY_PREFIX, cacheName, OTHER_ME, key);
        Optional<RedisLockLease> otherGroupLease = redisLock.tryLockWithLease(otherGroupLockKey,
                SmartCacheConstant.MIN_LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertTrue(otherGroupLease.isPresent(), "不同 me 的预刷新锁必须可与当前组锁同时持有");
        otherGroupLease.get().close();
        assertAbsentFromAllDatasources(otherGroupLockKey);

        preloadHandler.releaseReload();
        assertTrue(preloadHandler.awaitReloadFinished(), "释放后 reload 应完成");
        assertTrue(awaitPreloadCompletion(cacheName, key, lockKey), "预刷新应完成写入并释放分布式锁");
        assertAbsentFromAllDatasources(lockKey);
        assertEquals("refreshed-value", l2Cache.get(cacheName, key, String.class), "预刷新结果应写入 L2");
        assertOnlyInDatasource(cacheKey(cacheName, key), CACHE_DATASOURCE);
    }

    @Test
    @DisplayName("启动预热数据与完成元数据路由到 cache datasource")
    void shouldRouteWarmupDataAndMetadataToCacheDatasource() {
        skipRedis7ClusterLockOnSpringBoot22();
        String firstDataKey = cacheKey(WARMUP_CACHE, "warmup-first");
        String secondDataKey = cacheKey(WARMUP_CACHE, "warmup-second");
        String keysKey = KeyHelper.buildWarmUpMetadataKey(KEY_PREFIX, WARMUP_CACHE, ME,
                SmartCacheConstant.WARMUP_KEYS_KEY_SUFFIX);
        String completeKey = KeyHelper.buildWarmUpMetadataKey(KEY_PREFIX, WARMUP_CACHE, ME,
                SmartCacheConstant.WARMUP_COMPLETE_KEY_SUFFIX);

        assertOnlyInDatasource(firstDataKey, CACHE_DATASOURCE);
        assertOnlyInDatasource(secondDataKey, CACHE_DATASOURCE);
        assertOnlyInDatasource(keysKey, CACHE_DATASOURCE);
        assertOnlyInDatasource(completeKey, CACHE_DATASOURCE);
        assertEquals("warmup-value-1", l2Cache.get(WARMUP_CACHE, "warmup-first", String.class),
                "预热第一条数据应可从 L2 读回");
        assertEquals("warmup-value-2", l2Cache.get(WARMUP_CACHE, "warmup-second", String.class),
                "预热第二条数据应可从 L2 读回");
    }

    @Test
    @DisplayName("关闭 SCAN 时仅清理 L1，L2 数据保留且 size 返回零")
    void shouldSkipL2ScanWhenDisabled() {
        String cacheName = "route-scan-disabled";
        String key = "scan-disabled-key";
        String redisKey = cacheKey(cacheName, key);
        Boolean originalScanEnabled = properties.getRoute().getScanEnabled();
        try {
            cacheManager.put(cacheName, key, "scan-disabled-value");
            assertOnlyInDatasource(redisKey, CACHE_DATASOURCE);
            assertEquals(1, l1Cache.size(cacheName), "写入后 L1 应包含测试数据");

            properties.getRoute().setScanEnabled(false);
            cacheManager.clear(cacheName);

            assertEquals(0, l1Cache.size(cacheName), "关闭 SCAN 后 clear 仍必须清理 L1");
            assertOnlyInDatasource(redisKey, CACHE_DATASOURCE);
            assertEquals(0, l2Cache.size(cacheName), "关闭 SCAN 后 L2 size 必须返回零");
        } finally {
            properties.getRoute().setScanEnabled(originalScanEnabled);
            deleteFromAllDatasources(redisKey);
            l1Cache.clear(cacheName);
        }
    }

    @Test
    @DisplayName("不同 me 的预热元数据可同时存在且不互相覆盖")
    void shouldKeepWarmupMetadataIsolatedAcrossGroups() {
        skipRedis7ClusterLockOnSpringBoot22();
        String originalMe = properties.getMe();
        String currentKeysKey = KeyHelper.buildWarmUpMetadataKey(KEY_PREFIX, WARMUP_CACHE, ME,
                SmartCacheConstant.WARMUP_KEYS_KEY_SUFFIX);
        String currentCompleteKey = KeyHelper.buildWarmUpMetadataKey(KEY_PREFIX, WARMUP_CACHE, ME,
                SmartCacheConstant.WARMUP_COMPLETE_KEY_SUFFIX);
        String otherKeysKey = KeyHelper.buildWarmUpMetadataKey(KEY_PREFIX, WARMUP_CACHE, OTHER_ME,
                SmartCacheConstant.WARMUP_KEYS_KEY_SUFFIX);
        String otherCompleteKey = KeyHelper.buildWarmUpMetadataKey(KEY_PREFIX, WARMUP_CACHE, OTHER_ME,
                SmartCacheConstant.WARMUP_COMPLETE_KEY_SUFFIX);
        try {
            assertOnlyInDatasource(currentKeysKey, CACHE_DATASOURCE);
            assertOnlyInDatasource(currentCompleteKey, CACHE_DATASOURCE);

            properties.setMe(OTHER_ME);
            warmUpProcessor.onApplicationEvent(new ContextRefreshedEvent(applicationContext));

            assertOnlyInDatasource(otherKeysKey, CACHE_DATASOURCE);
            assertOnlyInDatasource(otherCompleteKey, CACHE_DATASOURCE);
            assertOnlyInDatasource(currentKeysKey, CACHE_DATASOURCE);
            assertOnlyInDatasource(currentCompleteKey, CACHE_DATASOURCE);
            assertOnlyInDatasource(cacheKey(WARMUP_CACHE, OTHER_ME, "warmup-first"), CACHE_DATASOURCE);
            assertOnlyInDatasource(cacheKey(WARMUP_CACHE, OTHER_ME, "warmup-second"), CACHE_DATASOURCE);
        } finally {
            properties.setMe(originalMe);
            deleteFromAllDatasources(cacheKey(WARMUP_CACHE, OTHER_ME, "warmup-first"));
            deleteFromAllDatasources(cacheKey(WARMUP_CACHE, OTHER_ME, "warmup-second"));
            deleteFromAllDatasources(otherKeysKey);
            deleteFromAllDatasources(otherCompleteKey);
            deleteFromAllDatasources(KeyHelper.buildLockKey(KEY_PREFIX, WARMUP_CACHE, OTHER_ME,
                    SmartCacheConstant.WARMUP_LOCK_KEY));
        }
    }

    @Test
    @DisplayName("真实 SCAN 跨批次仅统计并清理当前 me 的 L2 数据，保留其他组数据")
    void shouldScanAndClearOnlyCurrentGroupL2Data() {
        String otherKey = "other-key";
        String firstRedisKey = cacheKey(SCAN_CACHE, scanKey(0));
        String lastRedisKey = cacheKey(SCAN_CACHE, scanKey(SCAN_ENTRY_COUNT - 1));
        String otherRedisKey = cacheKey(SCAN_CACHE, OTHER_ME, otherKey);

        for (int index = 0; index < SCAN_ENTRY_COUNT; index++) {
            cacheManager.put(SCAN_CACHE, scanKey(index), "value-" + index);
        }
        template(CACHE_DATASOURCE).opsForValue().set(otherRedisKey, "other-value");
        log.info("验证跨 SCAN 批次的当前 me 边界，当前首尾 key：{}、{}，其他组 key：{}",
                firstRedisKey, lastRedisKey, otherRedisKey);

        assertOnlyInDatasource(firstRedisKey, CACHE_DATASOURCE);
        assertOnlyInDatasource(lastRedisKey, CACHE_DATASOURCE);
        assertOnlyInDatasource(otherRedisKey, CACHE_DATASOURCE);
        assertEquals(SCAN_ENTRY_COUNT, l2Cache.size(SCAN_CACHE), "SCAN size 必须统计当前 me 的全部跨批次 L2 数据");
        assertEquals(SCAN_ENTRY_COUNT, l1Cache.size(SCAN_CACHE), "写入后当前组 L1 应保留全部跨批次数据");

        cacheManager.clear(SCAN_CACHE);
        log.info("验证 clear 后当前 me 的跨批次 L1/L2 已清理，其他组 L2 key 保留：{}", otherRedisKey);

        for (int index = 0; index < SCAN_ENTRY_COUNT; index++) {
            assertAbsentFromAllDatasources(cacheKey(SCAN_CACHE, scanKey(index)));
        }
        assertOnlyInDatasource(otherRedisKey, CACHE_DATASOURCE);
        assertEquals(0, l2Cache.size(SCAN_CACHE), "clear 后当前 me 的跨批次 L2 SCAN size 应归零");
        assertEquals(0, l1Cache.size(SCAN_CACHE), "clear 后当前组的 L1 应清空");
    }

    @Test
    @DisplayName("Pub/Sub probe 的 listener connection factory 与 default datasource 完全同源")
    void shouldBindPubSubListenerToProbeDatasource() throws Exception {
        String probeChannel = KeyHelper.buildPubSubChannel(PUBSUB_PREFIX, ME,
                SmartCacheConstant.PUBSUB_ROUTE_PROBE_KEY);
        RedisConnectionFactory expected = redisRouteTemplate.connectionFactory(DEFAULT_DATASOURCE);

        assertSame(expected, redisRouteTemplate.connectionFactoryByKey(probeChannel),
                "probe channel 应路由到 default datasource 的 connection factory");
        assertSame(expected, readListenerConnectionFactory(),
                "listener container 应使用 probe 命中的 default connection factory");
    }

    private boolean awaitPreloadCompletion(String cacheName, String key, String lockKey) throws InterruptedException {
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        String redisKey = cacheKey(cacheName, key);
        while (System.nanoTime() < deadlineNanos) {
            String refreshedPayload = template(CACHE_DATASOURCE).opsForValue().get(redisKey);
            boolean lockReleased = template(LOCK_DATASOURCE).opsForValue().get(lockKey) == null;
            if (refreshedPayload != null && lockReleased) {
                return true;
            }
            TimeUnit.MILLISECONDS.sleep(20);
        }
        return false;
    }

    private String cacheKey(String cacheName, String key) {
        return cacheKey(cacheName, properties.getMe(), key);
    }

    private String cacheKey(String cacheName, String me, String key) {
        return KeyHelper.buildCacheKey(properties.getL2().getKeyFormat(), properties.getKeyPrefix(),
                cacheName, me, key);
    }

    private StringRedisTemplate template(String datasource) {
        return redisRouteTemplate.stringTemplate(datasource);
    }

    private void assertOnlyInDatasource(String redisKey, String expectedDatasource) {
        for (String datasource : assertionDatasources()) {
            String actual = template(datasource).opsForValue().get(redisKey);
            if (expectedDatasource.equals(datasource)) {
                assertNotNull(actual, "Redis key 应存在于 datasource=" + datasource + "，key=" + redisKey);
            } else {
                assertNull(actual, "Redis key 不应存在于 datasource=" + datasource + "，key=" + redisKey);
            }
        }
    }

    private void assertAbsentFromAllDatasources(String redisKey) {
        for (String datasource : assertionDatasources()) {
            assertNull(template(datasource).opsForValue().get(redisKey),
                    "Redis key 不应残留在 datasource=" + datasource + "，key=" + redisKey);
        }
    }

    private List<String> assertionDatasources() {
        return isUnsupportedRedis7ClusterTopology() ? NON_LOCK_DATASOURCES : DATASOURCES;
    }

    private void cleanupCache(String cacheName, String... keys) {
        l1Cache.clear(cacheName);
        for (String key : keys) {
            deleteFromAllDatasources(cacheKey(cacheName, key));
        }
    }

    private void cleanupScanCache() {
        l1Cache.clear(SCAN_CACHE);
        for (int index = 0; index < SCAN_ENTRY_COUNT; index++) {
            deleteFromAllDatasources(cacheKey(SCAN_CACHE, scanKey(index)));
        }
        deleteFromAllDatasources(cacheKey(SCAN_CACHE, OTHER_ME, "other-key"));
    }

    private String scanKey(int index) {
        return "scan-key-" + index;
    }

    private void cleanupWarmupKeys() {
        l1Cache.clear(WARMUP_CACHE);
        deleteFromAllDatasources(cacheKey(WARMUP_CACHE, "warmup-first"));
        deleteFromAllDatasources(cacheKey(WARMUP_CACHE, "warmup-second"));
        deleteFromAllDatasources(KeyHelper.buildWarmUpMetadataKey(KEY_PREFIX, WARMUP_CACHE, ME,
                SmartCacheConstant.WARMUP_KEYS_KEY_SUFFIX));
        deleteFromAllDatasources(KeyHelper.buildWarmUpMetadataKey(KEY_PREFIX, WARMUP_CACHE, ME,
                SmartCacheConstant.WARMUP_COMPLETE_KEY_SUFFIX));
        if (!isUnsupportedRedis7ClusterTopology()) {
            deleteFromAllDatasources(KeyHelper.buildLockKey(KEY_PREFIX, WARMUP_CACHE, ME,
                    SmartCacheConstant.WARMUP_LOCK_KEY));
        }
    }

    private void deleteFromAllDatasources(String redisKey) {
        redisRouteTemplate.execute(redisKey, template -> {
            template.delete(redisKey);
            return null;
        });
        assertAbsentFromAllDatasources(redisKey);
    }

    private RedisConnectionFactory readListenerConnectionFactory() throws Exception {
        Field containerField = CacheInvalidationListener.class.getDeclaredField("listenerContainer");
        containerField.setAccessible(true);
        Object container = containerField.get(invalidationListener);
        assertNotNull(container, "强一致性模式应创建 Pub/Sub listener container");
        Field connectionFactoryField = container.getClass().getDeclaredField("connectionFactory");
        connectionFactoryField.setAccessible(true);
        return (RedisConnectionFactory) connectionFactoryField.get(container);
    }

    @TestConfiguration
    public static class RouteFixtureConfiguration {

        @Bean
        RoutePreloadHandler routePreloadHandler() {
            return new RoutePreloadHandler();
        }

        @Bean
        RouteWarmUpFixture routeWarmUpFixture() {
            return new RouteWarmUpFixture();
        }
    }

    static class RoutePreloadHandler implements CachePreloadHandler {

        private final AtomicReference<String> nextValue = new AtomicReference<>("refreshed-value");
        private volatile CountDownLatch reloadStarted = new CountDownLatch(1);
        private volatile CountDownLatch releaseReload = new CountDownLatch(1);
        private volatile CountDownLatch reloadFinished = new CountDownLatch(1);
        private volatile boolean blockReload;

        @Override
        public boolean support(String cacheName) {
            return "route-preload".equals(cacheName);
        }

        @Override
        public Object reload(String cacheName, String key) {
            reloadStarted.countDown();
            try {
                if (blockReload) {
                    assertTrue(releaseReload.await(5, TimeUnit.SECONDS), "测试应在超时前释放 reload");
                }
                return nextValue.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("预刷新测试线程被中断", e);
            } finally {
                reloadFinished.countDown();
            }
        }

        @Override
        public Optional<Boolean> needPreload(String cacheName, String key, Object cachedValue) {
            return Optional.of(true);
        }

        void prepareBlockingReload(String value) {
            nextValue.set(value);
            blockReload = true;
            reloadStarted = new CountDownLatch(1);
            releaseReload = new CountDownLatch(1);
            reloadFinished = new CountDownLatch(1);
        }

        boolean awaitReloadStarted() throws InterruptedException {
            return reloadStarted.await(5, TimeUnit.SECONDS);
        }

        boolean awaitReloadFinished() throws InterruptedException {
            return reloadFinished.await(5, TimeUnit.SECONDS);
        }

        void releaseReload() {
            releaseReload.countDown();
        }

        void reset() {
            nextValue.set("refreshed-value");
            blockReload = false;
            reloadStarted = new CountDownLatch(1);
            releaseReload = new CountDownLatch(1);
            reloadFinished = new CountDownLatch(1);
        }
    }

    static class RouteWarmUpFixture {

        @SmartCacheWarmUp(cacheName = WARMUP_CACHE, order = 100)
        public Map<String, Object> loadRouteWarmupData() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("warmup-first", "warmup-value-1");
            data.put("warmup-second", "warmup-value-2");
            return data;
        }
    }
}
