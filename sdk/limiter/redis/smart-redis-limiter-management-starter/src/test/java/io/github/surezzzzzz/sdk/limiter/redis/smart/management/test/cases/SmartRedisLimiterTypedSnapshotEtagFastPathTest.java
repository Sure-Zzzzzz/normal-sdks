package io.github.surezzzzzz.sdk.limiter.redis.smart.management.test.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.directory.SmartRedisLimiterDirectoryObject;
import io.github.surezzzzzz.sdk.limiter.redis.smart.directory.SmartRedisLimiterDirectoryProvider;
import io.github.surezzzzzz.sdk.limiter.redis.smart.directory.SmartRedisLimiterServiceDeclaration;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.exception.SmartRedisLimiterPolicyConflictException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.view.SmartRedisLimiterPolicyDataScope;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.service.SmartRedisLimiterTypedPolicyManagementService;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.support.SmartRedisLimiterEtagHelper;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.test.SmartRedisLimiterManagementTestApplication;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterLimit;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.typed.SmartRedisLimiterTypedPolicyKey;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 快照 304 快路径行为回归：currentEtag 只依赖 revision（与 snapshot 的 ETag 同源），
 * 空库/新服务语义一致，LEGACY 服务协议冲突语义一致。
 *
 * <p>快速路径的价值断言是结构性的：currentEtag 的实现只调用 findRevision 单行查询，
 * 不触碰规则表——由 {@code assertRuleQueriesUnchangedByCurrentEtag} 以查询计数装饰
 * Repository 验证。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SmartRedisLimiterManagementTestApplication.class)
@Import(SmartRedisLimiterTypedSnapshotEtagFastPathTest.FastPathTestConfiguration.class)
public class SmartRedisLimiterTypedSnapshotEtagFastPathTest {

    private static final String TYPED_SERVICE = "fastpath-test-service";
    private static final String RESOURCE = "order-create";
    private static final String OPERATOR = "operator-fastpath";

    @Autowired
    private SmartRedisLimiterTypedPolicyManagementService managementService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private SmartRedisLimiterTypedRuleRepositoryHolder countingRepository;

    private static SmartRedisLimiterTypedPolicyKey key(String selector, String objectId) {
        return new SmartRedisLimiterTypedPolicyKey(
                TYPED_SERVICE, RESOURCE,
                io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterDataDimension.RESOURCE,
                io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterRuleSelector.valueOf(selector),
                "shared",
                null,
                "EXACT".equals(selector) ? objectId : null);
    }

    private static List<SmartRedisLimiterLimit> limits() {
        return Collections.singletonList(new SmartRedisLimiterLimit(60L, 1L,
                io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterTimeUnit.MINUTES));
    }

    @BeforeEach
    public void recreateTables() throws IOException {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        jdbcTemplate.execute("DROP TABLE IF EXISTS smart_redis_limiter_typed_rule_limit");
        jdbcTemplate.execute("DROP TABLE IF EXISTS smart_redis_limiter_typed_rule");
        jdbcTemplate.execute("DROP TABLE IF EXISTS smart_redis_limiter_policy_limit");
        jdbcTemplate.execute("DROP TABLE IF EXISTS smart_redis_limiter_policy");
        jdbcTemplate.execute("DROP TABLE IF EXISTS smart_redis_limiter_policy_revision");
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
        Path ddlPath = Paths.get(System.getProperty("user.dir"),
                "sdk/limiter/redis/smart-redis-limiter-management-starter/docs/schema.sql");
        if (!Files.exists(ddlPath)) {
            ddlPath = Paths.get(System.getProperty("user.dir"), "docs", "schema.sql");
        }
        String ddl = new String(Files.readAllBytes(ddlPath), java.nio.charset.StandardCharsets.UTF_8);
        for (String statement : ddl.split(";")) {
            String stripped = java.util.Arrays.stream(statement.split("\\R"))
                    .filter(line -> !line.trim().startsWith("--"))
                    .map(String::trim)
                    .reduce((left, right) -> left + " " + right)
                    .orElse("");
            if (!stripped.isEmpty()) {
                jdbcTemplate.execute(stripped);
            }
        }
        countingRepository.ruleQueryCount.set(0);
    }

