package io.github.surezzzzzz.sdk.retry.redis.smart.test.cases;

import io.github.surezzzzzz.sdk.redis.route.model.RedisServerInfo;
import io.github.surezzzzzz.sdk.redis.route.registry.SimpleRedisRouteRegistry;
import io.github.surezzzzzz.sdk.redis.route.support.RedisCommandCapabilityHelper;
import io.github.surezzzzzz.sdk.redis.route.support.RedisServerVersionHelper;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import io.github.surezzzzzz.sdk.retry.redis.smart.constant.RetryDecisionType;
import io.github.surezzzzzz.sdk.retry.redis.smart.engine.SmartRedisRetryEngine;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryDecision;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryInfo;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryScanRequest;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryScanResult;
import io.github.surezzzzzz.sdk.retry.redis.smart.support.RetryKeyHelper;
import io.github.surezzzzzz.sdk.retry.redis.smart.test.SmartRedisRetryTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smart Redis Retry 多版本矩阵端到端测试（复用 route 的 Docker Redis 3/5/7 矩阵）。
 *
 * @author surezzzzzz
 */
@Slf4j
@ActiveProfiles("smart-retry-version-matrix")
@SpringBootTest(classes = SmartRedisRetryTestApplication.class)
public class SmartRedisRetryMultiVersionMatrixEndToEndTest {

    private static final String REDIS7_CLUSTER = "redis7Cluster";
    private static final String MULTI_CLUSTER_BUSINESS_KEY_PREFIX = "business-retry-";
    private static final int MULTI_CLUSTER_RECORD_COUNT = 12;

    private static final List<MatrixRetryCase> RETRY_CASES = Arrays.asList(
            new MatrixRetryCase("redis3Standalone", "retry-redis3-standalone", 3, "standalone"),
            new MatrixRetryCase("redis5Standalone", "retry-redis5-standalone", 5, "standalone"),
            new MatrixRetryCase("redis7Standalone", "retry-redis7-standalone", 7, "standalone"),
            new MatrixRetryCase("redis3Cluster", "retry-business-order-compensation", 3, "cluster"),
            new MatrixRetryCase("redis5Cluster", "retry-business-inventory-compensation", 5, "cluster"),
            new MatrixRetryCase("redis7Cluster", "retry-business-notification-compensation", 7, "cluster"));

    @Autowired
    private SmartRedisRetryEngine engine;

    @Autowired
    private RedisRouteTemplate redisRouteTemplate;

    @Autowired
    private SimpleRedisRouteRegistry registry;

    @Autowired
    private RetryKeyHelper retryKeyHelper;

    @AfterEach
    public void cleanUp() {
        log.info("清理矩阵测试资源");
        for (MatrixRetryCase retryCase : activeRetryCases()) {
            redisRouteTemplate.executeOn(retryCase.datasourceKey, template -> {
                template.delete(retryKeyHelper.buildRedisKey(retryCase.retryType, retryCase.retryKey("matrix")));
                template.delete(retryKeyHelper.buildRedisKey(retryCase.retryType, retryCase.retryKey("decide")));
                template.delete(retryKeyHelper.buildRedisKey(retryCase.retryType, retryCase.retryKey("clear")));
                template.delete(retryKeyHelper.buildRedisKey(retryCase.retryType, retryCase.retryKey("scan")));
                template.delete(retryKeyHelper.buildRedisKey(retryCase.retryType, retryCase.retryKey("cursor-out-of-range")));
                for (int i = 0; i < 40; i++) {
                    template.delete(retryKeyHelper.buildRedisKey(retryCase.retryType, retryCase.retryKey("cluster-scan-" + i)));
                }
                for (int i = 0; i < MULTI_CLUSTER_RECORD_COUNT; i++) {
                    template.delete(retryKeyHelper.buildRedisKey(retryCase.retryType,
                            multiClusterRetryKey(retryCase, i)));
                }
                return null;
            });
        }
    }

