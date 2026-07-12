package io.github.surezzzzzz.sdk.redis.route.test.cases;

import io.github.surezzzzzz.sdk.redis.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.redis.route.exception.RouteException;
import io.github.surezzzzzz.sdk.redis.route.model.RedisServerInfo;
import io.github.surezzzzzz.sdk.redis.route.registry.SimpleRedisRouteRegistry;
import io.github.surezzzzzz.sdk.redis.route.support.RedisCommandCapabilityHelper;
import io.github.surezzzzzz.sdk.redis.route.support.RedisServerVersionHelper;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import io.github.surezzzzzz.sdk.redis.route.test.RedisRouteMatrixExpectationProperties;
import io.github.surezzzzzz.sdk.redis.route.test.RedisRouteMatrixProfilesResolver;
import io.github.surezzzzzz.sdk.redis.route.test.SimpleRedisRouteTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Redis 多版本矩阵端到端测试（v1.1.0 发布阻塞项）
 *
 * <p>测试矩阵：
 * <ul>
 *   <li>redis3Standalone  - Redis 3.2.12 单节点（端口 16379）</li>
 *   <li>redis5Standalone  - Redis 5.0.14 单节点（端口 16380）</li>
 *   <li>redis7Standalone  - Redis 7.2.6  单节点（端口 16381）</li>
 *   <li>redis3Cluster     - Redis 3.2.12 Cluster（端口 17000-17005）</li>
 *   <li>redis5Cluster     - Redis 5.0.14 Cluster（端口 17010-17015）</li>
 *   <li>redis7Cluster     - Redis 7.2.6  Cluster（端口 17020-17025）</li>
 * </ul>
 *
 * <p>启动方式：
 * <pre>
 * docker-compose -f docker-compose.redis-version-matrix.yml up -d
 * ./gradlew :sdk:route:redis:simple-redis-route-starter:test -Dredis.route.version.matrix.test=true
 * </pre>
 *
 * @author surezzzzzz
 */
@Slf4j
@EnabledIfSystemProperty(named = "redis.route.version.matrix.test", matches = "true")
@ActiveProfiles(resolver = RedisRouteMatrixProfilesResolver.class)
@SpringBootTest(classes = SimpleRedisRouteTestApplication.class)
public class RedisRouteMultiVersionMatrixEndToEndTest {

    private static final List<String> ALL_DS = Arrays.asList(
            "redis3Standalone", "redis5Standalone", "redis7Standalone",
            "redis3Cluster", "redis5Cluster", "redis7Cluster");

    private static final List<String> CORE_COMPATIBLE_DS = Arrays.asList(
            "redis3Standalone", "redis5Standalone", "redis7Standalone",
            "redis3Cluster", "redis5Cluster");

    private static final String REDIS7_CLUSTER = "redis7Cluster";

    @Autowired
    private RedisRouteTemplate template;

    @Autowired
    private SimpleRedisRouteRegistry registry;

    @Autowired
    private RedisRouteMatrixExpectationProperties matrixExpectation;

    @AfterEach
    public void cleanUp() {
        template.executeOn("redis3Standalone", t -> {
            t.delete("{route-matrix}:redis3:standalone:string");
            return null;
        });
        template.executeOn("redis5Standalone", t -> {
            t.delete("{route-matrix}:redis5:standalone:string");
            t.delete("cache:{route-matrix}:bykey");
            t.delete("standalone:{route-matrix}:mixed:001");
            return null;
        });
        template.executeOn("redis7Standalone", t -> {
            t.delete("{route-matrix}:redis7:standalone:string");
            return null;
        });
        template.executeOn("redis3Cluster", t -> {
            t.delete("{route-matrix}:redis3:cluster:string");
            return null;
        });
        template.executeOn("redis5Cluster", t -> {
            t.delete("{route-matrix}:redis5:cluster:string");
            t.delete("cluster:{route-matrix}:001");
            t.delete("cluster:{route-matrix}:002");
            t.delete("cluster:{slot-a}:001");
            t.delete("cluster:{slot-b}:001");
            return null;
        });
        if (isRedis7ClusterSupported()) {
            template.executeOn(REDIS7_CLUSTER, t -> {
                t.delete("{route-matrix}:redis7:cluster:string");
                return null;
            });
        }
    }

