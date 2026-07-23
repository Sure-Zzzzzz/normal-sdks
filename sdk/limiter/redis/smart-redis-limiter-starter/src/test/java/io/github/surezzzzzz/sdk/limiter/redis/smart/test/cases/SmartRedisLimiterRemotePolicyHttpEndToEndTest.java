package io.github.surezzzzzz.sdk.limiter.redis.smart.test.cases;

import io.github.surezzzzzz.sdk.limiter.redis.fixture.ManagementServerTestApplication;
import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterContext;
import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterAutoConfiguration;
import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.*;
import io.github.surezzzzzz.sdk.limiter.redis.smart.execution.SmartRedisLimiterExecutionCoordinator;
import io.github.surezzzzzz.sdk.limiter.redis.smart.execution.SmartRedisLimiterExecutionOutcome;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.configuration.SmartRedisLimiterManagementProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.SmartRedisLimiterManagementConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.request.SmartRedisLimiterPolicyCreateRequest;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.service.SmartRedisLimiterPolicyManagementService;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterLimit;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicy;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicyKey;
import io.github.surezzzzzz.sdk.limiter.redis.smart.policy.SmartRedisLimiterPolicyRefreshManager;
import io.github.surezzzzzz.sdk.limiter.redis.smart.policy.SmartRedisLimiterPolicySnapshotStore;
import io.github.surezzzzzz.sdk.limiter.redis.smart.policy.model.SmartRedisLimiterAcceptedPolicySnapshot;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.SmartRedisLimiterKeyHelper;
import io.github.surezzzzzz.sdk.limiter.redis.smart.test.SmartRedisLimiterTestApplication;
import io.github.surezzzzzz.sdk.redis.route.model.RedisServerInfo;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockingDetails;

/**
 * Limiter 与 Management 快照 HTTP 协议端到端测试
 *
 * @author surezzzzzz
 */
@Slf4j
@EnabledIfSystemProperty(named = "spring.profiles.active", matches = ".*2\\.7\\.9.*")
public class SmartRedisLimiterRemotePolicyHttpEndToEndTest {

    private static final String MODULE_PATH =
            "sdk/limiter/redis/smart-redis-limiter-management-starter";
    private static final String DDL_PATH = MODULE_PATH + "/docs/mysql-schema.sql";
    private static final String SERVICE_CODE = "remote-policy-e2e-service";
    private static final String RESOURCE_CODE = "remote-policy-e2e-resource";
    private static final String SUBJECT = "remote-policy-e2e-subject";
    private static final String POLICY_TOKEN = "remote-policy-e2e-token";
    private static final long REMOTE_SECOND_LIMIT = 2L;
    private static final long REMOTE_MINUTE_LIMIT = 2L;
    private static final long LOCAL_LIMIT = 10L;

