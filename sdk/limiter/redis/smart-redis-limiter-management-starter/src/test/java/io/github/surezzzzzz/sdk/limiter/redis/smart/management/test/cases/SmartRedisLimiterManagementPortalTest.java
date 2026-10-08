package io.github.surezzzzzz.sdk.limiter.redis.smart.management.test.cases;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.SimpleApplicationAuthorizationConstant;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataConstraintOperator;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.SimpleDataPermissionConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.*;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceAuthenticationFailureCategory;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceSubjectType;
import io.github.surezzzzzz.sdk.auth.resource.core.model.BearerResourceCredential;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationResult;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationSourceId;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourcePrincipal;
import io.github.surezzzzzz.sdk.auth.resource.core.spi.ResourceAuthenticationAdapter;
import io.github.surezzzzzz.sdk.auth.resource.server.constant.SimpleResourceServerStarterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterTimeUnit;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.request.SmartRedisLimiterPolicyCreateRequest;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.request.SmartRedisLimiterPolicyStateRequest;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.request.SmartRedisLimiterPolicyUpdateRequest;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.exception.SmartRedisLimiterManagementAccessDeniedException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.view.SmartRedisLimiterPolicyDataScope;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.service.SmartRedisLimiterPolicyManagementService;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.service.SmartRedisLimiterPolicySnapshotService;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.test.SmartRedisLimiterManagementTestApplication;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterLimit;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicyKey;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.SmartRedisLimiterManagementConstant.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 自身随机端口、真实 MySQL 与公共资源链验收；适配器只提供受控授权结果。
 */
@Slf4j
@SpringBootTest(classes = SmartRedisLimiterManagementTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "io.github.surezzzzzz.sdk.limiter.redis.smart.management.mode=portal",
        "io.github.surezzzzzz.sdk.limiter.redis.smart.management.ui.enable=false",
        "io.github.surezzzzzz.sdk.limiter.redis.smart.management.rest.policy-token=",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration",
        "io.github.surezzzzzz.sdk.auth.resource.server.enabled=true",
        "io.github.surezzzzzz.sdk.auth.resource.server.security.protected-paths[0]=/api/v1/policy/**"})
@Import(SmartRedisLimiterManagementPortalTest.PortalFixtureConfiguration.class)
public class SmartRedisLimiterManagementPortalTest {
    private static final String BASE = "/api/v1/policy";
    private static final String SERVICE_A = "mock-service-a";
    private static final String SERVICE_B = "mock-service-b";
    @Autowired
    private TestRestTemplate http;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PortalFixture fixture;
    @Autowired
    private SmartRedisLimiterPolicyManagementService service;
    @Autowired
    private SmartRedisLimiterPolicySnapshotService snapshots;

    private static DataGrant grant(String code) {
        return new DataGrant(DATA_POLICY_RESOURCE, Arrays.asList(DATA_READ, DATA_WRITE), false,
                Collections.singletonList(new DataConstraint(DATA_SERVICE_DIMENSION, DataConstraintOperator.IN,
                        Collections.singletonList(code))));
    }

    private static DataGrantDocument restricted(String code) {
        return document(Collections.singletonList(grant(code)));
    }

    private static DataGrantDocument full() {
        return document(Collections.singletonList(new DataGrant(DATA_POLICY_RESOURCE,
                Arrays.asList(DATA_READ, DATA_WRITE), true, Collections.emptyList())));
    }