    @Test
    public void testMatrixExpectationYamlCoversAllDatasources() {
        Set<String> known = new HashSet<>(matrixExpectation.getKnownDatasources());
        Set<String> unknown = new HashSet<>(matrixExpectation.getUnknownDatasources());
        Set<String> expected = new HashSet<>(ALL_DS);
        Set<String> actual = new HashSet<>();
        actual.addAll(known);
        actual.addAll(unknown);
        log.info("验证 matrix YAML 覆盖度，known={}，unknown={}，boundary={}",
                known, unknown, matrixExpectation.getCompatibilityBoundary());
        assertEquals(expected, actual, "matrix YAML 的 known + unknown 必须刚好覆盖 6 个矩阵 datasource");
        Set<String> overlap = new HashSet<>(known);
        overlap.retainAll(unknown);
        assertTrue(overlap.isEmpty(), "matrix YAML 的 known 与 unknown 不允许重复声明: " + overlap);
        assertTrue(known.containsAll(CORE_COMPATIBLE_DS),
                "matrix YAML 必须声明核心兼容 datasource: " + CORE_COMPATIBLE_DS);
        assertNotNull(matrixExpectation.getCompatibilityBoundary(), "matrix YAML 必须写明兼容边界说明");
        assertFalse(matrixExpectation.getCompatibilityBoundary().trim().isEmpty(), "matrix YAML 兼容边界说明不能为空");
    }

    @Test
    public void testAllMatrixDatasourcesConnected() {
        log.info("验证 6 个矩阵 datasource 全部连通，实际 datasourceKeys={}，预期 known={}，预期 unknown={}，兼容边界={}",
                registry.getDatasourceKeys(), matrixExpectation.getKnownDatasources(),
                matrixExpectation.getUnknownDatasources(), matrixExpectation.getCompatibilityBoundary());
        assertTrue(registry.getDatasourceKeys().containsAll(ALL_DS),
                "必须完整包含 6 个矩阵 datasource: " + ALL_DS);
        for (String ds : ALL_DS) {
            assertTrue(registry.containsDatasource(ds), "应包含 datasource: " + ds);
            assertNotNull(registry.getConnectionFactory(ds), "datasource=[" + ds + "] connectionFactory 不应为 null");
            assertNotNull(registry.getStringRedisTemplate(ds), "datasource=[" + ds + "] StringRedisTemplate 不应为 null");
        }
    }

    @Test
    public void testServerInfoProbeReturnsKnownForAllMatrixDatasources() {
        Map<String, RedisServerInfo> infos = registry.getServerInfos();
        log.info("验证 6 个矩阵 datasource 的 serverInfo 探测结果，size={}，keys={}", infos.size(), infos.keySet());
        assertTrue(infos.keySet().containsAll(ALL_DS),
                "serverInfo 必须完整包含 6 个矩阵 datasource: " + ALL_DS);
        for (String ds : expectedKnownDatasources()) {
            RedisServerInfo info = infos.get(ds);
            assertKnownServerInfo(ds, info);
        }
        for (String ds : matrixExpectation.getUnknownDatasources()) {
            RedisServerInfo info = infos.get(ds);
            assertUnknownServerInfo(ds, info);
        }
        assertRedis7ClusterCompatibilityBoundary(infos);
    }

