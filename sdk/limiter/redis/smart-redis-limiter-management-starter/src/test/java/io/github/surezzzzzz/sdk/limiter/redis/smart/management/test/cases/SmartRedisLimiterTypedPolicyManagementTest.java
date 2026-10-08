package io.github.surezzzzzz.sdk.limiter.redis.smart.management.test.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterManagementOperation;
import io.github.surezzzzzz.sdk.limiter.redis.smart.event.SmartRedisLimiterTypedManagementEvent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.response.SmartRedisLimiterTypedMutationResponse;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.response.SmartRedisLimiterTypedRuleResponse;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.directory.SmartRedisLimiterDirectoryObject;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.directory.SmartRedisLimiterDirectoryProvider;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.directory.SmartRedisLimiterServiceDeclaration;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.exception.SmartRedisLimiterManagementValidationException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.exception.SmartRedisLimiterPolicyConflictException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.view.SmartRedisLimiterPolicyDataScope;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.view.SmartRedisLimiterTypedRuleQuery;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.service.DefaultSmartRedisLimiterTypedPolicyManagementService;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.service.SmartRedisLimiterTypedPolicyManagementService;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.support.SmartRedisLimiterEtagHelper;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.test.SmartRedisLimiterManagementTestApplication;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterLimit;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.typed.SmartRedisLimiterTypedPolicyKey;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.typed.SmartRedisLimiterTypedPolicySnapshot;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 类型化策略管理服务真库测试：CRUD、目录校验、冲突语义与快照契约
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SmartRedisLimiterManagementTestApplication.class)
@Import(SmartRedisLimiterTypedPolicyManagementTest.TypedTestConfiguration.class)
public class SmartRedisLimiterTypedPolicyManagementTest {

    private static final String TYPED_SERVICE = "typed-test-service";
    private static final String LEGACY_SERVICE = "legacy-test-service";
    private static final String RESOURCE = "order-create";
    private static final String OPERATOR = "operator-unit";

    @Autowired
    private SmartRedisLimiterTypedPolicyManagementService managementService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TypedTestConfiguration.CapturingTypedEventPublisher eventPublisher;

    private static SmartRedisLimiterTypedPolicyKey key(String dimension, String selector,
                                                       String namespace, String objectId) {
        return new SmartRedisLimiterTypedPolicyKey(
                TYPED_SERVICE, RESOURCE,
                io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterDataDimension.valueOf(dimension),
                io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterRuleSelector.valueOf(selector),
                namespace,
                "CUSTOM".equals(dimension) ? "device" : null,
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
                "sdk/limiter/redis/smart-redis-limiter-management-starter/docs/mysql-schema.sql");
        if (!Files.exists(ddlPath)) {
            ddlPath = Paths.get(System.getProperty("user.dir"), "docs", "mysql-schema.sql");
        }
        String ddl = new String(Files.readAllBytes(ddlPath), java.nio.charset.StandardCharsets.UTF_8);
        for (String statement : ddl.split(";")) {
            String stripped = statement.lines()
                    .filter(line -> !line.trim().startsWith("--"))
                    .map(String::trim)
                    .reduce((left, right) -> left + " " + right)
                    .orElse("");
            if (!stripped.isEmpty()) {
                jdbcTemplate.execute(stripped);
            }
        }
        eventPublisher.events.clear();
    }

    @Test
    public void testCreateAlignsNamespaceFromDirectoryAndPublishesEvent() {
        // 客户端传入错误 namespace（client-guess），以目录声明（customer 域）对齐
        SmartRedisLimiterPolicyDataScope scope = SmartRedisLimiterPolicyDataScope.all();
        SmartRedisLimiterTypedMutationResponse result =
                managementService.create(key("CUSTOMER", "EXACT", "client-guess", "cust-001"),
                        Boolean.TRUE, limits(), OPERATOR, scope);
        assertNotNull(result.getRule(), "创建必须返回规则");
        assertEquals("customer", result.getRule().getNamespace(), "命名空间必须以目录声明对齐");
        assertEquals(1L, result.getRevision(), "首次变更后 revision 必须为 1");
        assertEquals(1, eventPublisher.events.size(), "必须发布一次类型化事件");
        SmartRedisLimiterTypedManagementEvent event = eventPublisher.events.get(0);
        assertEquals(SmartRedisLimiterManagementOperation.CREATE, event.getPayload().getOperation());
        assertEquals(83, String.valueOf(event.getPayload().getAttributes()
                        .get(SmartRedisLimiterConstant.OPERATOR_IDENTITY_ATTRIBUTE_KEY)).length(),
                "操作人摘要必须是 83 字符");
    }