    @Test
    void registryShouldContainAndProbeAllMatrixDatasources() {
        Set<String> datasourceKeys = registry.getDatasourceKeys();
        Set<String> expectedKeys = new HashSet<String>();
        for (MatrixRetryCase retryCase : RETRY_CASES) {
            expectedKeys.add(retryCase.datasourceKey);
            assertTrue(datasourceKeys.contains(retryCase.datasourceKey), "缺少 datasource: " + retryCase.datasourceKey);
        }
        assertTrue(datasourceKeys.containsAll(expectedKeys), "矩阵 datasource 必须全部注册: " + expectedKeys);

        for (MatrixRetryCase retryCase : activeRetryCases()) {
            RedisServerInfo serverInfo = redisRouteTemplate.serverInfo(retryCase.datasourceKey);
            assertTrue(serverInfo.isKnown(), "route 必须探测到 datasource=" + retryCase.datasourceKey
                    + ", error=" + serverInfo.getErrorMessage());
            assertNull(serverInfo.getErrorMessage(), "探测成功时不应有错误信息: " + retryCase.datasourceKey);
            assertNotNull(serverInfo.getVersion(), "探测成功时必须有版本号: " + retryCase.datasourceKey);
            assertEquals(retryCase.redisMajor, serverInfo.getVersion().getMajor(), "Redis 主版本不匹配: " + retryCase.datasourceKey);
            assertEquals(retryCase.redisMode, serverInfo.getRedisMode(), "Redis 模式不匹配: " + retryCase.datasourceKey);
        }

        if (isRedis7ClusterExempt()) {
            RedisServerInfo serverInfo = redisRouteTemplate.serverInfo(REDIS7_CLUSTER);
            assertFalse(serverInfo.isKnown(), "Spring Boot 2.2 默认依赖的 Redis 7 Cluster 必须保持不可用边界");
            assertNotNull(serverInfo.getErrorMessage(), "Redis 7 Cluster 不可用时必须保留探测错误信息");
            assertFalse(serverInfo.getErrorMessage().trim().isEmpty(), "Redis 7 Cluster 探测错误信息不能为空白");
        }
    }

    @Test
    void smartRetryKeysShouldRouteToExpectedDatasources() {
        for (MatrixRetryCase retryCase : activeRetryCases()) {
            String redisKey = retryKeyHelper.buildRedisKey(retryCase.retryType, retryCase.retryKey("matrix"));
            RedisServerInfo serverInfo = redisRouteTemplate.serverInfoByKey(redisKey);
            assertEquals(retryCase.datasourceKey, serverInfo.getDatasourceKey(), "smart-retry key 应按 route 规则路由: " + retryCase.datasourceKey);
        }
    }

    @Test
    void commandCapabilityShouldMatchRuntimeProbe() {
        RedisServerInfo info3 = redisRouteTemplate.serverInfo("redis3Standalone");
        assertTrue(RedisServerVersionHelper.isKnown(info3));
        assertFalse(RedisCommandCapabilityHelper.supportsUnlink(info3));

        RedisServerInfo info5 = redisRouteTemplate.serverInfo("redis5Standalone");
        assertTrue(RedisServerVersionHelper.isKnown(info5));
        assertTrue(RedisCommandCapabilityHelper.supportsUnlink(info5));
        assertFalse(RedisCommandCapabilityHelper.supportsGetEx(info5));

        RedisServerInfo info7 = redisRouteTemplate.serverInfo("redis7Standalone");
        assertTrue(RedisServerVersionHelper.isKnown(info7));
        assertTrue(RedisCommandCapabilityHelper.supportsAcl(info7));
        assertTrue(RedisCommandCapabilityHelper.supportsGetEx(info7));
        assertTrue(RedisCommandCapabilityHelper.supportsLMove(info7));
    }

    @Test
    void recordFailureShouldBeAtomicAcrossVersions() {
        for (MatrixRetryCase retryCase : activeRetryCases()) {
            String retryKey = retryCase.retryKey("matrix");
            RetryInfo info = engine.recordFailure(retryCase.retryType, retryKey);
            assertNotNull(info, "recordFailure 应返回状态: " + retryCase.datasourceKey);
            assertEquals(Integer.valueOf(1), info.getCount(), "count 应为 1: " + retryCase.datasourceKey);
            RetryInfo second = engine.recordFailure(retryCase.retryType, retryKey);
            assertEquals(Integer.valueOf(2), second.getCount(), "count 应为 2: " + retryCase.datasourceKey);
            assertTrue(second.getNextRetryTime() >= second.getLastFailTime(), "nextRetryTime 应大于等于 lastFailTime: " + retryCase.datasourceKey);
            assertRecordStoredInExpectedDatasource(retryCase, retryKey);
        }
    }