    @Test
    public void testRedis3VersionDetectedExactly() {
        RedisServerInfo info3s = registry.getServerInfo("redis3Standalone");
        RedisServerInfo info3c = registry.getServerInfo("redis3Cluster");
        log.info("redis3Standalone version=[{}], redis3Cluster version=[{}]",
                info3s.getVersion().getRaw(), info3c.getVersion().getRaw());
        assertEquals(3, info3s.getVersion().getMajor(), "redis3Standalone major 应为 3");
        assertEquals(2, info3s.getVersion().getMinor(), "redis3Standalone minor 应为 2");
        assertEquals(12, info3s.getVersion().getPatch(), "redis3Standalone patch 应为 12");
        assertEquals(3, info3c.getVersion().getMajor(), "redis3Cluster major 应为 3");
        assertEquals(2, info3c.getVersion().getMinor(), "redis3Cluster minor 应为 2");
        assertEquals(12, info3c.getVersion().getPatch(), "redis3Cluster patch 应为 12");
        assertTrue(RedisServerVersionHelper.isBefore(info3s, 4, 0), "redis3Standalone 应 < 4.0");
        assertTrue(RedisServerVersionHelper.isBefore(info3c, 4, 0), "redis3Cluster 应 < 4.0");
    }

    @Test
    public void testRedis5VersionDetectedExactly() {
        RedisServerInfo info5s = registry.getServerInfo("redis5Standalone");
        RedisServerInfo info5c = registry.getServerInfo("redis5Cluster");
        log.info("redis5Standalone version=[{}], redis5Cluster version=[{}]",
                info5s.getVersion().getRaw(), info5c.getVersion().getRaw());
        assertEquals(5, info5s.getVersion().getMajor(), "redis5Standalone major 应为 5");
        assertEquals(0, info5s.getVersion().getMinor(), "redis5Standalone minor 应为 0");
        assertEquals(14, info5s.getVersion().getPatch(), "redis5Standalone patch 应为 14");
        assertEquals(5, info5c.getVersion().getMajor(), "redis5Cluster major 应为 5");
        assertEquals(0, info5c.getVersion().getMinor(), "redis5Cluster minor 应为 0");
        assertEquals(14, info5c.getVersion().getPatch(), "redis5Cluster patch 应为 14");
        assertTrue(RedisServerVersionHelper.isAtLeast(info5s, 5, 0), "redis5Standalone 应 >= 5.0");
        assertTrue(RedisServerVersionHelper.isBefore(info5s, 6, 0), "redis5Standalone 应 < 6.0");
    }

    @Test
    public void testRedis7VersionDetectedExactly() {
        RedisServerInfo info7s = registry.getServerInfo("redis7Standalone");
        RedisServerInfo info7c = registry.getServerInfo("redis7Cluster");
        log.info("redis7Standalone version=[{}], redis7Cluster known=[{}], version=[{}]",
                info7s.getVersion().getRaw(), info7c.isKnown(), versionText(info7c));
        assertEquals(7, info7s.getVersion().getMajor(), "redis7Standalone major 应为 7");
        assertEquals(2, info7s.getVersion().getMinor(), "redis7Standalone minor 应为 2");
        assertEquals(6, info7s.getVersion().getPatch(), "redis7Standalone patch 应为 6");
        assertTrue(RedisServerVersionHelper.isAtLeast(info7s, 7, 0), "redis7Standalone 应 >= 7.0");
        if (isRedis7ClusterSupported()) {
            assertEquals(7, info7c.getVersion().getMajor(), "redis7Cluster major 应为 7");
            assertEquals(2, info7c.getVersion().getMinor(), "redis7Cluster minor 应为 2");
            assertEquals(6, info7c.getVersion().getPatch(), "redis7Cluster patch 应为 6");
            assertTrue(RedisServerVersionHelper.isAtLeast(info7c, 7, 0), "redis7Cluster 应 >= 7.0");
        } else {
            assertRedis7ClusterUnsupported(info7c);
        }
    }

    @Test
    public void testStandaloneModeReportedForAllStandaloneDatasources() {
        log.info("验证 3 个 standalone datasource 的 redisMode=standalone");
        for (String ds : Arrays.asList("redis3Standalone", "redis5Standalone", "redis7Standalone")) {
            RedisServerInfo info = registry.getServerInfo(ds);
            log.info("datasource=[{}] redisMode=[{}]", ds, info.getRedisMode());
            assertEquals("standalone", info.getRedisMode(),
                    "datasource=[" + ds + "] redisMode 应为 standalone");
        }
    }