    @Test
    public void testCreateCustomDimensionDefaultRuleKeepsCustomType() {
        // CUSTOM 维度允许默认额度（selector=DEFAULT），customType 仍必填且须在目录声明
        SmartRedisLimiterPolicyDataScope scope = SmartRedisLimiterPolicyDataScope.all();
        SmartRedisLimiterTypedMutationResponse result =
                managementService.create(key("CUSTOM", "DEFAULT", "custom", null),
                        Boolean.TRUE, limits(), OPERATOR, scope);
        assertEquals("device", result.getRule().getCustomType(), "CUSTOM 默认额度必须保留自定义类型");
        assertNull(result.getRule().getObjectId(), "默认额度不携带对象标识");
    }

    @Test
    public void testCreateDuplicateIdentityConflicts() {
        SmartRedisLimiterPolicyDataScope scope = SmartRedisLimiterPolicyDataScope.all();
        managementService.create(key("CUSTOMER", "EXACT", "customer", "cust-001"),
                Boolean.TRUE, limits(), OPERATOR, scope);
        assertThrows(SmartRedisLimiterPolicyConflictException.class, () ->
                        managementService.create(key("CUSTOMER", "EXACT", "customer", "cust-001"),
                                Boolean.TRUE, limits(), OPERATOR, scope),
                "同七字段身份必须返回冲突");
    }

    @Test
    public void testCreateOnLegacyServiceProtocolConflicts() {
        SmartRedisLimiterTypedPolicyKey legacyKey = new SmartRedisLimiterTypedPolicyKey(
                LEGACY_SERVICE, RESOURCE,
                io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterDataDimension.IP,
                io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterRuleSelector.DEFAULT,
                "entry", null, null);
        assertThrows(SmartRedisLimiterPolicyConflictException.class, () ->
                managementService.create(legacyKey, Boolean.TRUE, limits(), OPERATOR,
                        SmartRedisLimiterPolicyDataScope.all()), "LEGACY 服务不得创建类型化规则");
    }

    @Test
    public void testCreateDimensionNotDeclaredRejected() {
        // 目录只给 RESOURCE 声明了 IP 维度，USER 维度未声明
        SmartRedisLimiterPolicyDataScope scope = SmartRedisLimiterPolicyDataScope.all();
        assertThrows(SmartRedisLimiterManagementValidationException.class, () ->
                managementService.create(key("USER", "DEFAULT", "user", null),
                        Boolean.TRUE, limits(), OPERATOR, scope), "未声明维度必须拒绝");
    }

    @Test
    public void testUpdateWithStaleRowVersionConflicts() {
        SmartRedisLimiterPolicyDataScope scope = SmartRedisLimiterPolicyDataScope.all();
        SmartRedisLimiterTypedRuleResponse created =
                managementService.create(key("RESOURCE", "DEFAULT", "shared", null),
                        Boolean.TRUE, limits(), OPERATOR, scope).getRule();
        assertThrows(SmartRedisLimiterPolicyConflictException.class, () ->
                managementService.update(created.getId(), created.getRowVersion() - 1,
                        limits(), OPERATOR, scope), "过期行版本必须返回冲突");
    }

    @Test
    public void testStateToggleAndDeleteAdvanceRevision() {
        SmartRedisLimiterPolicyDataScope scope = SmartRedisLimiterPolicyDataScope.all();
        SmartRedisLimiterTypedRuleResponse created =
                managementService.create(key("RESOURCE", "DEFAULT", "shared", null),
                        Boolean.TRUE, limits(), OPERATOR, scope).getRule();
        long revisionAfterCreate = 1L;
        SmartRedisLimiterTypedRuleResponse disabled =
                managementService.state(created.getId(), created.getRowVersion(), false, OPERATOR, scope).getRule();
        assertEquals(Boolean.FALSE, disabled.getEnabled(), "停用必须生效");
        SmartRedisLimiterTypedRuleResponse enabled =
                managementService.state(created.getId(), disabled.getRowVersion(), true, OPERATOR, scope).getRule();
        assertEquals(Boolean.TRUE, enabled.getEnabled(), "启用必须生效");
        SmartRedisLimiterTypedMutationResponse deleted =
                managementService.delete(created.getId(), enabled.getRowVersion(), OPERATOR, scope);
        assertEquals(revisionAfterCreate + 3, deleted.getRevision(), "每次变更 revision 必须递增");
        assertThrows(SmartRedisLimiterPolicyConflictException.class, () ->
                managementService.findById(created.getId(), scope), "删除后读取必须 404 语义冲突");
    }