    @Test
    public void testCurrentEtagMatchesSnapshotEtagAndSurvivesEmptyRevision() {
        // 空库（无 revision 行）：currentEtag 与 snapshot 的 ETag 必须同源一致
        String etagBefore = managementService.currentEtag(TYPED_SERVICE);
        SmartRedisLimiterTypedPolicyManagementService.TypedSnapshotView view =
                managementService.snapshot(TYPED_SERVICE);
        assertEquals(view.getEtag(), etagBefore, "空 revision 时 fast-path ETag 必须与快照 ETag 一致");
        assertTrue(SmartRedisLimiterEtagHelper.matches(etagBefore, view.getEtag()), "ETag 必须自匹配");
    }

    @Test
    public void testCurrentEtagAdvancesWithWritesAndStaysStableOnReads() {
        String before = managementService.currentEtag(TYPED_SERVICE);
        SmartRedisLimiterPolicyDataScope scope = SmartRedisLimiterPolicyDataScope.all();
        managementService.create(key("DEFAULT", null), Boolean.TRUE, limits(), OPERATOR, scope);
        String afterCreate = managementService.currentEtag(TYPED_SERVICE);
        assertNotEquals(before, afterCreate, "写入后 fast-path ETag 必须变化（revision 递增）");
        // 连续读不改 revision：ETag 稳定（304 命中的前提）
        assertEquals(afterCreate, managementService.currentEtag(TYPED_SERVICE),
                "只读操作不得改变 fast-path ETag");
        // 快路径 ETag 必须仍与全量快照 ETag 一致
        assertEquals(managementService.snapshot(TYPED_SERVICE).getEtag(), afterCreate,
                "写入后 fast-path ETag 必须与快照 ETag 一致");
    }

    @Test
    public void testCurrentEtagNeverTouchesRuleTable() {
        SmartRedisLimiterPolicyDataScope scope = SmartRedisLimiterPolicyDataScope.all();
        managementService.create(key("DEFAULT", null), Boolean.TRUE, limits(), OPERATOR, scope);
        managementService.snapshot(TYPED_SERVICE);
        long before = countingRepository.ruleQueryCount.get();
        managementService.currentEtag(TYPED_SERVICE);
        managementService.currentEtag(TYPED_SERVICE);
        assertEquals(before, countingRepository.ruleQueryCount.get(),
                "currentEtag 不得触发全量规则查询（304 快路径的结构性保证）");
    }

    @Test
    public void testCurrentEtagOnLegacyServiceProtocolConflicts() {
        assertThrows(SmartRedisLimiterPolicyConflictException.class, () ->
                        managementService.currentEtag("legacy-fastpath-service"),
                "LEGACY 服务 fast-path 必须与快照同语义协议冲突");
    }

    /**
     * 快路径测试装配：查询计数装饰 Repository（@Primary 覆盖默认 JDBC 实现）+ 编程式目录
     */
    @Configuration
    public static class FastPathTestConfiguration {

        @Bean
        public SmartRedisLimiterDirectoryProvider fastPathTestDirectoryProvider() {
            return new SmartRedisLimiterDirectoryProvider() {
                @Override
                public List<SmartRedisLimiterServiceDeclaration> listServices() {
                    return Arrays.asList(findService(TYPED_SERVICE), findService("legacy-fastpath-service"));
                }

                @Override
                public SmartRedisLimiterServiceDeclaration findService(String serviceCode) {
                    if (TYPED_SERVICE.equals(serviceCode)) {
                        SmartRedisLimiterServiceDeclaration declaration = new SmartRedisLimiterServiceDeclaration();
                        declaration.setServiceCode(TYPED_SERVICE);
                        declaration.setControlMode("TYPED_V2");
                        declaration.setDisplayName("快路径测试服务");
                        declaration.getResources().put(RESOURCE, Collections.singletonList("RESOURCE"));
                        declaration.getNamespaces().put("RESOURCE", "shared");
                        return declaration;
                    }
                    if ("legacy-fastpath-service".equals(serviceCode)) {
                        SmartRedisLimiterServiceDeclaration declaration = new SmartRedisLimiterServiceDeclaration();
                        declaration.setServiceCode("legacy-fastpath-service");
                        declaration.setControlMode("LEGACY_V1");
                        return declaration;
                    }
                    return null;
                }

                @Override
                public List<SmartRedisLimiterDirectoryObject> listObjects(String serviceCode,
                                                                          String dimension,
                                                                          String customType,
                                                                          String keyword,
                                                                          int limit) {
                    return Collections.emptyList();
                }
            };
        }