    @Test
    public void testClusterModeReportedForAllClusterDatasources() {
        log.info("验证 3 个 cluster datasource 的 redisMode=cluster");
        for (String ds : Arrays.asList("redis3Cluster", "redis5Cluster", "redis7Cluster")) {
            RedisServerInfo info = registry.getServerInfo(ds);
            log.info("datasource=[{}] known=[{}] redisMode=[{}]", ds, info.isKnown(), info.getRedisMode());
            if (REDIS7_CLUSTER.equals(ds) && !isRedis7ClusterSupported()) {
                assertRedis7ClusterUnsupported(info);
            } else {
                assertEquals("cluster", info.getRedisMode(),
                        "datasource=[" + ds + "] redisMode 应为 cluster");
            }
        }
    }

    @Test
    public void testCapabilityJudgmentMatchesVersionMatrix() {
        log.info("验证版本矩阵能力判断一致性");
        RedisServerInfo i3 = registry.getServerInfo("redis3Standalone");
        RedisServerInfo i5 = registry.getServerInfo("redis5Standalone");
        RedisServerInfo i7 = registry.getServerInfo("redis7Standalone");

        // Redis 3.x 不支持 UNLINK / ZPOP / GETEX / ACL
        assertFalse(RedisCommandCapabilityHelper.supportsUnlink(i3), "redis3 不应支持 UNLINK");
        assertFalse(RedisCommandCapabilityHelper.supportsZPop(i3), "redis3 不应支持 ZPOP");
        assertFalse(RedisCommandCapabilityHelper.supportsGetEx(i3), "redis3 不应支持 GETEX");
        assertFalse(RedisCommandCapabilityHelper.supportsAcl(i3), "redis3 不应支持 ACL");

        // Redis 5.x 支持 UNLINK / ZPOP，不支持 GETEX / ACL / LMOVE / KEEPTTL
        assertTrue(RedisCommandCapabilityHelper.supportsUnlink(i5), "redis5 应支持 UNLINK");
        assertTrue(RedisCommandCapabilityHelper.supportsZPop(i5), "redis5 应支持 ZPOP");
        assertFalse(RedisCommandCapabilityHelper.supportsGetEx(i5), "redis5 不应支持 GETEX");
        assertFalse(RedisCommandCapabilityHelper.supportsAcl(i5), "redis5 不应支持 ACL");
        assertFalse(RedisCommandCapabilityHelper.supportsLMove(i5), "redis5 不应支持 LMOVE");
        assertFalse(RedisCommandCapabilityHelper.supportsKeepTtl(i5), "redis5 不应支持 KEEPTTL");

        // Redis 7.x 支持全部能力
        assertTrue(RedisCommandCapabilityHelper.supportsAcl(i7), "redis7 应支持 ACL");
        assertTrue(RedisCommandCapabilityHelper.supportsGetEx(i7), "redis7 应支持 GETEX");
        assertTrue(RedisCommandCapabilityHelper.supportsLMove(i7), "redis7 应支持 LMOVE");
        assertTrue(RedisCommandCapabilityHelper.supportsKeepTtl(i7), "redis7 应支持 KEEPTTL");
        assertTrue(RedisCommandCapabilityHelper.supportsSetGet(i7), "redis7 应支持 SET GET");

        // cluster datasource 能力也应正确
        if (isRedis7ClusterSupported()) {
            assertTrue(RedisCommandCapabilityHelper.supportsLMove(registry.getServerInfo(REDIS7_CLUSTER)),
                    "redis7Cluster 应支持 LMOVE");
        } else {
            assertFalse(RedisCommandCapabilityHelper.supportsLMove(registry.getServerInfo(REDIS7_CLUSTER)),
                    "SB 2.2.x 旧 Lettuce 无法探测 redis7Cluster 时应保守返回 false");
        }
        assertFalse(RedisCommandCapabilityHelper.supportsUnlink(registry.getServerInfo("redis3Cluster")),
                "redis3Cluster 不应支持 UNLINK");
    }

