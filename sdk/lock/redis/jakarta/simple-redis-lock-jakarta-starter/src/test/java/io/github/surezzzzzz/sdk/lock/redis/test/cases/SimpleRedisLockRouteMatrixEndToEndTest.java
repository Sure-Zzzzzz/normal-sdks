package io.github.surezzzzzz.sdk.lock.redis.test.cases;

import io.github.surezzzzzz.sdk.lock.redis.SimpleRedisLock;
import io.github.surezzzzzz.sdk.lock.redis.model.RedisLockLease;
import io.github.surezzzzzz.sdk.lock.redis.test.RedisLockRouteMatrixExpectationProperties;
import io.github.surezzzzzz.sdk.lock.redis.test.SimpleRedisLockTestApplication;
import io.github.surezzzzzz.sdk.redis.route.model.RedisServerInfo;
import io.github.surezzzzzz.sdk.redis.route.registry.SimpleRedisRouteRegistry;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Redis 分布式锁 route 多版本矩阵端到端测试。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleRedisLockTestApplication.class)
public class SimpleRedisLockRouteMatrixEndToEndTest {

    private static final List<String> ALL_DS = Arrays.asList(
            "redis3Standalone", "redis5Standalone", "redis7Standalone",
            "redis3Cluster", "redis5Cluster", "redis7Cluster");
    private static final String DEFAULT_LOCK_KEY = "matrix:lock:default:001";
    private static final String LOCK5_KEY = "lock5:matrix:lock:001";
    private static final String LOCK7_KEY = "lock7:matrix:lock:001";
    private static final String CLUSTER3_KEY = "cluster3:{lock-matrix-3}:001";
    private static final String CLUSTER5_KEY = "cluster5:{lock-matrix-5}:001";
    private static final String CLUSTER7_KEY = "cluster7:{lock-matrix-7}:001";
    private static final String LOCK_VALUE = "matrix-client-id";
    private static final String DEFAULT_LEASE_KEY = "matrix:lock:default:lease";
    private static final String LOCK5_LEASE_KEY = "lock5:matrix:lock:lease";
    private static final String LOCK7_LEASE_KEY = "lock7:matrix:lock:lease";
    private static final String CLUSTER3_LEASE_KEY = "cluster3:{lock-matrix-lease-3}:001";
    private static final String CLUSTER5_LEASE_KEY = "cluster5:{lock-matrix-lease-5}:001";
    private static final String CLUSTER7_LEASE_KEY = "cluster7:{lock-matrix-lease-7}:001";

    @Autowired
    private SimpleRedisLock simpleRedisLock;

    @Autowired
    private RedisRouteTemplate redisRouteTemplate;

    @Autowired
    private SimpleRedisRouteRegistry redisRouteRegistry;

    @Autowired
    private RedisLockRouteMatrixExpectationProperties matrixExpectation;

    @AfterEach
    public void cleanUp() {
        redisRouteTemplate.executeOn("redis3Standalone", template -> {
            template.delete(Arrays.asList(DEFAULT_LOCK_KEY, DEFAULT_LEASE_KEY));
            return null;
        });
        redisRouteTemplate.executeOn("redis5Standalone", template -> {
            template.delete(Arrays.asList(LOCK5_KEY, LOCK5_LEASE_KEY));
            return null;
        });
        redisRouteTemplate.executeOn("redis7Standalone", template -> {
            template.delete(Arrays.asList(LOCK7_KEY, LOCK7_LEASE_KEY));
            return null;
        });
        deleteClusterLockKeys("redis3Cluster", CLUSTER3_KEY, CLUSTER3_LEASE_KEY);
        deleteClusterLockKeys("redis5Cluster", CLUSTER5_KEY, CLUSTER5_LEASE_KEY);
        deleteClusterLockKeys("redis7Cluster", CLUSTER7_KEY, CLUSTER7_LEASE_KEY);
    }