        /**
         * 规则查询计数装饰：委托真实 JDBC 实现，统计 findEnabledByServiceCode 调用次数
         * （currentEtag 快路径的结构性断言依赖此计数）
         */
        @Bean
        @org.springframework.context.annotation.Primary
        public SmartRedisLimiterTypedRuleRepositoryHolder countingTypedRuleRepository(
                org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbcTemplate) {
            io.github.surezzzzzz.sdk.limiter.redis.smart.management.repository.SmartRedisLimiterTypedRuleRepository
                    delegate = new io.github.surezzzzzz.sdk.limiter.redis.smart.management.repository.JdbcSmartRedisLimiterTypedRuleRepository(
                    jdbcTemplate);
            return new SmartRedisLimiterTypedRuleRepositoryHolder(delegate);
        }
    }

    /**
     * 计数装饰 Repository：公开查询计数供断言
     */
    public static class SmartRedisLimiterTypedRuleRepositoryHolder
            implements io.github.surezzzzzz.sdk.limiter.redis.smart.management.repository.SmartRedisLimiterTypedRuleRepository {

        public final java.util.concurrent.atomic.AtomicLong ruleQueryCount =
                new java.util.concurrent.atomic.AtomicLong();
        private final io.github.surezzzzzz.sdk.limiter.redis.smart.management.repository.SmartRedisLimiterTypedRuleRepository delegate;

        SmartRedisLimiterTypedRuleRepositoryHolder(
                io.github.surezzzzzz.sdk.limiter.redis.smart.management.repository.SmartRedisLimiterTypedRuleRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.entity.SmartRedisLimiterTypedRuleEntity findById(long id) {
            return delegate.findById(id);
        }

        @Override
        public io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.entity.SmartRedisLimiterTypedRuleEntity findById(long id,
                                                                                                                              io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.view.SmartRedisLimiterPolicyDataScope scope) {
            return delegate.findById(id, scope);
        }

        @Override
        public io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.entity.SmartRedisLimiterTypedRuleEntity findByIdentity(
                String serviceCode, String resourceCode, String dimension, String selector,
                String namespace, String customType, String objectId) {
            return delegate.findByIdentity(serviceCode, resourceCode, dimension, selector,
                    namespace, customType, objectId);
        }

        @Override
        public long insert(io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.entity.SmartRedisLimiterTypedRuleEntity entity) {
            return delegate.insert(entity);
        }

        @Override
        public boolean replaceLimits(long id, long expectedRowVersion,
                                     List<io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterLimit> limits,
                                     java.time.Instant updatedAt,
                                     io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.view.SmartRedisLimiterPolicyDataScope scope) {
            return delegate.replaceLimits(id, expectedRowVersion, limits, updatedAt, scope);
        }

        @Override
        public boolean updateEnabled(long id, long expectedRowVersion, boolean enabled, java.time.Instant updatedAt,
                                     io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.view.SmartRedisLimiterPolicyDataScope scope) {
            return delegate.updateEnabled(id, expectedRowVersion, enabled, updatedAt, scope);
        }

        @Override
        public boolean delete(long id, long expectedRowVersion,
                              io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.view.SmartRedisLimiterPolicyDataScope scope) {
            return delegate.delete(id, expectedRowVersion, scope);
        }

        @Override
        public List<io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.entity.SmartRedisLimiterTypedRuleEntity> findEnabledByServiceCode(
                String serviceCode) {
            ruleQueryCount.incrementAndGet();
            return delegate.findEnabledByServiceCode(serviceCode);
        }

        @Override
        public List<io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.entity.SmartRedisLimiterTypedRuleEntity> query(
                io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.view.SmartRedisLimiterTypedRuleQuery query,
                io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.view.SmartRedisLimiterPolicyDataScope scope) {
            return delegate.query(query, scope);
        }

        @Override
        public long count(
                io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.view.SmartRedisLimiterTypedRuleQuery query,
                io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.view.SmartRedisLimiterPolicyDataScope scope) {
            return delegate.count(query, scope);
        }
    }
}