    @Test
    public void testStandaloneReadWriteIsolation() {
        log.info("验证 3 个 standalone datasource 读写隔离，互不串库");
        template.executeOn("redis3Standalone", t -> {
            t.opsForValue().set("{route-matrix}:redis3:standalone:string", "v3");
            return null;
        });
        template.executeOn("redis5Standalone", t -> {
            t.opsForValue().set("{route-matrix}:redis5:standalone:string", "v5");
            return null;
        });
        template.executeOn("redis7Standalone", t -> {
            t.opsForValue().set("{route-matrix}:redis7:standalone:string", "v7");
            return null;
        });
        String v3 = template.executeOn("redis3Standalone",
                t -> t.opsForValue().get("{route-matrix}:redis3:standalone:string"));
        String v5 = template.executeOn("redis5Standalone",
                t -> t.opsForValue().get("{route-matrix}:redis5:standalone:string"));
        String v7 = template.executeOn("redis7Standalone",
                t -> t.opsForValue().get("{route-matrix}:redis7:standalone:string"));
        log.info("v3=[{}], v5=[{}], v7=[{}]", v3, v5, v7);
        assertEquals("v3", v3, "redis3Standalone 读写应一致");
        assertEquals("v5", v5, "redis5Standalone 读写应一致");
        assertEquals("v7", v7, "redis7Standalone 读写应一致");

        // 串库验证：redis3 不应能读到 redis5 写的 key
        assertNull(template.executeOn("redis3Standalone",
                t -> t.opsForValue().get("{route-matrix}:redis5:standalone:string")),
                "redis3Standalone 不应读到 redis5 的数据");
        assertNull(template.executeOn("redis7Standalone",
                t -> t.opsForValue().get("{route-matrix}:redis3:standalone:string")),
                "redis7Standalone 不应读到 redis3 的数据");
    }

    @Test
    public void testClusterReadWriteIsolation() {
        log.info("验证 3 个 cluster datasource 读写隔离，互不串库");
        template.executeOn("redis3Cluster", t -> {
            t.opsForValue().set("{route-matrix}:redis3:cluster:string", "c3");
            return null;
        });
        template.executeOn("redis5Cluster", t -> {
            t.opsForValue().set("{route-matrix}:redis5:cluster:string", "c5");
            return null;
        });
        if (isRedis7ClusterSupported()) {
            template.executeOn(REDIS7_CLUSTER, t -> {
                t.opsForValue().set("{route-matrix}:redis7:cluster:string", "c7");
                return null;
            });
        }

        String c3 = template.executeOn("redis3Cluster",
                t -> t.opsForValue().get("{route-matrix}:redis3:cluster:string"));
        String c5 = template.executeOn("redis5Cluster",
                t -> t.opsForValue().get("{route-matrix}:redis5:cluster:string"));
        log.info("c3=[{}], c5=[{}]", c3, c5);
        assertEquals("c3", c3, "redis3Cluster 读写应一致");
        assertEquals("c5", c5, "redis5Cluster 读写应一致");
        if (isRedis7ClusterSupported()) {
            String c7 = template.executeOn(REDIS7_CLUSTER,
                    t -> t.opsForValue().get("{route-matrix}:redis7:cluster:string"));
            log.info("c7=[{}]", c7);
            assertEquals("c7", c7, "redis7Cluster 读写应一致");
        }

        assertNull(template.executeOn("redis3Cluster",
                t -> t.opsForValue().get("{route-matrix}:redis5:cluster:string")),
                "redis3Cluster 不应读到 redis5Cluster 的数据");
    }

