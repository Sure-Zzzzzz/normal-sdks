package io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.test.cases;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.SimpleApplicationAuthorizationConstant;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataConstraintOperator;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.SimpleDataPermissionConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.*;
import io.github.surezzzzzz.sdk.auth.data.permission.core.support.DataPermissionEvaluator;
import io.github.surezzzzzz.sdk.auth.data.permission.core.support.DefaultDataPermissionEvaluator;
import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.annotation.CurrentDataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.support.DataAccessPlanRestrictionVerifier;
import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.test.SimpleDataPermissionSpringMvcTestApplication;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceSubjectType;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationResult;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationSourceId;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceCredential;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourcePrincipal;
import io.github.surezzzzzz.sdk.auth.resource.core.spi.ResourceAuthenticationAdapter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spring MVC数据权限自动配置测试。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleDataPermissionSpringMvcTestApplication.class)
@Import(DataPermissionSpringMvcAutoConfigurationTest.TestConfiguration.class)
@AutoConfigureMockMvc
class DataPermissionSpringMvcAutoConfigurationTest {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CountingDataPermissionEvaluator evaluator;

    private static ResourceAuthenticationAdapter adapter(final String source,
                                                         final ResourceSubjectType resourceSubjectType,
                                                         final ApplicationAuthorizationSubjectType authorizationSubjectType,
                                                         final java.util.List<String> apiPermissions,
                                                         final DataGrantDocument document) {
        return new ResourceAuthenticationAdapter() {
            private final ResourceAuthenticationSourceId sourceId = new ResourceAuthenticationSourceId(source);

            @Override
            public ResourceAuthenticationSourceId sourceId() {
                return sourceId;
            }

            @Override
            public ResourceAuthenticationResult authenticate(ResourceCredential credential) {
                Instant now = Instant.now();
                ApplicationAuthorizationContext authorization = new ApplicationAuthorizationContext(
                        SimpleApplicationAuthorizationConstant.PROTOCOL, SimpleApplicationAuthorizationConstant.VERSION,
                        authorizationSubjectType, "subject-a", "app-a", true, Collections.<String>emptyList(),
                        Collections.<String>emptyList(), apiPermissions, document, 1L, "manifest-a", "digest-a",
                        now.minusSeconds(1L), now.plusSeconds(60L));
                return ResourceAuthenticationResult.authenticated(new VerifiedResourcePrincipal(sourceId,
                        resourceSubjectType, "subject-a"), authorization);
            }
        };
    }