    @Test
    public void testLimiterFetchesManagementSnapshotAndEnforcesRemotePolicyThroughRedisRoute() throws Exception {
        ConfigurableApplicationContext managementContext = startManagement();
        try {
            JdbcTemplate jdbcTemplate = managementContext.getBean(JdbcTemplate.class);
            recreateTables(jdbcTemplate);
            SmartRedisLimiterPolicyManagementService managementService = managementContext.getBean(
                    SmartRedisLimiterPolicyManagementService.class);
            managementService.create(createRequest(), "remote-policy-e2e-seed");

            int port = ((WebServerApplicationContext) managementContext).getWebServer().getPort();
            SmartRedisLimiterManagementProperties managementProperties = managementContext.getBean(
                    SmartRedisLimiterManagementProperties.class);
            String snapshotUrl = "http://127.0.0.1:" + port
                    + managementProperties.getApi().getBasePath()
                    + SmartRedisLimiterManagementConstant.PATH_POLICY_SNAPSHOT;
            log.info("Management 快照服务已使用随机端口启动, port={}", port);
            assertUnauthorizedWithoutToken(snapshotUrl);

            ConfigurableApplicationContext limiterContext = startLimiter(snapshotUrl);
            try {
                SmartRedisLimiterPolicyRefreshManager refreshManager = limiterContext.getBean(
                        SmartRedisLimiterPolicyRefreshManager.class);
                SmartRedisLimiterPolicySnapshotStore snapshotStore = limiterContext.getBean(
                        SmartRedisLimiterPolicySnapshotStore.class);
                SmartRedisLimiterExecutionCoordinator coordinator = limiterContext.getBean(
                        SmartRedisLimiterExecutionCoordinator.class);
                RedisRouteTemplate redisRouteTemplate = limiterContext.getBean(RedisRouteTemplate.class);
                assertFalse(mockingDetails(redisRouteTemplate).isMock(),
                        "远程策略 E2E 必须使用真实 RedisRouteTemplate，不能被测试替身覆盖");
                String expectedPolicyKey = SmartRedisLimiterKeyHelper.buildPolicyBaseKey(
                        SERVICE_CODE, RESOURCE_CODE, SUBJECT);
                cleanRedis(redisRouteTemplate, expectedPolicyKey);

                assertTrue(refreshManager.refresh(), "首次刷新必须发起实际 HTTP 请求");
                SmartRedisLimiterAcceptedPolicySnapshot accepted = snapshotStore.getCurrent();
                assertAcceptedSnapshot(refreshManager, accepted);
                long acceptedRevision = accepted.getRevision();

                assertFalse(expectedPolicyKey.contains(SUBJECT), "动态策略 Redis Key 不得包含原始 subject");

                SmartRedisLimiterExecutionOutcome first = execute(coordinator);
                SmartRedisLimiterExecutionOutcome second = execute(coordinator);
                SmartRedisLimiterExecutionOutcome third = execute(coordinator);
                log.info("远程策略 Redis 执行结果, policySource={}, policyRevision={}, routeKey={}, "
                                + "datasourceKey={}, passSequence={}/{}/{}",
                        first.getPlan().getPolicySource(), first.getPlan().getPolicyRevision(),
                        first.getPlan().getRouteKey(), first.getResult().getDatasourceKey(),
                        first.getResult().isPassed(), second.getResult().isPassed(), third.getResult().isPassed());

                assertRemoteExecution(first, redisRouteTemplate, expectedPolicyKey, acceptedRevision, true);
                assertRemoteExecution(second, redisRouteTemplate, expectedPolicyKey, acceptedRevision, true);
                assertRemoteExecution(third, redisRouteTemplate, expectedPolicyKey, acceptedRevision, false);
                assertEquals(REMOTE_SECOND_LIMIT, third.getResult().getLimit(),
                        "第三次请求必须按远程秒级阈值拒绝，不能继续使用本地宽松阈值");

                assertTrue(refreshManager.refresh(), "第二次刷新必须通过 If-None-Match 重新校验");
                assertSame(accepted, snapshotStore.getCurrent(),
                        "Management 返回 304 时 limiter 必须保留同一 last-known-good 快照");
                assertTrue(refreshManager.getRefreshState().isLastAttemptSuccessful(),
                        "304 必须记录为成功刷新");
                assertEquals(acceptedRevision, refreshManager.getRefreshState().getAcceptedRevision(),
                        "304 不得改变已接受 revision");
                assertEquals(acceptedRevision, jdbcTemplate.queryForObject(
                                "SELECT revision FROM smart_redis_limiter_policy_revision WHERE service_code = ?",
                                Long.class, SERVICE_CODE).longValue(),
                        "只读条件拉取不得修改 Management revision");
            } finally {
                RedisRouteTemplate redisRouteTemplate = limiterContext.getBean(RedisRouteTemplate.class);
                cleanRedis(redisRouteTemplate, SmartRedisLimiterKeyHelper.buildPolicyBaseKey(
                        SERVICE_CODE, RESOURCE_CODE, SUBJECT));
                limiterContext.close();
            }
        } finally {
            managementContext.close();
        }
    }

    private ConfigurableApplicationContext startManagement() {
        return new SpringApplicationBuilder(ManagementServerTestApplication.class)
                .web(WebApplicationType.SERVLET)
                .profiles("local")
                .properties("server.port=0")
                .run();
    }

    private ConfigurableApplicationContext startLimiter(String snapshotUrl) {
        return new SpringApplicationBuilder(SmartRedisLimiterTestApplication.class,
                SmartRedisLimiterAutoConfiguration.class)
                .web(WebApplicationType.NONE)
                .run(
                        commandLineProperty(SmartRedisLimiterStarterConstant.CONFIG_PATH_ME, SERVICE_CODE),
                        commandLineProperty(SmartRedisLimiterStarterConstant.CONFIG_PATH_REMOTE_POLICY_ENABLE, "true"),
                        commandLineProperty(SmartRedisLimiterStarterConstant.CONFIG_PATH_REMOTE_POLICY_SNAPSHOT_URL, snapshotUrl),
                        commandLineProperty(SmartRedisLimiterStarterConstant.CONFIG_PATH_REMOTE_POLICY_POLICY_TOKEN, POLICY_TOKEN),
                        commandLineProperty(SmartRedisLimiterStarterConstant.CONFIG_PREFIX_REMOTE_POLICY + ".initial-refresh", "false"));
    }