    @Test
    void decideShouldFlowAcrossVersions() {
        for (MatrixRetryCase retryCase : activeRetryCases()) {
            String retryKey = retryCase.retryKey("decide");
            RetryDecision allow = engine.decide(retryCase.retryType, retryKey);
            assertEquals(RetryDecisionType.ALLOW, allow.getType(), "无记录应 ALLOW: " + retryCase.datasourceKey);
            engine.recordFailure(retryCase.retryType, retryKey);
            RetryDecision waiting = engine.decide(retryCase.retryType, retryKey);
            assertEquals(RetryDecisionType.WAITING, waiting.getType(), "有记录未到时间应 WAITING: " + retryCase.datasourceKey);
            assertRecordStoredInExpectedDatasource(retryCase, retryKey);
        }
    }

    @Test
    void clearShouldRemoveRecordAcrossVersions() {
        for (MatrixRetryCase retryCase : activeRetryCases()) {
            String retryKey = retryCase.retryKey("clear");
            engine.recordFailure(retryCase.retryType, retryKey);
            RetryInfo before = engine.clear(retryCase.retryType, retryKey);
            assertNotNull(before, "clear 应返回删除前状态: " + retryCase.datasourceKey);
            assertNull(engine.getInfo(retryCase.retryType, retryKey), "clear 后记录应不存在: " + retryCase.datasourceKey);
            assertFalse(hasRecordInExpectedDatasource(retryCase, retryKey), "clear 后目标 datasource 不应残留记录: " + retryCase.datasourceKey);
        }
    }

    @Test
    void scanShouldUseRouteKeyDatasourceAcrossClusters() {
        for (MatrixRetryCase retryCase : clusterRetryCases()) {
            String retryKey = retryCase.retryKey("scan");
            engine.recordFailure(retryCase.retryType, retryKey);
            String redisKey = retryKeyHelper.buildRedisKey(retryCase.retryType, retryKey);
            RetryInfo expected = engine.getInfo(retryCase.retryType, retryKey);
            RetryScanResult result = scanUntilFinished(redisKey, retryCase.retryType, 50, true);
            assertTrue(result.isFinished(), "cluster scan 应完成: " + retryCase.datasourceKey);
            assertTrue(result.getKeys().contains(redisKey), "cluster scan 应扫描 routeKey 指向 datasource 的记录: " + retryCase.datasourceKey);
            assertEquals(expected, result.getInfos().get(redisKey), "cluster scan includeInfo 应返回目标记录: " + retryCase.datasourceKey);
        }
    }

    @Test
    void multiClusterBusinessRetryScanShouldBeIsolatedAndAggregatedByCaller() {
        Set<String> allExpectedKeys = new HashSet<String>();
        Map<String, RetryInfo> allExpectedInfos = new LinkedHashMap<String, RetryInfo>();
        Map<String, Set<String>> expectedKeysByDatasource = new LinkedHashMap<String, Set<String>>();
        Map<String, String> routeKeyByDatasource = new LinkedHashMap<String, String>();

        for (MatrixRetryCase retryCase : clusterRetryCases()) {
            Set<String> datasourceKeys = new HashSet<String>();
            String routeKey = null;
            for (int i = 0; i < MULTI_CLUSTER_RECORD_COUNT; i++) {
                String retryKey = multiClusterRetryKey(retryCase, i);
                String redisKey = retryKeyHelper.buildRedisKey(retryCase.retryType, retryKey);
                RedisServerInfo serverInfo = redisRouteTemplate.serverInfoByKey(redisKey);
                assertEquals(retryCase.datasourceKey, serverInfo.getDatasourceKey(),
                        "多集群业务记录必须命中预期 datasource: " + retryCase.datasourceKey);
                RetryInfo retryInfo = engine.recordFailure(retryCase.retryType, retryKey);
                assertRecordStoredOnlyInExpectedCluster(retryCase, redisKey);
                datasourceKeys.add(redisKey);
                allExpectedKeys.add(redisKey);
                allExpectedInfos.put(redisKey, retryInfo);
                routeKey = redisKey;
            }
            expectedKeysByDatasource.put(retryCase.datasourceKey, datasourceKeys);
            routeKeyByDatasource.put(retryCase.datasourceKey, routeKey);
        }

        Set<String> allScannedKeys = new HashSet<String>();
        Map<String, RetryInfo> allScannedInfos = new LinkedHashMap<String, RetryInfo>();
        for (MatrixRetryCase retryCase : clusterRetryCases()) {
            ScanAggregation aggregation = scanAllPages(routeKeyByDatasource.get(retryCase.datasourceKey),
                    retryCase.retryType, 1, true);
            Set<String> expectedDatasourceKeys = expectedKeysByDatasource.get(retryCase.datasourceKey);
            Set<String> actualDatasourceKeys = new HashSet<String>(aggregation.result.getKeys());
            assertTrue(aggregation.pages > 1, "单个 Cluster 必须完成多页 scan: " + retryCase.datasourceKey);
            assertEquals(aggregation.result.getKeys().size(), actualDatasourceKeys.size(),
                    "单个 Cluster scan 不能返回重复记录: " + retryCase.datasourceKey);
            assertEquals(expectedDatasourceKeys, actualDatasourceKeys,
                    "单个 Cluster scan 必须严格返回自身业务分区: " + retryCase.datasourceKey);
            assertEquals(expectedDatasourceKeys, aggregation.result.getInfos().keySet(),
                    "单个 Cluster scan 必须返回自身业务分区的状态: " + retryCase.datasourceKey);
            assertTrue(Collections.disjoint(allScannedKeys, actualDatasourceKeys),
                    "独立 Cluster scan 结果不能出现重复记录: " + retryCase.datasourceKey);
            allScannedKeys.addAll(aggregation.result.getKeys());
            allScannedInfos.putAll(aggregation.result.getInfos());
        }

        assertEquals(allExpectedKeys, allScannedKeys, "调用方合并独立 Cluster scan 后必须得到全部业务记录");
        assertEquals(allExpectedInfos.keySet(), allScannedInfos.keySet(), "调用方合并结果必须保留每条记录的状态");
        for (String redisKey : allExpectedKeys) {
            assertEquals(allExpectedInfos.get(redisKey), allScannedInfos.get(redisKey),
                    "调用方合并结果中的重试状态不匹配: " + redisKey);
        }
    }