    private static DataGrantDocument document() {
        return new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL, SimpleDataPermissionConstant.VERSION,
                Collections.singletonList(new DataGrant("order", Collections.singletonList("read"), false,
                        Collections.singletonList(new DataConstraint("tenantId", DataConstraintOperator.IN,
                                Collections.singletonList("tenant-a"))))));
    }

    /**
     * 每个用例前清除DATA评估计数。
     */
    @BeforeEach
    void resetEvaluator() {
        evaluator.reset();
    }

    /**
     * 验证DATA计划在API准入后注入Controller，并只允许授权范围内的请求目标。
     *
     * @throws Exception MockMvc调用异常
     */
    @Test
    void shouldInjectPlanAndRejectOutOfRangeQuery() throws Exception {
        mockMvc.perform(get("/api/orders").queryParam("tenantId", "tenant-a")
                        .header(AUTHORIZATION_HEADER, bearer("iam")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("tenant-a"));

        mockMvc.perform(get("/api/orders").queryParam("tenantId", "tenant-b")
                        .header(AUTHORIZATION_HEADER, bearer("iam")))
                .andExpect(status().isForbidden());

        assertEquals(Integer.valueOf(2), Integer.valueOf(evaluator.count()),
                "每个通过API准入的DATA操作必须评估一次");
        log.info("请求范围只能收紧已验证DATA计划，越权tenantId被拒绝");
    }

    /**
     * 验证AKSK服务主体可通过同一已验证上下文完成DATA操作。
     *
     * @throws Exception MockMvc调用异常
     */
    @Test
    void shouldInjectPlanForVerifiedServiceSubject() throws Exception {
        mockMvc.perform(get("/api/orders").queryParam("tenantId", "tenant-a")
                        .header(AUTHORIZATION_HEADER, bearer("aksk")))
                .andExpect(status().isOk())
                .andExpect(content().string("tenant-a"));

        log.info("AKSK服务主体已通过统一已验证上下文完成DATA计划注入");
        assertEquals(Integer.valueOf(1), Integer.valueOf(evaluator.count()),
                "已通过API准入的AKSK DATA操作必须评估一次");
    }

    /**
     * 验证未声明DATA操作的接口不会被Starter隐式收紧。
     *
     * @throws Exception MockMvc调用异常
     */
    @Test
    void shouldNotEvaluateDataForControllerMethodWithoutOperation() throws Exception {
        mockMvc.perform(get("/api/health").header(AUTHORIZATION_HEADER, bearer("without-data")))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));

        assertEquals(Integer.valueOf(0), Integer.valueOf(evaluator.count()),
                "未声明DATA操作的接口不得触发DATA评估");
    }

    /**
     * 验证缺少DATA文档失败关闭，且API拒绝不会触发DATA评估。
     *
     * @throws Exception MockMvc调用异常
     */
    @Test
    void shouldFailClosedAndRunAfterApiPermission() throws Exception {
        mockMvc.perform(get("/api/orders").queryParam("tenantId", "tenant-a")
                        .header(AUTHORIZATION_HEADER, bearer("without-data")))
                .andExpect(status().isForbidden());
        assertEquals(Integer.valueOf(0), Integer.valueOf(evaluator.count()),
                "缺少DATA文档必须由Core默认路径直接拒绝，不调用具体评估器");

        evaluator.reset();
        mockMvc.perform(get("/api/orders").queryParam("tenantId", "tenant-a")
                        .header(AUTHORIZATION_HEADER, bearer("without-api")))
                .andExpect(status().isForbidden());
        assertEquals(Integer.valueOf(0), Integer.valueOf(evaluator.count()),
                "API权限拒绝时不得评估DATA计划");
        log.info("DATA缺文档失败关闭，且严格位于API权限之后");
    }

    private String bearer(String sourceId) {
        String header = "{\"alg\":\"dir\",\"enc\":\"A256GCM\",\"kid\":\"" + sourceId + "/key-a\"}";
        String encodedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(header.getBytes(StandardCharsets.UTF_8));
        return BEARER_PREFIX + encodedHeader + ".encrypted-key.iv.cipher-text.authentication-tag";
    }

    /**
     * 测试应用。
     */
    @org.springframework.context.annotation.Configuration
    static class TestConfiguration {

        /**
         * 注册无凭据的测试用户服务，避免测试报告输出随机密码。
         *
         * @return 测试用户服务
         */
        @Bean
        UserDetailsService testUserDetailsService() {
            return username -> {
                throw new UsernameNotFoundException("测试不提供表单用户");
            };
        }

        /**
         * 注册携带受限DATA文档的IAM测试认证适配器。
         *
         * @return 认证适配器
         */
        @Bean
        ResourceAuthenticationAdapter iamResourceAuthenticationAdapter() {
            return adapter("iam", ResourceSubjectType.HUMAN, ApplicationAuthorizationSubjectType.HUMAN,
                    Collections.singletonList("order:read"), document());
        }

        /**
         * 注册携带受限DATA文档的AKSK测试认证适配器。
         *
         * @return 认证适配器
         */
        @Bean
        ResourceAuthenticationAdapter akskResourceAuthenticationAdapter() {
            return adapter("aksk", ResourceSubjectType.SERVICE, ApplicationAuthorizationSubjectType.SERVICE,
                    Collections.singletonList("order:read"), document());
        }

        /**
         * 注册缺少DATA文档的测试认证适配器。
         *
         * @return 认证适配器
         */
        @Bean
        ResourceAuthenticationAdapter withoutDataResourceAuthenticationAdapter() {
            return adapter("without-data", ResourceSubjectType.HUMAN, ApplicationAuthorizationSubjectType.HUMAN,
                    Collections.singletonList("order:read"), null);
        }

        /**
         * 注册缺少API权限的测试认证适配器。
         *
         * @return 认证适配器
         */
        @Bean
        ResourceAuthenticationAdapter withoutApiResourceAuthenticationAdapter() {
            return adapter("without-api", ResourceSubjectType.HUMAN, ApplicationAuthorizationSubjectType.HUMAN,
                    Collections.<String>emptyList(), document());
        }

        /**
         * 注册用于验证API与DATA顺序的计数评估器。
         *
         * @return 数据权限评估器
         */
        @Bean
        CountingDataPermissionEvaluator dataPermissionEvaluator() {
            return new CountingDataPermissionEvaluator();
        }

        /**
         * 注册测试Controller。
         *
         * @return 测试Controller
         */
        @Bean
        TestController testController() {
            return new TestController();
        }
    }

    /**
     * 测试Controller。
     */
    @RestController
    static class TestController {

        /**
         * 返回健康状态。
         *
         * @return 健康状态
         */
        @GetMapping("/api/health")
        @RequireApiPermission("order:read")
        public String health() {
            return "ok";
        }

        /**
         * 查询指定租户订单。
         *
         * @param plan     当前数据访问计划
         * @param tenantId 请求租户标识
         * @return 租户标识
         */
        @GetMapping("/api/orders")
        @RequireApiPermission("order:read")
        @DataPermissionOperation(resource = "order", action = "read")
        public String orders(@CurrentDataAccessPlan DataAccessPlan plan, @RequestParam String tenantId) {
            DataAccessPlanRestrictionVerifier.requireTargetAllowed(plan,
                    Collections.singletonMap("tenantId", tenantId));
            return tenantId;
        }
    }

    /**
     * 记录DATA评估次数的测试评估器。
     */
    static final class CountingDataPermissionEvaluator implements DataPermissionEvaluator {

        private final AtomicInteger count = new AtomicInteger();
        private final DefaultDataPermissionEvaluator delegate = new DefaultDataPermissionEvaluator();

        /**
         * 评估授权文档。
         *
         * @param document 已验证授权文档
         * @param request  资源动作请求
         * @return 数据访问计划
         */
        @Override
        public DataAccessPlan evaluate(DataGrantDocument document, DataPermissionRequest request) {
            count.incrementAndGet();
            return delegate.evaluate(document, request);
        }

        /**
         * 返回评估次数。
         *
         * @return 评估次数
         */
        int count() {
            return count.get();
        }

        /**
         * 重置评估次数。
         */
        void reset() {
            count.set(0);
        }
    }
}