    private String commandLineProperty(String path, String value) {
        return "--" + path + "=" + value;
    }

    private void assertAcceptedSnapshot(SmartRedisLimiterPolicyRefreshManager refreshManager,
                                        SmartRedisLimiterAcceptedPolicySnapshot accepted) {
        assertNotNull(accepted, "Management 的 200 快照必须被 limiter 接受");
        assertTrue(accepted.getRevision() >= 0L, "Management 返回的 revision 必须是非负版本号");
        assertEquals(SERVICE_CODE, accepted.getSnapshot().getServiceCode(),
                "limiter 必须接收请求服务的快照");
        assertTrue(accepted.getEtag().startsWith("\"srlm-"),
                "真实 Management 响应必须提供强 ETag");
        assertEquals(1, accepted.getSnapshot().getPolicies().size(),
                "快照必须仅包含已启用的已落库策略");

        SmartRedisLimiterPolicy policy = accepted.findPolicy(
                new SmartRedisLimiterPolicyKey(SERVICE_CODE, RESOURCE_CODE, SUBJECT));
        assertNotNull(policy, "limiter 安装的快照必须精确保留策略 identity");
        assertEquals(2, policy.getLimits().size(), "Management 多窗口策略不得在 HTTP 传输中丢失");
        assertEquals(REMOTE_SECOND_LIMIT, policy.getLimits().get(0).getCount(), "秒级窗口阈值必须精确保留");
        assertEquals(SmartRedisLimiterTimeUnit.SECONDS, policy.getLimits().get(0).getUnit(),
                "秒级窗口单位必须精确保留");
        assertEquals(REMOTE_MINUTE_LIMIT, policy.getLimits().get(1).getCount(), "分钟窗口阈值必须精确保留");
        assertEquals(SmartRedisLimiterTimeUnit.MINUTES, policy.getLimits().get(1).getUnit(),
                "分钟窗口单位必须精确保留");
        assertTrue(refreshManager.getRefreshState().isLastAttemptSuccessful(),
                "首次成功拉取必须记录成功状态");
        assertEquals(accepted.getRevision(), refreshManager.getRefreshState().getAcceptedRevision(),
                "刷新状态必须记录实际接受的 revision");
    }

    private SmartRedisLimiterExecutionOutcome execute(SmartRedisLimiterExecutionCoordinator coordinator) {
        SmartRedisLimiterContext context = SmartRedisLimiterContext.builder()
                .attribute(SmartRedisLimiterContextAttribute.PRECOMPUTED_KEY_PART, SUBJECT)
                .build();
        return coordinator.execute(
                context,
                localLimits(),
                "path",
                SmartRedisLimiterConstant.ALGORITHM_FIXED,
                "deny",
                RESOURCE_CODE);
    }

    private List<SmartRedisLimiterProperties.SmartLimitRule> localLimits() {
        return Arrays.asList(
                localLimit(LOCAL_LIMIT, 1L, SmartRedisLimiterTimeUnit.SECONDS),
                localLimit(LOCAL_LIMIT, 1L, SmartRedisLimiterTimeUnit.MINUTES));
    }

    private SmartRedisLimiterProperties.SmartLimitRule localLimit(
            long count, long window, SmartRedisLimiterTimeUnit unit) {
        SmartRedisLimiterProperties.SmartLimitRule rule = new SmartRedisLimiterProperties.SmartLimitRule();
        rule.setCount(count);
        rule.setWindow(window);
        rule.setUnit(unit);
        return rule;
    }

