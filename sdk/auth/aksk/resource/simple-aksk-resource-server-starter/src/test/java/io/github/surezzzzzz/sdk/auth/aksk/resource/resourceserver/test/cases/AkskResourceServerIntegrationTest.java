package io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.core.constant.AkskConstant;
import io.github.surezzzzzz.sdk.auth.aksk.core.constant.JwtClaimConstant;
import io.github.surezzzzzz.sdk.auth.aksk.resource.core.constant.AkskResourceIntrospectionClaimConstant;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.claim.ApplicationAuthorizationContextClaimMapper;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.SimpleApplicationAuthorizationConstant;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.resource.server.event.ResourceAccessEvent;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AKSK资源服务公共安全链集成测试。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = AkskResourceServerIntegrationTest.TestApplication.class,
        properties = {
                "io.github.surezzzzzz.sdk.auth.resource.server.security.protected-paths[0]=/api/**",
                "io.github.surezzzzzz.sdk.auth.aksk.resource.server.introspect.endpoint=http://aksk.test/introspect",
                "io.github.surezzzzzz.sdk.auth.aksk.resource.server.introspect.local-cache.enabled=false"
        })
@AutoConfigureMockMvc
class AkskResourceServerIntegrationTest {

    private static final String API_PERMISSION = "resource.read";
    private static final String AKSK_SUBJECT = "service-client";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private VerificationRecorder recorder;

    @Autowired
    private EventRecorder eventRecorder;

    private static String bearer(String variant) {
        return bearerForSource(AkskConstant.RESOURCE_AUTHENTICATION_SOURCE_ID, variant);
    }

    private static String bearerForSource(String source, String variant) {
        String header = "{\"alg\":\"dir\",\"enc\":\"A256GCM\",\"kid\":\"" + source + "/" + variant + "\"}";
        return "Bearer " + Base64.getUrlEncoder().withoutPadding().encodeToString(
                header.getBytes(StandardCharsets.UTF_8)) + ".encrypted";
    }

    private static boolean hasTokenVariant(String token, String variant) {
        int headerEnd = token.indexOf('.');
        if (headerEnd <= 0) {
            return false;
        }
        String header = new String(Base64.getUrlDecoder().decode(token.substring(0, headerEnd)), StandardCharsets.UTF_8);
        return ("{\"alg\":\"dir\",\"enc\":\"A256GCM\",\"kid\":\"aksk/" + variant + "\"}").equals(header);
    }

    private static Map<String, Object> claims(boolean denied) {
        Instant now = Instant.now();
        ApplicationAuthorizationContext authorization = new ApplicationAuthorizationContext(
                SimpleApplicationAuthorizationConstant.PROTOCOL,
                SimpleApplicationAuthorizationConstant.VERSION,
                ApplicationAuthorizationSubjectType.SERVICE,
                AKSK_SUBJECT,
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
        Map<String, Object> claims = new HashMap<String, Object>();
        claims.put(AkskResourceIntrospectionClaimConstant.ACTIVE, Boolean.TRUE);
        claims.put(JwtClaimConstant.CLIENT_ID, AKSK_SUBJECT);
        claims.put(JwtClaimConstant.APPLICATION_AUTHORIZATION,
                ApplicationAuthorizationContextClaimMapper.toClaim(authorization));
        return claims;
    }

    @BeforeEach
    void resetRecorders() {
        recorder.reset();
        eventRecorder.reset();
    }

    @Test
    void shouldAuthenticateAkskAgainstThePublicResourceChain() throws Exception {
        mockMvc.perform(get("/api/resource").header("Authorization", bearer("allowed")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("resource"));

        log.info("AKSK公共安全链认证成功，内省调用次数：{}，公共事件数量：{}", recorder.calls.get(),
                eventRecorder.successEvents.get());
        assertEquals(1, recorder.calls.get(), "AKSK凭据必须只调用一次内省器");
        assertEquals(1, eventRecorder.successEvents.get(), "已认证访问必须发布公共安全摘要事件");
    }

    @Test
    void shouldRejectUnknownAndAmbiguousCredentialsBeforeIntrospection() throws Exception {
        mockMvc.perform(get("/api/resource").header("Authorization", bearerForSource("unknown", "allowed")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/resource")
                        .header("Authorization", bearer("allowed"), bearer("allowed")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/resource")
                        .header("Authorization", bearer("allowed"))
                        .header("Cookie", "session=unexpected"))
                .andExpect(status().isUnauthorized());

        log.info("未知或歧义凭据在内省前被拒绝，内省调用次数：{}，公共事件数量：{}", recorder.calls.get(),
                eventRecorder.successEvents.get());
        assertEquals(0, recorder.calls.get(), "未路由或歧义凭据不得进入AKSK内省");
        assertEquals(0, eventRecorder.successEvents.get(), "未认证请求不得发布访问事件");
    }

    @Test
    void shouldRejectInactiveCredentialWithoutFallback() throws Exception {
        mockMvc.perform(get("/api/resource").header("Authorization", bearer("inactive")))
                .andExpect(status().isUnauthorized());

        log.info("失效AKSK凭据被拒绝，内省调用次数：{}，公共事件数量：{}", recorder.calls.get(),
                eventRecorder.successEvents.get());
        assertEquals(1, recorder.calls.get(), "选定AKSK来源后不得回退到其他Provider");
        assertEquals(0, eventRecorder.successEvents.get(), "内省拒绝不得发布访问事件");
    }

    @Test
    void shouldRejectMissingApiPermissionAfterAkskAuthentication() throws Exception {
        mockMvc.perform(get("/api/resource").header("Authorization", bearer("denied")))
                .andExpect(status().isForbidden());

        log.info("AKSK认证完成后的权限拒绝，内省调用次数：{}，公共事件数量：{}", recorder.calls.get(),
                eventRecorder.successEvents.get());
        assertEquals(1, recorder.calls.get(), "已认证AKSK请求必须完成内省");
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

        @Bean(name = "akskOpaqueTokenIntrospector")
        OpaqueTokenIntrospector akskOpaqueTokenIntrospector(VerificationRecorder recorder) {
            return token -> {
                recorder.calls.incrementAndGet();
                Map<String, Object> claims = claims(hasTokenVariant(token, "denied"));
                if (hasTokenVariant(token, "inactive")) {
                    claims.put(AkskResourceIntrospectionClaimConstant.ACTIVE, Boolean.FALSE);
                }
                return new DefaultOAuth2AuthenticatedPrincipal(AKSK_SUBJECT, claims, Collections.emptyList());
            };
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

    static final class VerificationRecorder {
        private final AtomicInteger calls = new AtomicInteger();

        private void reset() {
            calls.set(0);
        }
    }

    static final class EventRecorder implements ApplicationListener<ResourceAccessEvent> {
        private final AtomicInteger successEvents = new AtomicInteger();

        @Override
        public void onApplicationEvent(ResourceAccessEvent event) {
            if (AkskConstant.RESOURCE_AUTHENTICATION_SOURCE_ID.equals(event.getAuthenticationSourceId())) {
                successEvents.incrementAndGet();
            }
        }

        private void reset() {
            successEvents.set(0);
        }
    }
}