    @Test
    public void testMatrixExpectationYamlCoversAllDatasources() {
        Set<String> known = new HashSet<>(matrixExpectation.getKnownDatasources());
        Set<String> unknown = new HashSet<>(matrixExpectation.getUnknownDatasources());
        Set<String> expected = new HashSet<>(ALL_DS);
        Set<String> actual = new HashSet<>();
        actual.addAll(known);
        actual.addAll(unknown);
        log.info("验证 lock route matrix YAML 覆盖度，known={}，unknown={}，boundary={}",
                known, unknown, matrixExpectation.getCompatibilityBoundary());
        assertEquals(expected, actual, "lock route matrix YAML 的 known + unknown 必须刚好覆盖 6 个矩阵 datasource");
        Set<String> overlap = new HashSet<>(known);
        overlap.retainAll(unknown);
        assertTrue(overlap.isEmpty(), "lock route matrix YAML 的 known 与 unknown 不允许重复声明: " + overlap);
        assertTrue(known.containsAll(ALL_DS), "lock route matrix YAML 必须声明全部兼容 datasource: " + ALL_DS);
        assertTrue(unknown.isEmpty(), "Jakarta 矩阵中的全部 datasource 都应可用，不应声明 unknown: " + unknown);
        assertNotNull(matrixExpectation.getCompatibilityBoundary(), "lock route matrix YAML 必须写明兼容边界说明");
        assertFalse(matrixExpectation.getCompatibilityBoundary().trim().isEmpty(), "lock route matrix YAML 兼容边界说明不能为空");
    }

    @Test
    public void testRouteMatrixServerInfoMatchesExpectation() {
        log.info("验证 lock 复用 redis-route 1.1.0 矩阵探测结果，datasources={}，known={}，unknown={}",
                redisRouteRegistry.getDatasourceKeys(), matrixExpectation.getKnownDatasources(), matrixExpectation.getUnknownDatasources());
        assertTrue(redisRouteRegistry.getDatasourceKeys().containsAll(ALL_DS), "必须完整包含 6 个矩阵 datasource: " + ALL_DS);
        for (String datasource : matrixExpectation.getKnownDatasources()) {
            RedisServerInfo info = redisRouteRegistry.getServerInfo(datasource);
            assertNotNull(info, "datasource=[" + datasource + "] serverInfo 不应为 null");
            assertTrue(info.isKnown(), "datasource=[" + datasource + "] 应探测成功 known=true");
            assertNotNull(info.getVersion(), "datasource=[" + datasource + "] version 不应为 null");
        }
        for (String datasource : matrixExpectation.getUnknownDatasources()) {
            RedisServerInfo info = redisRouteRegistry.getServerInfo(datasource);
            assertNotNull(info, "datasource=[" + datasource + "] serverInfo 不应为 null");
            assertFalse(info.isKnown(), "datasource=[" + datasource + "] 在 matrix YAML 中声明为 unknown，应探测失败");
            assertNotNull(info.getErrorMessage(), "datasource=[" + datasource + "] 探测失败时应有脱敏 errorMessage");
        }
    }

    @Test
    public void testStandaloneRouteLockByKey() {
        verifyLockLifecycle(DEFAULT_LOCK_KEY, "redis3Standalone");
        verifyLockLifecycle(LOCK5_KEY, "redis5Standalone");
        verifyLockLifecycle(LOCK7_KEY, "redis7Standalone");
    }

    @Test
    public void testClusterRouteSingleKeyLock() {
        verifyLockLifecycle(CLUSTER3_KEY, "redis3Cluster");
        verifyLockLifecycle(CLUSTER5_KEY, "redis5Cluster");
        verifyLockLifecycle(CLUSTER7_KEY, "redis7Cluster");
    }

    @Test
    public void testStandaloneRouteLeaseLifecycleByKey() {
        verifyLeaseLifecycle(DEFAULT_LEASE_KEY, "redis3Standalone", "redis5Standalone");
        verifyLeaseLifecycle(LOCK5_LEASE_KEY, "redis5Standalone", "redis3Standalone");
        verifyLeaseLifecycle(LOCK7_LEASE_KEY, "redis7Standalone", "redis3Standalone");
    }

    @Test
    public void testClusterRouteSingleKeyLeaseLifecycle() {
        verifyLeaseLifecycle(CLUSTER3_LEASE_KEY, "redis3Cluster", "redis3Standalone");
        verifyLeaseLifecycle(CLUSTER5_LEASE_KEY, "redis5Cluster", "redis3Standalone");
        verifyLeaseLifecycle(CLUSTER7_LEASE_KEY, "redis7Cluster", "redis3Standalone");
    }

    private void deleteClusterLockKeys(String datasource, String lockKey, String leaseLockKey) {
        redisRouteTemplate.executeOn(datasource, template -> {
            template.delete(lockKey);
            template.delete(leaseLockKey);
            return null;
        });
    }