    @Test
    public void testClusterRouteReadWriteByKey() {
        StringRedisTemplate clusterTemplate = template.stringTemplate("redis5Cluster");
        StringRedisTemplate defaultTemplate = template.stringTemplate();

        String value = template.execute("cluster:{route-matrix}:001", redisTemplate -> {
            assertSame(clusterTemplate, redisTemplate, "cluster:* 应路由到 redis5Cluster template");
            redisTemplate.opsForValue().set("cluster:{route-matrix}:001", "cluster-route-value");
            return redisTemplate.opsForValue().get("cluster:{route-matrix}:001");
        });

        log.info("cluster route value=[{}]", value);
        assertEquals("cluster-route-value", value, "Cluster 路由写读应一致");
        assertSame(clusterTemplate, template.stringTemplateByKey("cluster:{route-matrix}:001"),
                "stringTemplateByKey 应返回 redis5Cluster template");
        assertNotSame(defaultTemplate, clusterTemplate, "默认 datasource 与 cluster datasource template 不应相同");
        assertEquals("cluster-route-value", clusterTemplate.opsForValue().get("cluster:{route-matrix}:001"),
                "redis5Cluster template 应可读到写入值");
    }

    @Test
    public void testClusterMultiKeySameDatasourceWithHashTag() {
        Boolean result = template.execute(Arrays.asList("cluster:{route-matrix}:001", "cluster:{route-matrix}:002"), redisTemplate -> {
            assertSame(template.stringTemplate("redis5Cluster"), redisTemplate,
                    "cluster:* multi-key 应路由到 redis5Cluster template");
            Map<String, String> values = new LinkedHashMap<>();
            values.put("cluster:{route-matrix}:001", "cluster-route-1");
            values.put("cluster:{route-matrix}:002", "cluster-route-2");
            redisTemplate.opsForValue().multiSet(values);
            return "cluster-route-1".equals(redisTemplate.opsForValue().get("cluster:{route-matrix}:001"))
                    && "cluster-route-2".equals(redisTemplate.opsForValue().get("cluster:{route-matrix}:002"));
        });

        log.info("cluster same-slot multi-key result=[{}]", result);
        assertTrue(result, "同 hash tag 的 Cluster multi-key 写读应成功");
        assertEquals("cluster-route-1", template.stringTemplate("redis5Cluster").opsForValue().get("cluster:{route-matrix}:001"),
                "redis5Cluster 应读到第一个 multi-key 值");
        assertEquals("cluster-route-2", template.stringTemplate("redis5Cluster").opsForValue().get("cluster:{route-matrix}:002"),
                "redis5Cluster 应读到第二个 multi-key 值");
    }

    @Test
    public void testClusterDoesNotGuaranteeSameSlotForMultiKeyCommand() {
        assertDoesNotThrow(() -> template.execute(Arrays.asList("cluster:{slot-a}:001", "cluster:{slot-b}:001"), redisTemplate -> {
            assertSame(template.stringTemplate("redis5Cluster"), redisTemplate,
                    "跨 slot key 只要同 datasource，route 层应允许进入回调");
            return redisTemplate;
        }), "route 只保证同 datasource，不保证 Cluster 同 slot");

        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setScriptText("return redis.call('GET', KEYS[1]) or redis.call('GET', KEYS[2])");
        script.setResultType(String.class);
        Exception exception = assertThrows(Exception.class,
                () -> template.execute(Arrays.asList("cluster:{slot-a}:001", "cluster:{slot-b}:001"), redisTemplate ->
                        redisTemplate.execute(script, Arrays.asList("cluster:{slot-a}:001", "cluster:{slot-b}:001"))),
                "不同 hash tag 的 Redis Cluster Lua multi-key 命令应由 Redis/Lettuce 暴露真实 cross-slot 异常");
        log.info("cluster cross-slot exceptionType=[{}], message=[{}]", exception.getClass().getSimpleName(), exception.getMessage());
        assertExceptionChainContains(exception, "slot", "cross-slot 异常链必须包含 slot 语义");
    }