    private static DataGrantDocument document(List<DataGrant> grants) {
        return new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL, SimpleDataPermissionConstant.VERSION, grants);
    }

    @BeforeEach
    void prepareOwnedSchema() {
        jdbc.execute("DROP TABLE IF EXISTS smart_redis_limiter_policy_limit");
        jdbc.execute("DROP TABLE IF EXISTS smart_redis_limiter_policy");
        jdbc.execute("DROP TABLE IF EXISTS smart_redis_limiter_policy_revision");
        Path ddl = Paths.get("docs/mysql-schema.sql");
        if (!Files.exists(ddl)) {
            ddl = Paths.get("sdk/limiter/redis/smart-redis-limiter-management-starter/docs/mysql-schema.sql");
        }
        new ResourceDatabasePopulator(new FileSystemResource(ddl)).execute(jdbc.getDataSource());
        fixture.results.clear();
    }

    @Test
    void testPortalEntryCannotBorrowCookieFixedTokenOrConsole() {
        log.info("验收 Portal 入口隔离与认证拒绝");
        String token = human(false, true, full());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange(BASE, HttpMethod.GET, null, null).getStatusCode());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.add(HttpHeaders.COOKIE, "JSESSIONID=mock-session");
        assertEquals(HttpStatus.UNAUTHORIZED, http.exchange(BASE, HttpMethod.GET,
                new HttpEntity<>(headers), JsonNode.class).getStatusCode());
        headers.clear();
        headers.add(HEADER_POLICY_TOKEN, "mock-fixed-token");
        assertEquals(HttpStatus.UNAUTHORIZED, http.exchange(BASE, HttpMethod.GET,
                new HttpEntity<>(headers), JsonNode.class).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, http.getForEntity("/management/login", String.class).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, http.getForEntity("/api/admin/v1/policy", String.class).getStatusCode());
        assertThrows(SmartRedisLimiterManagementAccessDeniedException.class, () -> service.findById(1L));
        assertThrows(SmartRedisLimiterManagementAccessDeniedException.class, () -> snapshots.getSnapshot(SERVICE_A));
    }

    @Test
    void testDataScopeProtectsListCountDetailAndAllMutations() {
        log.info("验收跨 serviceCode 的列表、统计、详情与写范围");
        String all = human(true, true, full());
        long a = create(all, SERVICE_A);
        long b = create(all, SERVICE_B);
        String restricted = human(true, true, restricted(SERVICE_A));
        JsonNode page = exchange(BASE, HttpMethod.GET, null, restricted).getBody();
        assertNotNull(page);
        assertEquals(1L, page.path("totalElements").asLong());
        assertEquals(1, page.path("items").size());
        assertEquals(a, page.path("items").get(0).path("id").asLong());
        assertEquals(0L, exchange(BASE + "?serviceCode=" + SERVICE_B, HttpMethod.GET, null, restricted)
                .getBody().path("totalElements").asLong());
        for (HttpMethod method : Arrays.asList(HttpMethod.GET, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE)) {
            Object body = method == HttpMethod.PUT ? update(0L, 2L) : method == HttpMethod.PATCH ? state(0L, false) : null;
            String suffix = method == HttpMethod.DELETE ? "?expectedRowVersion=0" : "";
            assertEquals(HttpStatus.NOT_FOUND, exchange(BASE + "/" + b + suffix, method, body, restricted).getStatusCode());
        }
        assertEquals(HttpStatus.FORBIDDEN, exchange(BASE, HttpMethod.POST, request(SERVICE_B), restricted).getStatusCode());
        assertEquals(0L, exchange(BASE + "/" + b, HttpMethod.GET, null, all).getBody().path("rowVersion").asLong());
        assertEquals(HttpStatus.OK, exchange(BASE + "/" + a, HttpMethod.PUT, update(0L, 2L), restricted).getStatusCode());
        assertEquals(HttpStatus.CONFLICT, exchange(BASE + "/" + a, HttpMethod.PUT, update(0L, 3L), restricted).getStatusCode());
        assertEquals(HttpStatus.OK, exchange(BASE + "/" + a, HttpMethod.PATCH, state(1L, false), restricted).getStatusCode());
        assertEquals(HttpStatus.OK, exchange(BASE + "/" + a + "?expectedRowVersion=2",
                HttpMethod.DELETE, null, restricted).getStatusCode());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM smart_redis_limiter_policy", Integer.class));
    }

    @Test
    void testReadApiDoesNotBorrowWriteApiAndPageIsNotMachineGate() {
        log.info("验收精确 API 权限与 HUMAN AKU 无 PAGE 访问");
        String noPage = human(false, true, full());
        create(noPage, SERVICE_A);
        assertEquals(HttpStatus.OK, exchange(BASE, HttpMethod.GET, null, noPage).getStatusCode());
        JsonNode capabilities = exchange(BASE + "/capabilities", HttpMethod.GET, null, noPage).getBody();
        assertFalse(capabilities.path("pageAllowed").asBoolean());
        assertFalse(capabilities.path("canWrite").asBoolean());
        String readOnly = human(true, false, full());
        assertEquals(HttpStatus.OK, exchange(BASE, HttpMethod.GET, null, readOnly).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, exchange(BASE, HttpMethod.POST, request(SERVICE_B), readOnly).getStatusCode());
        String writeOnly = fixture.token(ResourceSubjectType.HUMAN, true,
                Collections.singletonList(API_POLICY_WRITE), full());
        assertEquals(HttpStatus.FORBIDDEN, exchange(BASE, HttpMethod.GET, null, writeOnly).getStatusCode());
        String machine = fixture.token(ResourceSubjectType.SERVICE, false,
                Arrays.asList(API_POLICY_READ, API_POLICY_WRITE, API_SNAPSHOT_READ), full());
        assertEquals(HttpStatus.OK, exchange(BASE, HttpMethod.GET, null, machine).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, exchange(BASE + "/capabilities", HttpMethod.GET, null, machine).getStatusCode());
    }

    @Test
    void testDataAnnotationChainFailsClosedOnMissingOrEmptyGrantDocument() {
        log.info("验收 DATA 注解链失败关闭：无授权文档或空授权必须 403，不得 500");
        String noDocument = fixture.token(ResourceSubjectType.HUMAN, true,
                Collections.singletonList(API_POLICY_READ), null);
        assertEquals(HttpStatus.FORBIDDEN, exchange(BASE, HttpMethod.GET, null, noDocument).getStatusCode(),
                "缺 DATA 授权文档必须 403（注解拦截器拒绝）");
    }

    @Test
    void testCapabilitiesUsesRequestedServiceAndIsNeverCached() {
        log.info("验收页面能力、目标服务写范围与缓存约束");
        String token = human(true, true, restricted(SERVICE_A));
        ResponseEntity<JsonNode> allowed = exchange(BASE + "/capabilities?serviceCode=" + SERVICE_A,
                HttpMethod.GET, null, token);
        assertEquals("no-store", allowed.getHeaders().getCacheControl());
        assertEquals(2, allowed.getBody().size());
        assertTrue(allowed.getBody().path("pageAllowed").asBoolean());
        assertTrue(allowed.getBody().path("canWrite").asBoolean());
        assertTrue(exchange(BASE + "/capabilities", HttpMethod.GET, null, token).getBody().path("canWrite").asBoolean());
        assertFalse(exchange(BASE + "/capabilities?serviceCode=" + SERVICE_B, HttpMethod.GET, null, token)
                .getBody().path("canWrite").asBoolean());
        assertEquals(HttpStatus.BAD_REQUEST, exchange(BASE + "/capabilities?serviceCode=bad%20code",
                HttpMethod.GET, null, token).getStatusCode());
    }

    @Test
    void testSnapshotRequiresDataBeforeReturning304() {
        log.info("验收快照授权先于 ETag 与 304");
        String all = human(true, true, full());
        create(all, SERVICE_B);
        String machine = fixture.token(ResourceSubjectType.SERVICE, false,
                Collections.singletonList(API_SNAPSHOT_READ), restricted(SERVICE_B));
        ResponseEntity<JsonNode> snapshot = exchange(BASE + "/snapshot?serviceCode=" + SERVICE_B,
                HttpMethod.GET, null, machine);
        assertEquals(HttpStatus.OK, snapshot.getStatusCode());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(machine);
        headers.setIfNoneMatch(snapshot.getHeaders().getETag());
        assertEquals(HttpStatus.NOT_MODIFIED, http.exchange(BASE + "/snapshot?serviceCode=" + SERVICE_B,
                HttpMethod.GET, new HttpEntity<>(headers), String.class).getStatusCode());
        headers.setBearerAuth(human(true, true, restricted(SERVICE_A)));
        ResponseEntity<JsonNode> forbidden = http.exchange(BASE + "/snapshot?serviceCode=" + SERVICE_B,
                HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());
        assertNull(forbidden.getHeaders().getETag());
    }

    @Test
    void testUnknownDimensionAndMissingDataFailClosedWithoutWrites() {
        log.info("验收未知维度、缺 DATA 和缺计划的失败关闭");
        DataGrant unknown = new DataGrant(DATA_POLICY_RESOURCE, Arrays.asList(DATA_READ, DATA_WRITE), false,
                Collections.singletonList(new DataConstraint("unknownDimension", DataConstraintOperator.IN,
                        Collections.singletonList(SERVICE_A))));
        String token = human(true, true, document(Arrays.asList(grant(SERVICE_A), unknown)));
        assertEquals(HttpStatus.FORBIDDEN, exchange(BASE, HttpMethod.GET, null, token).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, exchange(BASE, HttpMethod.POST, request(SERVICE_A), token).getStatusCode());
        assertFalse(exchange(BASE + "/capabilities", HttpMethod.GET, null, token).getBody().path("canWrite").asBoolean());
        String missing = human(true, true, null);
        assertEquals(HttpStatus.FORBIDDEN, exchange(BASE, HttpMethod.GET, null, missing).getStatusCode());
        assertThrows(SmartRedisLimiterManagementAccessDeniedException.class,
                () -> service.create(request(SERVICE_A), "mock-operator", null));
        assertThrows(SmartRedisLimiterManagementAccessDeniedException.class,
                () -> snapshots.getSnapshot(SERVICE_A, null));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM smart_redis_limiter_policy_revision", Integer.class));
    }

    @Test
    void testGrantUnionCaseSensitivityAndInvalidServiceValues() {
        log.info("验收完整 grant 合并与服务编码精确匹配");
        SmartRedisLimiterPolicyDataScope scope = SmartRedisLimiterPolicyDataScope.from(DataAccessPlan.evaluate(
                document(Arrays.asList(grant(SERVICE_A), grant(SERVICE_B))),
                new DataPermissionRequest(DATA_POLICY_RESOURCE, DATA_READ)));
        assertTrue(scope.allows(SERVICE_A));
        assertTrue(scope.allows(SERVICE_B));
        assertFalse(scope.allows(SERVICE_A.toUpperCase(Locale.ROOT)));
        assertFalse(SmartRedisLimiterPolicyDataScope.from(DataAccessPlan.deny()).allowsAny());
        DataGrant invalid = new DataGrant(DATA_POLICY_RESOURCE, Collections.singletonList(DATA_READ), false,
                Collections.singletonList(new DataConstraint(DATA_SERVICE_DIMENSION, DataConstraintOperator.IN,
                        Collections.singletonList("invalid service"))));
        assertFalse(SmartRedisLimiterPolicyDataScope.from(DataAccessPlan.evaluate(document(Collections.singletonList(invalid)),
                new DataPermissionRequest(DATA_POLICY_RESOURCE, DATA_READ))).allowsAny());
    }

    @Test
    void testHttpMalformedBodyVersionAndWrongMediaTypeAreNot500() {
        log.info("验收 HTTP 参数和内容类型错误的状态码");
        String token = human(true, true, full());
        long id = create(token, SERVICE_A);
        assertEquals(HttpStatus.BAD_REQUEST, exchange(BASE + "/" + id, HttpMethod.PATCH,
                new SmartRedisLimiterPolicyStateRequest(), token).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, exchange(BASE + "/" + id + "?expectedRowVersion=-1",
                HttpMethod.DELETE, null, token).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, exchange(BASE + "?page=bad", HttpMethod.GET, null, token).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, exchange(BASE, HttpMethod.POST, "{", token).getStatusCode());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.TEXT_PLAIN);
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, http.exchange(BASE, HttpMethod.POST,
                new HttpEntity<>("not-json", headers), String.class).getStatusCode());
    }

    private String human(boolean page, boolean write, DataGrantDocument data) {
        List<String> permissions = new ArrayList<>(Arrays.asList(API_POLICY_READ, API_SNAPSHOT_READ));
        if (write) {
            permissions.add(API_POLICY_WRITE);
        }
        return fixture.token(ResourceSubjectType.HUMAN, page, permissions, data);
    }

    private long create(String token, String code) {
        ResponseEntity<JsonNode> response = exchange(BASE, HttpMethod.POST, request(code), token);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getHeaders().getLocation());
        assertNull(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE));
        return response.getBody().path("policy").path("id").asLong();
    }

    private ResponseEntity<JsonNode> exchange(String path, HttpMethod method, Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return http.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private SmartRedisLimiterPolicyCreateRequest request(String code) {
        SmartRedisLimiterPolicyCreateRequest request = new SmartRedisLimiterPolicyCreateRequest();
        request.setKey(new SmartRedisLimiterPolicyKey(code, "mock-resource", "mock-subject"));
        request.setEnabled(true);
        request.setLimits(Collections.singletonList(new SmartRedisLimiterLimit(1L, 1L, SmartRedisLimiterTimeUnit.SECONDS)));
        return request;
    }

    private SmartRedisLimiterPolicyUpdateRequest update(long version, long count) {
        SmartRedisLimiterPolicyUpdateRequest request = new SmartRedisLimiterPolicyUpdateRequest();
        request.setExpectedRowVersion(version);
        request.setLimits(Collections.singletonList(new SmartRedisLimiterLimit(count, 1L, SmartRedisLimiterTimeUnit.SECONDS)));
        return request;
    }

    private SmartRedisLimiterPolicyStateRequest state(long version, boolean enabled) {
        SmartRedisLimiterPolicyStateRequest request = new SmartRedisLimiterPolicyStateRequest();
        request.setExpectedRowVersion(version);
        request.setEnabled(enabled);
        return request;
    }

    /**
     * 受控 Provider 位于测试域，不验证外部签名，不进入制品。
     */
    @TestConfiguration
    public static class PortalFixtureConfiguration {
        @Bean
        public PortalFixture portalFixture() {
            return new PortalFixture();
        }

        @Bean
        public ResourceAuthenticationAdapter mockPersonAdapter(PortalFixture fixture) {
            return fixture.adapter("mock-person");
        }

        @Bean
        public ResourceAuthenticationAdapter mockMachineAdapter(PortalFixture fixture) {
            return fixture.adapter("mock-machine");
        }
    }

    public static class PortalFixture {
        private final Map<String, ResourceAuthenticationResult> results = new ConcurrentHashMap<>();

        ResourceAuthenticationAdapter adapter(String source) {
            ResourceAuthenticationAdapter adapter = mock(ResourceAuthenticationAdapter.class);
            when(adapter.sourceId()).thenReturn(new ResourceAuthenticationSourceId(source));
            when(adapter.authenticate(any())).thenAnswer(invocation -> results.getOrDefault(
                    ((BearerResourceCredential) invocation.getArgument(0)).getToken(),
                    ResourceAuthenticationResult.rejected(ResourceAuthenticationFailureCategory.CREDENTIAL_MALFORMED)));
            return adapter;
        }

        String token(ResourceSubjectType type, boolean page, List<String> apis, DataGrantDocument data) {
            String source = type == ResourceSubjectType.HUMAN ? "mock-person" : "mock-machine";
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    ("{\"kid\":\"" + source + SimpleResourceServerStarterConstant.KID_SOURCE_SEPARATOR
                            + "mock-key\"}").getBytes(StandardCharsets.UTF_8)) + "." + UUID.randomUUID() + ".mock";
            Instant now = Instant.now();
            ApplicationAuthorizationContext authorization = new ApplicationAuthorizationContext(
                    SimpleApplicationAuthorizationConstant.PROTOCOL, SimpleApplicationAuthorizationConstant.VERSION,
                    type == ResourceSubjectType.HUMAN ? ApplicationAuthorizationSubjectType.HUMAN : ApplicationAuthorizationSubjectType.SERVICE,
                    "mock-subject-id", "mock-limiter", true, Collections.emptyList(),
                    page ? Collections.singletonList(PAGE_POLICY) : Collections.emptyList(), apis, data, 1L, "1",
                    String.join("", Collections.nCopies(64, "a")), now, now.plusSeconds(600));
            results.put(token, ResourceAuthenticationResult.authenticated(new VerifiedResourcePrincipal(
                    new ResourceAuthenticationSourceId(source), type, "mock-subject-id"), authorization));
            return token;
        }
    }
}