    @Test
    public void testSnapshotCarriesSchemaEpochRevisionAndEtag() {
        SmartRedisLimiterPolicyDataScope scope = SmartRedisLimiterPolicyDataScope.all();
        managementService.create(key("CUSTOMER", "EXACT", "customer", "cust-001"),
                Boolean.TRUE, limits(), OPERATOR, scope);
        SmartRedisLimiterTypedPolicyManagementService.TypedSnapshotView view =
                managementService.snapshot(TYPED_SERVICE);
        SmartRedisLimiterTypedPolicySnapshot snapshot =
                (SmartRedisLimiterTypedPolicySnapshot) view.getSnapshot();
        assertEquals(SmartRedisLimiterConstant.TYPED_POLICY_SCHEMA_VERSION, snapshot.getSchemaVersion(),
                "快照协议版本必须为 2");
        assertEquals(2L, snapshot.getPolicyEpoch(), "策略代次必须来自目录声明");
        assertEquals(1L, snapshot.getRevision(), "创建后快照 revision 必须为 1");
        assertEquals(1, snapshot.getRules().size(), "快照必须包含一条启用规则");
        assertTrue(SmartRedisLimiterEtagHelper.matches(view.getEtag(), view.getEtag()), "ETag 必须自匹配");
        assertThrows(SmartRedisLimiterPolicyConflictException.class, () ->
                managementService.snapshot(LEGACY_SERVICE), "LEGACY 服务快照必须协议冲突");
    }

    @Test
    public void testQueryObjectIdLiteralPrefixEscape() {
        SmartRedisLimiterPolicyDataScope scope = SmartRedisLimiterPolicyDataScope.all();
        // 精确对象含 % 与 _，检索必须按字面量前缀而非通配
        managementService.create(key("CUSTOMER", "EXACT", "customer", "a%b_c.001"),
                Boolean.TRUE, limits(), OPERATOR, scope);
        managementService.create(key("CUSTOMER", "EXACT", "customer", "aXbYc002"),
                Boolean.TRUE, limits(), OPERATOR, scope);
        SmartRedisLimiterPolicyDataScope queryScope = SmartRedisLimiterPolicyDataScope.all();
        List<SmartRedisLimiterTypedRuleResponse> matched = managementService.query(
                SmartRedisLimiterTypedRuleQuery.builder()
                        .serviceCode(TYPED_SERVICE).resourceCode(RESOURCE)
                        .dimension("CUSTOMER").selector("EXACT").objectId("a%b")
                        .page(1).size(20).build(),
                queryScope).getItems();
        assertEquals(1, matched.size(), "含通配符的对象标识必须按字面前缀命中");
        assertEquals("a%b_c.001", matched.get(0).getObjectId());
    }

    /**
     * 类型化测试装配：编程式目录（含 LEGACY 对照服务与代次声明）+事件捕获
     */
    @Configuration
    public static class TypedTestConfiguration {

        @Bean
        public SmartRedisLimiterDirectoryProvider typedTestDirectoryProvider() {
            return new SmartRedisLimiterDirectoryProvider() {
                @Override
                public List<SmartRedisLimiterServiceDeclaration> listServices() {
                    return Arrays.asList(findService(TYPED_SERVICE), findService(LEGACY_SERVICE));
                }

                @Override
                public SmartRedisLimiterServiceDeclaration findService(String serviceCode) {
                    if (TYPED_SERVICE.equals(serviceCode)) {
                        SmartRedisLimiterServiceDeclaration declaration = new SmartRedisLimiterServiceDeclaration();
                        declaration.setServiceCode(TYPED_SERVICE);
                        declaration.setControlMode("TYPED_V2");
                        declaration.setDisplayName("类型化测试服务");
                        declaration.setPolicyEpoch(2L);
                        declaration.getResources().put(RESOURCE, Arrays.asList("RESOURCE", "IP", "CUSTOMER", "CUSTOM"));
                        declaration.getNamespaces().put("RESOURCE", "shared");
                        declaration.getNamespaces().put("IP", "entry");
                        declaration.getNamespaces().put("CUSTOMER", "customer");
                        declaration.getNamespaces().put("CUSTOM", "custom");
                        declaration.getCustomTypes().add("device");
                        return declaration;
                    }
                    if (LEGACY_SERVICE.equals(serviceCode)) {
                        SmartRedisLimiterServiceDeclaration declaration = new SmartRedisLimiterServiceDeclaration();
                        declaration.setServiceCode(LEGACY_SERVICE);
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

        @Bean
        public CapturingTypedEventPublisher typedTestEventPublisher() {
            return new CapturingTypedEventPublisher();
        }

        /**
         * 事件捕获发布器（直发，不挂事务同步，便于断言）
         */
        public static class CapturingTypedEventPublisher
                implements DefaultSmartRedisLimiterTypedPolicyManagementService.TypedEventPublisher {
            public final List<SmartRedisLimiterTypedManagementEvent> events =
                    Collections.synchronizedList(new ArrayList<>());

            @Override
            public void publish(SmartRedisLimiterTypedManagementEvent event) {
                events.add(event);
            }
        }
    }
}
