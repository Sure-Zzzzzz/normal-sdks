package io.github.surezzzzzz.sdk.lock.redis.test.cases;

import io.github.surezzzzzz.sdk.lock.redis.test.SimpleRedisLockTestApplication;
import io.github.surezzzzzz.sdk.lock.redis.test.RedisLockRouteMatrixExpectationProperties;
import io.github.surezzzzzz.sdk.lock.redis.test.RedisLockRouteMatrixProfilesResolver;
import io.github.surezzzzzz.sdk.lock.redis.SimpleRedisLock;
import io.github.surezzzzzz.sdk.redis.route.model.RedisServerInfo;
import io.github.surezzzzzz.sdk.redis.route.registry.SimpleRedisRouteRegistry;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Redis 分布式锁 route 多版本矩阵端到端测试。
 *
 * @author surezzzzzz
 */
@Slf4j
@ActiveProfiles(resolver = RedisLockRouteMatrixProfilesResolver.class)
@SpringBootTest(classes = SimpleRedisLockTestApplication.class)
public class SimpleRedisLockRouteMatrixEndToEndTest {

    private static final List<String> ALL_DS = Arrays.asList(
            "redis3Standalone", "redis5Standalone", "redis7Standalone",
            "redis3Cluster", "redis5Cluster", "redis7Cluster");
    private static final List<String> CORE_COMPATIBLE_DS = Arrays.asList(
            "redis3Standalone", "redis5Standalone", "redis7Standalone",
            "redis3Cluster", "redis5Cluster");
    private static final String REDIS7_CLUSTER = "redis7Cluster";
    private static final String DEFAULT_LOCK_KEY = "matrix:lock:default:001";
    private static final String LOCK5_KEY = "lock5:matrix:lock:001";
    private static final String CLUSTER_KEY = "cluster:{lock-matrix}:001";
    private static final String LOCK_VALUE = "matrix-client-id";

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
            template.delete(DEFAULT_LOCK_KEY);
            return null;
        });
        redisRouteTemplate.executeOn("redis5Standalone", template -> {
            template.delete(LOCK5_KEY);
            return null;
        });
        redisRouteTemplate.executeOn("redis5Cluster", template -> {
            template.delete(CLUSTER_KEY);
            return null;
        });
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
        assertTrue(known.containsAll(CORE_COMPATIBLE_DS), "lock route matrix YAML 必须声明核心兼容 datasource: " + CORE_COMPATIBLE_DS);
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
        if (matrixExpectation.getUnknownDatasources().contains(REDIS7_CLUSTER)) {
            assertFalse(redisRouteRegistry.getServerInfo(REDIS7_CLUSTER).isKnown(), "SB 2.2.x 下 redis7Cluster 必须是兼容边界");
        }
    }

    @Test
    public void testStandaloneRouteLockByKey() {
        log.info("验证 lock route matrix standalone key 路由，defaultKey={}，lock5Key={}", DEFAULT_LOCK_KEY, LOCK5_KEY);
        assertTrue(simpleRedisLock.tryLock(DEFAULT_LOCK_KEY, LOCK_VALUE, 10, TimeUnit.SECONDS), "default route 加锁应成功");
        assertEquals(LOCK_VALUE, redisRouteTemplate.executeOn("redis3Standalone", t -> t.opsForValue().get(DEFAULT_LOCK_KEY)),
                "default key 应落到 redis3Standalone");
        assertNull(redisRouteTemplate.executeOn("redis5Standalone", t -> t.opsForValue().get(DEFAULT_LOCK_KEY)),
                "default key 不应落到 redis5Standalone");
        assertTrue(simpleRedisLock.unlock(DEFAULT_LOCK_KEY, LOCK_VALUE), "default route 解锁应成功");

        assertTrue(simpleRedisLock.tryLock(LOCK5_KEY, LOCK_VALUE, 10, TimeUnit.SECONDS), "lock5 route 加锁应成功");
        assertEquals(LOCK_VALUE, redisRouteTemplate.executeOn("redis5Standalone", t -> t.opsForValue().get(LOCK5_KEY)),
                "lock5 key 应落到 redis5Standalone");
        assertNull(redisRouteTemplate.executeOn("redis3Standalone", t -> t.opsForValue().get(LOCK5_KEY)),
                "lock5 key 不应落到 redis3Standalone");
        assertTrue(simpleRedisLock.unlock(LOCK5_KEY, LOCK_VALUE), "lock5 route 解锁应成功");
    }

    @Test
    public void testClusterRouteSingleKeyLock() {
        log.info("验证 lock route matrix cluster 单 key 加锁解锁，lockKey={}", CLUSTER_KEY);
        assertTrue(simpleRedisLock.tryLock(CLUSTER_KEY, LOCK_VALUE, 10, TimeUnit.SECONDS), "cluster route 单 key 加锁应成功");
        assertEquals(LOCK_VALUE, redisRouteTemplate.executeOn("redis5Cluster", t -> t.opsForValue().get(CLUSTER_KEY)),
                "cluster key 应落到 redis5Cluster");
        assertTrue(simpleRedisLock.unlock(CLUSTER_KEY, LOCK_VALUE), "cluster route 单 key 解锁应成功");
        assertNull(redisRouteTemplate.executeOn("redis5Cluster", t -> t.opsForValue().get(CLUSTER_KEY)),
                "cluster route 解锁后 key 应删除");
    }
}