    @Test
    void clusterScanShouldReturnEmptyWhenCursorNodeIndexExceedsMasterCount() {
        for (MatrixRetryCase retryCase : clusterRetryCases()) {
            String retryKey = retryCase.retryKey("cursor-out-of-range");
            engine.recordFailure(retryCase.retryType, retryKey);
            String routeKey = retryKeyHelper.buildRedisKey(retryCase.retryType, retryKey);
            RetryScanResult result = engine.scan(RetryScanRequest.builder()
                    .routeKey(routeKey)
                    .retryType(retryCase.retryType)
                    .cursor("999:0")
                    .count(50)
                    .includeInfo(false)
                    .build());
            assertTrue(result.isFinished(), "越界 cursor 应直接返回 finished: " + retryCase.datasourceKey);
            assertTrue(result.getKeys().isEmpty(), "越界 cursor 不应返回任何 key: " + retryCase.datasourceKey);
            assertEquals("0", result.getNextCursor(), "越界 cursor 应重置游标为 0: " + retryCase.datasourceKey);
        }
    }

    @Test
    void clusterScanShouldAggregateAllPagesFromEveryMasterNode() {
        for (MatrixRetryCase retryCase : clusterRetryCases()) {
            Set<String> expectedKeys = new HashSet<String>();
            for (int i = 0; i < 40; i++) {
                String retryKey = retryCase.retryKey("cluster-scan-" + i);
                engine.recordFailure(retryCase.retryType, retryKey);
                expectedKeys.add(retryKeyHelper.buildRedisKey(retryCase.retryType, retryKey));
            }
            String routeKey = expectedKeys.iterator().next();
            RetryScanResult result = scanUntilFinished(routeKey, retryCase.retryType, 1, false);
            assertTrue(result.isFinished(), "cluster scan 应完成: " + retryCase.datasourceKey);
            assertTrue(result.getKeys().containsAll(expectedKeys), "cluster scan 应覆盖所有分页记录: " + retryCase.datasourceKey);
            log.info("Cluster SCAN 多页聚合验证通过，datasource={}，keys={}", retryCase.datasourceKey, result.getKeys().size());
        }
    }

    private List<MatrixRetryCase> activeRetryCases() {
        List<MatrixRetryCase> activeCases = new ArrayList<MatrixRetryCase>();
        for (MatrixRetryCase retryCase : RETRY_CASES) {
            if (!isRedis7ClusterExempt() || !REDIS7_CLUSTER.equals(retryCase.datasourceKey)) {
                activeCases.add(retryCase);
            }
        }
        return activeCases;
    }

    private boolean isRedis7ClusterExempt() {
        return SpringBootVersion.getVersion().startsWith("2.2.");
    }

