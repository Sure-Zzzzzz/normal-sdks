package io.github.surezzzzzz.sdk.auth.iam.resource.server.test.cases;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.claim.ApplicationAuthorizationContextClaimMapper;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.SimpleApplicationAuthorizationConstant;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.iam.core.constant.SimpleIamCoreConstant;
import io.github.surezzzzzz.sdk.auth.iam.resource.server.support.HttpIamResourceTokenVerificationClient;
import io.github.surezzzzzz.sdk.auth.resource.core.event.ResourceAccessEvent;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IAM资源服务公共安全链集成测试。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = IamResourceServerIntegrationTest.TestApplication.class,
        properties = {
                "io.github.surezzzzzz.sdk.auth.resource.server.security.protected-paths[0]=/api/**",
                "io.github.surezzzzzz.sdk.auth.iam.resource.server.verification-endpoint="
                        + "http://iam.example/iam/resource/tokens/verify",
                "io.github.surezzzzzz.sdk.auth.iam.resource.server.client-id=verifier",
                "io.github.surezzzzzz.sdk.auth.iam.resource.server.client-secret=secret"
        })
@AutoConfigureMockMvc
class IamResourceServerIntegrationTest {

    private static final String API_PERMISSION = "resource.read";
    private static final String IAM_SUBJECT = "user-a";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private VerificationRecorder recorder;

    @Autowired
    private EventRecorder eventRecorder;

    private static String bearer(String variant) {
        String header = "{\"alg\":\"dir\",\"enc\":\"A256GCM\",\"kid\":\"iam/" + variant + "\"}";
        return "Bearer " + Base64.getUrlEncoder().withoutPadding().encodeToString(
                header.getBytes(StandardCharsets.UTF_8)) + ".encrypted";
    }

    private static Map<String, Object> authorizationClaim(boolean denied) {
        Instant now = Instant.now();
        ApplicationAuthorizationContext authorization = new ApplicationAuthorizationContext(
                SimpleApplicationAuthorizationConstant.PROTOCOL,
                SimpleApplicationAuthorizationConstant.VERSION,
                ApplicationAuthorizationSubjectType.HUMAN,
                IAM_SUBJECT,
                "resource-app",
                true,
                Collections.<String>emptyList(),
                Collections.<String>emptyList(),
                denied ? Collections.<String>emptyList() : Collections.singletonList(API_PERMISSION),
                null,
                1L,
                "resource-manifest",
                "resource-digest",
                now.minusSeconds(1L),
                now.plusSeconds(60L));
        return ApplicationAuthorizationContextClaimMapper.toClaim(authorization);
    }