    private void assertRemoteExecution(SmartRedisLimiterExecutionOutcome outcome,
                                       RedisRouteTemplate redisRouteTemplate,
                                       String expectedPolicyKey,
                                       long expectedRevision,
                                       boolean expectedPassed) {
        assertEquals(SmartRedisLimiterConstant.POLICY_SOURCE_REMOTE, outcome.getPlan().getPolicySource(),
                "精确匹配的已接受远程策略必须覆盖本地 limits");
        assertEquals(expectedRevision, outcome.getPlan().getPolicyRevision(),
                "执行计划必须携带已接受快照 revision");
        assertEquals(RESOURCE_CODE, outcome.getPlan().getResourceCode(), "执行计划必须保留资源编码");
        assertEquals(expectedPolicyKey, outcome.getPlan().getBaseKey(),
                "执行计划必须使用 subject 摘要构造动态策略 Key");
        assertEquals(expectedPolicyKey, outcome.getPlan().getRouteKey(),
                "Redis Route 必须以动态策略 Key 解析数据源");
        assertEquals(REMOTE_SECOND_LIMIT, outcome.getPlan().getLimits().get(0).getCount(),
                "执行计划必须采用远程秒级阈值");
        assertEquals(REMOTE_MINUTE_LIMIT, outcome.getPlan().getLimits().get(1).getCount(),
                "执行计划必须采用远程分钟阈值");

        assertEquals(expectedPassed, outcome.getResult().isPassed(), "真实 Redis 执行结果必须符合远程策略阈值");
        assertEquals(REMOTE_SECOND_LIMIT, outcome.getResult().getLimit(), "结果阈值必须来自远程策略");
        assertFalse(outcome.getResult().isFallback(), "Redis Route 可用时不得进入降级逻辑");
        assertEquals(expectedPolicyKey, outcome.getResult().getRouteKey(),
                "执行结果必须携带 subject 摘要后的路由 Key");
        assertFalse(outcome.getResult().getRouteKey().contains(SUBJECT),
                "执行结果路由 Key 不得暴露原始 subject");
        assertTrue(outcome.getResult().isRouteRequired(), "远程策略执行必须要求 Redis Route");
        assertTrue(outcome.getResult().isRouteResolved(), "真实 Redis Route 必须解析到数据源");
        RedisServerInfo expectedServerInfo = redisRouteTemplate.serverInfoByKey(expectedPolicyKey);
        assertTrue(expectedServerInfo.isKnown(), "真实 Redis Route 必须探测到目标 Redis 服务信息");
        assertEquals(expectedServerInfo.getDatasourceKey(), outcome.getResult().getDatasourceKey(),
                "执行结果 datasource 必须与同一动态策略 Key 的实际 Route 解析结果一致");
        assertEquals(expectedServerInfo.getRedisMode(), outcome.getResult().getRedisMode(),
                "执行结果 Redis 模式必须与同一动态策略 Key 的实际 Route 解析结果一致");
    }

    private SmartRedisLimiterPolicyCreateRequest createRequest() {
        SmartRedisLimiterPolicyCreateRequest request = new SmartRedisLimiterPolicyCreateRequest();
        request.setKey(new SmartRedisLimiterPolicyKey(SERVICE_CODE, RESOURCE_CODE, SUBJECT));
        request.setLimits(Arrays.asList(
                new SmartRedisLimiterLimit(REMOTE_SECOND_LIMIT, 1L, SmartRedisLimiterTimeUnit.SECONDS),
                new SmartRedisLimiterLimit(REMOTE_MINUTE_LIMIT, 1L, SmartRedisLimiterTimeUnit.MINUTES)));
        request.setEnabled(true);
        return request;
    }

    private void assertUnauthorizedWithoutToken(String snapshotUrl) {
        try {
            new RestTemplate().getForEntity(
                    snapshotUrl + "?serviceCode=" + SERVICE_CODE, String.class);
        } catch (org.springframework.web.client.HttpClientErrorException ex) {
            assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode(),
                    "未携带固定 token 的真实 Management 快照请求必须返回 401");
            return;
        }
        throw new AssertionError("未携带固定 token 的真实 Management 快照请求必须返回 401");
    }

    private void cleanRedis(RedisRouteTemplate redisRouteTemplate, String routeKey) {
        Set<String> keys = redisRouteTemplate.stringTemplateByKey(routeKey).keys(
                SmartRedisLimiterRedisKeyConstant.KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisRouteTemplate.stringTemplateByKey(routeKey).delete(keys);
        }
    }

    private void recreateTables(JdbcTemplate jdbcTemplate) throws IOException {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        jdbcTemplate.execute("DROP TABLE IF EXISTS smart_redis_limiter_policy_limit");
        jdbcTemplate.execute("DROP TABLE IF EXISTS smart_redis_limiter_policy");
        jdbcTemplate.execute("DROP TABLE IF EXISTS smart_redis_limiter_policy_revision");
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
        String ddl = new String(Files.readAllBytes(resolveDdlPath()), StandardCharsets.UTF_8);
        for (String statement : ddl.split(";")) {
            if (!statement.trim().isEmpty()) {
                jdbcTemplate.execute(statement);
            }
        }
    }

    private Path resolveDdlPath() {
        Path rootPath = Paths.get(System.getProperty("user.dir"), DDL_PATH);
        if (Files.exists(rootPath)) {
            return rootPath;
        }
        Path siblingModulePath = Paths.get(System.getProperty("user.dir"), "..",
                "smart-redis-limiter-management-starter", "docs", "mysql-schema.sql").normalize();
        if (Files.exists(siblingModulePath)) {
            return siblingModulePath;
        }
        throw new IllegalStateException("未找到 Management MySQL 建表脚本");
    }
}
