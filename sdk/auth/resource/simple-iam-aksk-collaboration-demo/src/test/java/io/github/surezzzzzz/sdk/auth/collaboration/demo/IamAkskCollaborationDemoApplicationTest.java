package io.github.surezzzzzz.sdk.auth.collaboration.demo;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.claim.ApplicationAuthorizationContextClaimMapper;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.SimpleApplicationAuthorizationConstant;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.collaboration.demo.repository.OrderRepository;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataConstraintOperator;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.SimpleDataPermissionConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataConstraint;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;
import io.github.surezzzzzz.sdk.auth.iam.resource.server.configuration.SimpleIamResourceServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.resource.server.support.HttpIamResourceTokenVerificationClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IAM 与 AKSK 协作 Demo 集成回归。
 *
 * <p>Mock 身份按 token 变体分发，业务数据写入真实 MySQL 测试库，
 * 断言两种身份进入同一 Controller、Service 与数据边界，且 DATA 计划约束真实结果集。</p>
 *
 * @author surezzzzzz
 */
@SpringBootTest(classes = IamAkskCollaborationDemoApplication.class)
@AutoConfigureMockMvc
@Import(IamAkskCollaborationDemoApplicationTest.TestIdentityConfiguration.class)
class IamAkskCollaborationDemoApplicationTest {

    private static final String ORDER_RESOURCE = "order";
    private static final String READ_ACTION = "read";
    private static final String WRITE_ACTION = "write";
    private static final String TENANT_ID = "tenantId";
    private static final String DEPARTMENT_ID = "departmentId";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @BeforeEach
    void seedOrders() {
        orderRepository.deleteAll();
        saveOrder("t1", "d1", "no-t1-d1");
        saveOrder("t1", "d2", "no-t1-d2");
        saveOrder("t2", "d1", "no-t2-d1");
        saveOrder("t2", "d2", "no-t2-d2");
    }