    @Test
    public void testMixedMultiKeyCrossDatasourceThrows() {
        RouteException exception = assertThrows(RouteException.class,
                () -> template.execute(Arrays.asList("cluster:{route-matrix}:001", "standalone:{route-matrix}:mixed:001"),
                        redisTemplate -> redisTemplate),
                "cluster:* 与默认 standalone key 跨 datasource 应被 route 层拦截");
        log.info("cross datasource errorCode=[{}], message=[{}]", exception.getErrorCode(), exception.getMessage());
        assertEquals(ErrorCode.REDIS_ROUTE_009, exception.getErrorCode(), "errorCode 应为 REDIS_ROUTE_009");
        assertTrue(exception.getMessage().contains("redis5Cluster"), "消息应包含 cluster datasource");
        assertTrue(exception.getMessage().contains("redis3Standalone"), "消息应包含默认 standalone datasource");
        assertTrue(exception.getMessage().contains("cluster:{route-matrix}:001"), "消息应包含 cluster key");
        assertTrue(exception.getMessage().contains("standalone:{route-matrix}:mixed:001"), "消息应包含 standalone key");
    }

    @Test
    public void testMixedConnectionFactoryModes() {
        LettuceConnectionFactory clusterFactory = (LettuceConnectionFactory) template.connectionFactory("redis5Cluster");
        LettuceConnectionFactory standaloneFactory = (LettuceConnectionFactory) template.connectionFactory("redis5Standalone");
        log.info("clusterNodes={}, standaloneHost={}",
                clusterFactory.getClusterConfiguration().getClusterNodes().size(),
                standaloneFactory.getStandaloneConfiguration().getHostName());
        assertNotNull(clusterFactory.getClusterConfiguration(), "redis5Cluster 应有 cluster 配置");
        assertEquals(6, clusterFactory.getClusterConfiguration().getClusterNodes().size(),
                "redis5Cluster 应配置 6 个 cluster node");
        assertTrue(clusterFactory.getClusterConfiguration().getClusterNodes().stream()
                        .allMatch(node -> "localhost".equals(node.getHost())),
                "redis5Cluster 6 个节点 host 都应为 localhost");
        assertEquals(new HashSet<>(Arrays.asList(17010, 17011, 17012, 17013, 17014, 17015)),
                clusterNodePorts(clusterFactory), "redis5Cluster 6 个节点端口必须与 compose 矩阵一致");
        assertNotNull(standaloneFactory.getStandaloneConfiguration(), "redis5Standalone 应有 standalone 配置");
        assertEquals("localhost", standaloneFactory.getStandaloneConfiguration().getHostName(),
                "redis5Standalone host 应为 localhost");
        assertEquals(16380, standaloneFactory.getStandaloneConfiguration().getPort(),
                "redis5Standalone port 应为 16380");
    }

    @Test
    public void testServerInfoByKeyReturnsRoutedDatasourceInfo() {
        // cache:* 前缀规则路由到 redis5Standalone
        RedisServerInfo info = template.serverInfoByKey("cache:{route-matrix}:bykey");
        log.info("serverInfoByKey('cache:...') datasourceKey={}, version={}",
                info.getDatasourceKey(), info.isKnown() ? info.getVersion().getRaw() : "unknown");
        assertEquals("redis5Standalone", info.getDatasourceKey(),
                "cache:* 应路由到 redis5Standalone，serverInfoByKey 应返回该 datasource 的 info");
        assertTrue(info.isKnown(), "redis5Standalone 应 known=true");
        assertEquals(5, info.getVersion().getMajor(), "redis5Standalone major 应为 5");
    }

    @Test
    public void testServerInfoReturnsDefaultDatasourceInfo() {
        // default-source=redis3Standalone
        RedisServerInfo info = template.serverInfo();
        log.info("serverInfo() datasourceKey={}, version={}",
                info.getDatasourceKey(), info.isKnown() ? info.getVersion().getRaw() : "unknown");
        assertEquals("redis3Standalone", info.getDatasourceKey(),
                "serverInfo() 应返回默认 datasource=redis3Standalone 的 info");
        assertTrue(info.isKnown(), "redis3Standalone 应 known=true");
    }