    private void verifyLockLifecycle(String lockKey, String expectedDatasource) {
        log.info("验证矩阵单 key 锁完整生命周期，lockKey={}，expectedDatasource={}", lockKey, expectedDatasource);
        assertTrue(simpleRedisLock.tryLock(lockKey, LOCK_VALUE, 10, TimeUnit.SECONDS),
                "矩阵预期 datasource 加锁应成功");
        assertEquals(LOCK_VALUE, redisRouteTemplate.executeOn(
                        expectedDatasource, template -> template.opsForValue().get(lockKey)),
                "矩阵 lock key 必须落到预期 datasource");
        assertTrue(simpleRedisLock.unlock(lockKey, LOCK_VALUE), "矩阵预期 datasource 解锁应成功");
        assertNull(redisRouteTemplate.executeOn(
                        expectedDatasource, template -> template.opsForValue().get(lockKey)),
                "矩阵预期 datasource 解锁后 key 应删除");
    }

    private void verifyLeaseLifecycle(String lockKey, String expectedDatasource, String otherDatasource) {
        log.info("验证矩阵租约完整生命周期，lockKey={}，expectedDatasource={}，otherDatasource={}",
                lockKey, expectedDatasource, otherDatasource);
        Optional<RedisLockLease> optionalLease = simpleRedisLock.tryLockWithLease(
                lockKey, 1, TimeUnit.SECONDS);
        Boolean expectedExistsAfterAcquire = redisRouteTemplate.executeOn(
                expectedDatasource, template -> template.hasKey(lockKey));
        Boolean otherExistsAfterAcquire = otherDatasource == null ? null : redisRouteTemplate.executeOn(
                otherDatasource, template -> template.hasKey(lockKey));
        log.info("矩阵租约获取后，leasePresent={}，expectedExists={}，otherExists={}",
                optionalLease.isPresent(), expectedExistsAfterAcquire, otherExistsAfterAcquire);
        assertTrue(optionalLease.isPresent(), "矩阵预期 datasource 首次获取租约应成功");
        assertTrue(Boolean.TRUE.equals(expectedExistsAfterAcquire), "租约获取后 key 必须存在于预期 datasource");
        if (otherDatasource != null) {
            assertFalse(Boolean.TRUE.equals(otherExistsAfterAcquire), "租约获取后 key 不得写入另一 standalone datasource");
        }

        RedisLockLease lease = optionalLease.get();
        boolean renewed = lease.renew(2, TimeUnit.SECONDS);
        Long pttl = redisRouteTemplate.executeOn(
                expectedDatasource, template -> template.getExpire(lockKey, TimeUnit.MILLISECONDS));
        Boolean otherExistsAfterRenew = otherDatasource == null ? null : redisRouteTemplate.executeOn(
                otherDatasource, template -> template.hasKey(lockKey));
        log.info("矩阵租约续租后，renewed={}，expectedPttl={}，otherExists={}",
                renewed, pttl, otherExistsAfterRenew);
        assertTrue(renewed, "矩阵预期 datasource 中当前 owner 应能续租");
        assertNotNull(pttl, "矩阵预期 datasource 续租后 PTTL 不应为 null");
        assertTrue(pttl > 1000L, "矩阵租约续租后的 PTTL 应体现新的租约时长");
        if (otherDatasource != null) {
            assertFalse(Boolean.TRUE.equals(otherExistsAfterRenew), "矩阵租约续租不得错误写入另一 standalone datasource");
        }

        boolean released = lease.release();
        Boolean expectedExistsAfterRelease = redisRouteTemplate.executeOn(
                expectedDatasource, template -> template.hasKey(lockKey));
        Boolean otherExistsAfterRelease = otherDatasource == null ? null : redisRouteTemplate.executeOn(
                otherDatasource, template -> template.hasKey(lockKey));
        log.info("矩阵租约释放后，released={}，expectedExists={}，otherExists={}",
                released, expectedExistsAfterRelease, otherExistsAfterRelease);
        assertTrue(released, "矩阵预期 datasource 中当前 owner 应能释放租约");
        assertFalse(Boolean.TRUE.equals(expectedExistsAfterRelease), "矩阵租约释放后预期 datasource 中 key 应删除");
        if (otherDatasource != null) {
            assertFalse(Boolean.TRUE.equals(otherExistsAfterRelease), "矩阵租约释放不得影响另一 standalone datasource");
        }
    }
}