    @Test
    void shouldReturnOnlyGrantedRowsForIamHumanIdentity() throws Exception {
        getJson("/api/orders", iamToken("iam/full"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].orderNo").value("no-t1-d1"));
    }

    @Test
    void shouldReturnUnionAcrossGrantsForAkskServiceIdentity() throws Exception {
        getJson("/api/orders", akskToken("aksk/or"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.orderNo == 'no-t1-d1')]").exists())
                .andExpect(jsonPath("$[?(@.orderNo == 'no-t2-d1')]").exists())
                .andExpect(jsonPath("$[?(@.orderNo == 'no-t2-d2')]").exists());
    }

    @Test
    void shouldUseSameBusinessChainForBothIdentities() throws Exception {
        getJson("/api/orders", iamToken("iam/full"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderNo").value("no-t1-d1"));
        getJson("/api/orders", akskToken("aksk/full"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderNo").value("no-t1-d1"));
    }

    @Test
    void shouldNotReadDetailOutsideGrantedScope() throws Exception {
        Long outsideId = idOfOrder("no-t2-d1");
        getJson("/api/orders/" + outsideId, iamToken("iam/full"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectCreateOutsideGrantedScope() throws Exception {
        String body = "{\"tenantId\":\"t2\",\"departmentId\":\"d1\",\"orderNo\":\"no-out\",\"amount\":10.00}";
        mockMvc.perform(post("/api/orders").header("Authorization", "Bearer " + iamToken("iam/full"))
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldDeleteInsideGrantedScopeButNotOutside() throws Exception {
        Long insideId = idOfOrder("no-t1-d1");
        mockMvc.perform(delete("/api/orders/" + insideId).header("Authorization", "Bearer " + iamToken("iam/full")))
                .andExpect(status().isOk());
        Long outsideId = idOfOrder("no-t2-d2");
        mockMvc.perform(delete("/api/orders/" + outsideId).header("Authorization", "Bearer " + iamToken("iam/full")))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldFailClosedWhenApiPermissionExistsButDataGrantMissing() throws Exception {
        getJson("/api/orders", iamToken("iam/nodata"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectWhenApiPermissionMissing() throws Exception {
        getJson("/api/orders", iamToken("iam/noapi"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectMissingUnknownOrMalformedCredentials() throws Exception {
        getJson("/api/orders", null)
                .andExpect(status().isUnauthorized());
        getJson("/api/orders", compactToken("unknown/k1", "variant-orphan"))
                .andExpect(status().isUnauthorized());
        getJson("/api/orders", "not-a-compact-token")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowPublicHealthWithoutCredential() throws Exception {
        getJson("/public/health", null)
                .andExpect(status().isOk());
    }

    private ResultActions getJson(String path, String bearerToken) throws Exception {
        if (bearerToken == null) {
            return mockMvc.perform(get(path));
        }
        return mockMvc.perform(get(path).header("Authorization", "Bearer " + bearerToken));
    }

    private Long idOfOrder(String orderNo) {
        return orderRepository.findAll().stream()
                .filter(order -> orderNo.equals(order.getOrderNo()))
                .findFirst()
                .map(order -> order.getId())
                .orElseThrow(() -> new IllegalStateException("测试种子缺少订单 " + orderNo));
    }

    private void saveOrder(String tenantId, String departmentId, String orderNo) {
        io.github.surezzzzzz.sdk.auth.collaboration.demo.entity.OrderEntity entity =
                new io.github.surezzzzzz.sdk.auth.collaboration.demo.entity.OrderEntity();
        entity.setTenantId(tenantId);
        entity.setDepartmentId(departmentId);
        entity.setOrderNo(orderNo);
        entity.setAmount(new BigDecimal("10.00"));
        entity.setStatus("CREATED");
        entity.setCreatedAt(LocalDateTime.now());
        orderRepository.save(entity);
    }

    private static String iamToken(String variant) {
        return compactToken("iam/k-demo", variant);
    }

    private static String akskToken(String variant) {
        return compactToken("aksk/k-demo", variant);
    }

    private static String compactToken(String kid, String variant) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString(("{\"kid\":\"" + kid + "\",\"alg\":\"none\"}")
                .getBytes(StandardCharsets.UTF_8));
        String payload = encoder.encodeToString(("{\"variant\":\"" + variant + "\"}")
                .getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".";
    }

    private static String variantOf(String token) {
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            return token;
        }
        return new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
    }

    private static boolean isVariant(String token, String variant) {
        return variantOf(token).contains(variant);
    }

    private static Map<String, Object> iamClaims(String variant) {
        String subjectId = "user-demo";
        DataGrantDocument document;
        List<String> apiPermissions;
        if (isVariant(variant, "nodata")) {
            document = null;
            apiPermissions = permissions("order.read", "order.write");
        } else if (isVariant(variant, "noapi")) {
            document = document(grant(TENANT_ID, "t1", DEPARTMENT_ID, "d1"));
            apiPermissions = Collections.emptyList();
        } else {
            document = document(grant(TENANT_ID, "t1", DEPARTMENT_ID, "d1"));
            apiPermissions = permissions("order.read", "order.write");
        }
        ApplicationAuthorizationContext context = new ApplicationAuthorizationContext(
                SimpleApplicationAuthorizationConstant.PROTOCOL, SimpleApplicationAuthorizationConstant.VERSION,
                ApplicationAuthorizationSubjectType.HUMAN, subjectId, "collab-demo", true,
                Collections.emptyList(), Collections.emptyList(), apiPermissions, document,
                1L, "v1", "digest", Instant.now(), Instant.now().plusSeconds(600));
        Map<String, Object> claims = new HashMap<String, Object>();
        claims.put("sub", subjectId);
        claims.put("iam_authorization", ApplicationAuthorizationContextClaimMapper.toClaim(context));
        return claims;
    }

    private static OAuth2AuthenticatedPrincipal akskPrincipal(String variant) {
        String clientId = "svc-demo";
        DataGrantDocument document;
        if (isVariant(variant, "/or")) {
            document = document(
                    grant(TENANT_ID, "t1", DEPARTMENT_ID, "d1"),
                    grant(TENANT_ID, "t2"));
        } else {
            document = document(grant(TENANT_ID, "t1", DEPARTMENT_ID, "d1"));
        }
        ApplicationAuthorizationContext context = new ApplicationAuthorizationContext(
                SimpleApplicationAuthorizationConstant.PROTOCOL, SimpleApplicationAuthorizationConstant.VERSION,
                ApplicationAuthorizationSubjectType.SERVICE, clientId, "collab-demo", true,
                Collections.emptyList(), Collections.emptyList(), permissions("order.read", "order.write"), document,
                1L, "v1", "digest", Instant.now(), Instant.now().plusSeconds(600));
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put("active", Boolean.TRUE);
        attributes.put("client_id", clientId);
        attributes.put("aksk_authorization", ApplicationAuthorizationContextClaimMapper.toClaim(context));
        return new DefaultOAuth2AuthenticatedPrincipal(clientId, attributes, Collections.emptyList());
    }

    private static List<String> permissions(String... names) {
        List<String> permissions = new ArrayList<String>();
        Collections.addAll(permissions, names);
        return permissions;
    }

    private static DataGrantDocument document(DataGrant... grants) {
        List<DataGrant> grantList = new ArrayList<DataGrant>();
        Collections.addAll(grantList, grants);
        return new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL,
                SimpleDataPermissionConstant.VERSION, grantList);
    }

    private static DataGrant grant(String dimensionA, String valueA, String dimensionB, String valueB) {
        List<DataConstraint> constraints = new ArrayList<DataConstraint>();
        constraints.add(new DataConstraint(dimensionA, DataConstraintOperator.IN, Collections.singletonList(valueA)));
        constraints.add(new DataConstraint(dimensionB, DataConstraintOperator.IN, Collections.singletonList(valueB)));
        return new DataGrant(ORDER_RESOURCE, permissions(READ_ACTION, WRITE_ACTION), false, constraints);
    }

    private static DataGrant grant(String dimension, String value) {
        return new DataGrant(ORDER_RESOURCE, permissions(READ_ACTION, WRITE_ACTION), false,
                Collections.singletonList(
                        new DataConstraint(dimension, DataConstraintOperator.IN, Collections.singletonList(value))));
    }

    /**
     * Mock 身份配置：按 token 变体分发 IAM claims 与 AKSK 内省结果。
     */
    @TestConfiguration
    static class TestIdentityConfiguration {

        @Bean
        HttpIamResourceTokenVerificationClient iamResourceTokenVerificationClient() {
            SimpleIamResourceServerProperties properties = new SimpleIamResourceServerProperties();
            properties.setVerificationEndpoint("http://iam.example/iam/resource/tokens/verify");
            properties.setClientId("verifier");
            properties.setClientSecret("secret");
            return new HttpIamResourceTokenVerificationClient(properties) {
                @Override
                public Map<String, Object> verify(String token) {
                    return iamClaims(token);
                }
            };
        }

        @Bean(name = "akskOpaqueTokenIntrospector")
        OpaqueTokenIntrospector akskOpaqueTokenIntrospector() {
            return token -> akskPrincipal(token);
        }
    }
}