    private List<MatrixRetryCase> clusterRetryCases() {
        List<MatrixRetryCase> clusterCases = new ArrayList<MatrixRetryCase>();
        for (MatrixRetryCase retryCase : activeRetryCases()) {
            RedisServerInfo serverInfo = redisRouteTemplate.serverInfo(retryCase.datasourceKey);
            if ("cluster".equals(retryCase.redisMode)) {
                assertEquals("cluster", serverInfo.getRedisMode(), "cluster datasource 模式不匹配: " + retryCase.datasourceKey);
                clusterCases.add(retryCase);
            }
        }
        int expectedClusterCount = isRedis7ClusterExempt() ? 2 : 3;
        assertEquals(expectedClusterCount, clusterCases.size(), "cluster datasource 数量不匹配");
        return clusterCases;
    }

    private void assertRecordStoredInExpectedDatasource(MatrixRetryCase retryCase, String retryKey) {
        assertTrue(hasRecordInExpectedDatasource(retryCase, retryKey), "记录应写入 route 规则指向的 datasource: " + retryCase.datasourceKey);
    }

    private void assertRecordStoredOnlyInExpectedCluster(MatrixRetryCase expectedCase, String redisKey) {
        for (MatrixRetryCase retryCase : clusterRetryCases()) {
            Boolean hasKey = redisRouteTemplate.executeOn(retryCase.datasourceKey, template -> template.hasKey(redisKey));
            if (expectedCase.datasourceKey.equals(retryCase.datasourceKey)) {
                assertTrue(Boolean.TRUE.equals(hasKey), "记录应写入预期 Cluster: " + retryCase.datasourceKey);
            } else {
                assertFalse(Boolean.TRUE.equals(hasKey), "记录不能写入其他 Cluster: " + retryCase.datasourceKey);
            }
        }
    }

    private boolean hasRecordInExpectedDatasource(MatrixRetryCase retryCase, String retryKey) {
        String redisKey = retryKeyHelper.buildRedisKey(retryCase.retryType, retryKey);
        Boolean hasKey = redisRouteTemplate.executeOn(retryCase.datasourceKey, template -> template.hasKey(redisKey));
        return Boolean.TRUE.equals(hasKey);
    }

    private String multiClusterRetryKey(MatrixRetryCase retryCase, int index) {
        return MULTI_CLUSTER_BUSINESS_KEY_PREFIX + retryCase.datasourceKey + "-" + index;
    }

    private RetryScanResult scanUntilFinished(String routeKey, String retryType, int count, boolean includeInfo) {
        return scanAllPages(routeKey, retryType, count, includeInfo).result;
    }

    private ScanAggregation scanAllPages(String routeKey, String retryType, int count, boolean includeInfo) {
        String cursor = "0";
        int pages = 0;
        List<String> keys = new ArrayList<String>();
        Map<String, RetryInfo> infos = new LinkedHashMap<String, RetryInfo>();
        RetryScanResult result;
        do {
            result = engine.scan(RetryScanRequest.builder()
                    .routeKey(routeKey)
                    .retryType(retryType)
                    .cursor(cursor)
                    .count(count)
                    .includeInfo(includeInfo)
                    .build());
            keys.addAll(result.getKeys());
            infos.putAll(result.getInfos());
            cursor = result.getNextCursor();
            pages++;
            assertTrue(pages <= 200, "SCAN 分页必须在合理次数内结束: " + retryType);
        } while (!result.isFinished());
        log.info("矩阵 SCAN 聚合完成，retryType={}，pages={}，keys={}", retryType, pages, keys.size());
        return new ScanAggregation(RetryScanResult.builder()
                .nextCursor("0")
                .finished(true)
                .keys(keys)
                .infos(infos)
                .build(), pages);
    }

    private static class ScanAggregation {
        private final RetryScanResult result;
        private final int pages;

        private ScanAggregation(RetryScanResult result, int pages) {
            this.result = result;
            this.pages = pages;
        }
    }

    private static class MatrixRetryCase {
        private final String datasourceKey;
        private final String retryType;
        private final int redisMajor;
        private final String redisMode;

        private MatrixRetryCase(String datasourceKey, String retryType, int redisMajor, String redisMode) {
            this.datasourceKey = datasourceKey;
            this.retryType = retryType;
            this.redisMajor = redisMajor;
            this.redisMode = redisMode;
        }

        private String retryKey(String prefix) {
            return prefix + "-" + datasourceKey;
        }
    }
}