    /**
     * 构造受控验证成功响应体（恰好双字段协议）。
     */
    private static String verificationBody(Map<String, Object> authorizationClaim) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put(SimpleIamCoreConstant.CLAIM_SUBJECT, IAM_SUBJECT);
        body.put(SimpleIamCoreConstant.CLAIM_APPLICATION_AUTHORIZATION, authorizationClaim);
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body);
        } catch (Exception exception) {
            throw new IllegalStateException("构造验证响应体失败", exception);
        }
    }

    @BeforeEach
    void resetRecorders() {
        recorder.reset();
        eventRecorder.reset();
    }

    @Test
    void shouldAuthenticateIamHumanAgainstThePublicResourceChain() throws Exception {
        recorder.enqueueOk(authorizationClaim(false));

        mockMvc.perform(get("/api/resource").header("Authorization", bearer("allowed")))
                .andExpect(status().isOk());

        log.info("IAM公共安全链认证成功，受控验证调用次数：{}，公共事件数量：{}", recorder.calls.get(),
                eventRecorder.successEvents.get());
        assertEquals(1, recorder.calls.get(), "IAM凭据必须只调用一次受控验证端点");
        assertEquals(1, eventRecorder.successEvents.get(), "已认证访问必须发布公共安全摘要事件");
    }

    @Test
    void shouldSendIndependentBasicCredentialsAndTokenOnly() throws Exception {
        recorder.server.expect(requestTo("http://iam.example/iam/resource/tokens/verify"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Basic dmVyaWZpZXI6c2VjcmV0"))
                .andExpect(jsonPath("$.token").exists())
                .andRespond(withStatus(org.springframework.http.HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(verificationBody(authorizationClaim(false))));

        mockMvc.perform(get("/api/resource").header("Authorization", bearer("allowed")))
                .andExpect(status().isOk());

        recorder.server.verify();
        assertEquals(1, recorder.calls.get(), "凭据请求体必须携带完整Bearer令牌");
    }

    @Test
    void shouldRejectInactiveTokenWithoutProviderFallback() throws Exception {
        recorder.server.expect(requestTo("http://iam.example/iam/resource/tokens/verify"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.UNAUTHORIZED));

        mockMvc.perform(get("/api/resource").header("Authorization", bearer("inactive")))
                .andExpect(status().isUnauthorized());

        log.info("IAM拒绝的Token被拒绝，受控验证调用次数：{}，公共事件数量：{}", recorder.calls.get(),
                eventRecorder.successEvents.get());
        assertEquals(1, recorder.calls.get(), "选定IAM来源后不得回退到其他Provider");
        assertEquals(0, eventRecorder.successEvents.get(), "未认证请求不得发布访问事件");
    }

    @Test
    void shouldRejectProtocolViolatingVerificationResponse() throws Exception {
        recorder.server.expect(requestTo("http://iam.example/iam/resource/tokens/verify"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"sub\":\"user-a\",\"iam_authorization\":{},\"roles\":[]}"));

        mockMvc.perform(get("/api/resource").header("Authorization", bearer("extra")))
                .andExpect(status().isUnauthorized());

        assertEquals(1, recorder.calls.get(), "协议检查发生在受控验证之后");
        assertEquals(0, eventRecorder.successEvents.get(), "协议无效不得发布访问事件");
    }

    @Test
    void shouldRejectMissingApiPermissionAfterIamAuthentication() throws Exception {
        recorder.enqueueOk(authorizationClaim(true));

        mockMvc.perform(get("/api/resource").header("Authorization", bearer("denied")))
                .andExpect(status().isForbidden());

        log.info("IAM认证完成后的权限拒绝，受控验证调用次数：{}，公共事件数量：{}", recorder.calls.get(),
                eventRecorder.successEvents.get());
        assertEquals(1, recorder.calls.get(), "已认证IAM请求必须完成受控验证");
        assertEquals(1, eventRecorder.successEvents.get(), "认证完成后权限拒绝仍是已认证访问");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {

        @Bean
        VerificationRecorder verificationRecorder() {
            return new VerificationRecorder();
        }

        @Bean
        EventRecorder eventRecorder() {
            return new EventRecorder();
        }

        @Bean
        ResourceController resourceController() {
            return new ResourceController();
        }

        @Bean
        HttpIamResourceTokenVerificationClient iamResourceTokenVerificationClient(
                VerificationRecorder recorder) {
            return new StubVerificationClient(recorder);
        }
    }

    @RestController
    static class ResourceController {

        @GetMapping("/api/resource")
        @RequireApiPermission(API_PERMISSION)
        public String resource() {
            return "resource";
        }
    }

    static final class StubVerificationClient extends HttpIamResourceTokenVerificationClient {

        StubVerificationClient(VerificationRecorder recorder) {
            super(stubProperties(), recorder.restTemplate);
        }

        private static io.github.surezzzzzz.sdk.auth.iam.resource.server.configuration.SimpleIamResourceServerProperties stubProperties() {
            io.github.surezzzzzz.sdk.auth.iam.resource.server.configuration.SimpleIamResourceServerProperties properties =
                    new io.github.surezzzzzz.sdk.auth.iam.resource.server.configuration.SimpleIamResourceServerProperties();
            properties.setVerificationEndpoint("http://iam.example/iam/resource/tokens/verify");
            properties.setClientId("verifier");
            properties.setClientSecret("secret");
            return properties;
        }
    }

    static final class VerificationRecorder {
        private final AtomicInteger calls = new AtomicInteger();
        private final RestTemplate restTemplate = new RestTemplate();
        private org.springframework.test.web.client.MockRestServiceServer server;

        private VerificationRecorder() {
            // 用 ClientHttpRequestInterceptor 计数真实 HTTP 调用，不经桩方法，保留完整生产链路
            restTemplate.getInterceptors().add((request, body, execution) -> {
                calls.incrementAndGet();
                return execution.execute(request, body);
            });
        }

        private void reset() {
            calls.set(0);
            // 每个用例重建 Mock 绑定：单例 RestTemplate 跨用例复用，旧 server 在请求发生后无法追加期望
            server = org.springframework.test.web.client.MockRestServiceServer.bindTo(restTemplate).build();
        }

        private void enqueueOk(Map<String, Object> authorizationClaim) {
            server.expect(requestTo("http://iam.example/iam/resource/tokens/verify"))
                    .andRespond(withStatus(org.springframework.http.HttpStatus.OK)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(verificationBody(authorizationClaim)));
        }
    }

    static final class EventRecorder {
        private final AtomicInteger successEvents = new AtomicInteger();

        private void reset() {
            successEvents.set(0);
        }

        @EventListener
        public void onResourceAccess(ResourceAccessEvent event) {
            if (SimpleIamCoreConstant.RESOURCE_AUTHENTICATION_SOURCE_ID.equals(event.getAuthenticationSourceId())) {
                successEvents.incrementAndGet();
            }
        }
    }
}