    @Test
    public void testServerInfoByDatasourceKeyForAllSix() {
        log.info("验证 serverInfo(datasourceKey) 对 6 个 datasource 均可用");
        for (String ds : expectedKnownDatasources()) {
            RedisServerInfo info = template.serverInfo(ds);
            assertNotNull(info, "serverInfo(" + ds + ") 不应返回 null");
            assertEquals(ds, info.getDatasourceKey(),
                    "serverInfo(" + ds + ").datasourceKey 必须匹配");
            assertTrue(info.isKnown(), "datasource=[" + ds + "] 应 known=true");
        }
        assertRedis7ClusterCompatibilityBoundary(registry.getServerInfos());
    }

    private List<String> expectedKnownDatasources() {
        return matrixExpectation.getKnownDatasources();
    }

    private void assertKnownServerInfo(String ds, RedisServerInfo info) {
        assertNotNull(info, "datasource=[" + ds + "] 的 serverInfo 不应为 null");
        assertTrue(info.isKnown(), "datasource=[" + ds + "] 应探测成功 known=true");
        assertNotNull(info.getVersion(), "datasource=[" + ds + "] version 不应为 null");
        assertEquals(ds, info.getDatasourceKey(), "datasource=[" + ds + "] 的 serverInfo.datasourceKey 必须匹配");
        assertNull(info.getErrorMessage(), "datasource=[" + ds + "] 探测成功时 errorMessage 应为 null");
    }

    private void assertUnknownServerInfo(String ds, RedisServerInfo info) {
        assertNotNull(info, "datasource=[" + ds + "] 的 serverInfo 不应为 null");
        assertFalse(info.isKnown(), "datasource=[" + ds + "] 在 matrix YAML 中声明为 unknown，应探测失败");
        assertEquals(ds, info.getDatasourceKey(), "datasource=[" + ds + "] 的 serverInfo.datasourceKey 必须匹配");
        assertNull(info.getVersion(), "datasource=[" + ds + "] 探测失败时 version 应为 null");
        assertNotNull(info.getErrorMessage(), "datasource=[" + ds + "] 探测失败时应有脱敏 errorMessage");
        assertFalse(info.getErrorMessage().trim().isEmpty(), "datasource=[" + ds + "] errorMessage 不应为空白");
    }

    private void assertExceptionChainContains(Throwable throwable, String expected, String message) {
        Throwable current = throwable;
        while (current != null) {
            String text = String.valueOf(current.getMessage()).toLowerCase();
            if (text.contains(expected.toLowerCase())) {
                return;
            }
            current = current.getCause();
        }
        fail(message + ": " + throwable);
    }

    private Set<Integer> clusterNodePorts(LettuceConnectionFactory connectionFactory) {
        Set<Integer> ports = new HashSet<>();
        connectionFactory.getClusterConfiguration().getClusterNodes()
                .forEach(node -> ports.add(node.getPort()));
        return ports;
    }

    private boolean isRedis7ClusterSupported() {
        return !matrixExpectation.getUnknownDatasources().contains(REDIS7_CLUSTER);
    }

    private void assertRedis7ClusterCompatibilityBoundary(Map<String, RedisServerInfo> infos) {
        RedisServerInfo redis7Cluster = infos.get(REDIS7_CLUSTER);
        assertNotNull(redis7Cluster, "redis7Cluster 的 serverInfo 不应为 null");
        if (isRedis7ClusterSupported()) {
            assertTrue(redis7Cluster.isKnown(), "redis7Cluster 在当前 matrix YAML 预期中应 known=true");
        } else {
            assertRedis7ClusterUnsupported(redis7Cluster);
        }
    }

    private void assertRedis7ClusterUnsupported(RedisServerInfo redis7Cluster) {
        log.info("Redis 7 cluster 兼容边界: known={}, errorMessage={}, boundary={}",
                redis7Cluster.isKnown(), redis7Cluster.getErrorMessage(), matrixExpectation.getCompatibilityBoundary());
        assertUnknownServerInfo(REDIS7_CLUSTER, redis7Cluster);
    }

    private String versionText(RedisServerInfo info) {
        if (info == null || info.getVersion() == null) {
            return "unknown";
        }
        return info.getVersion().getRaw();
    }
}
