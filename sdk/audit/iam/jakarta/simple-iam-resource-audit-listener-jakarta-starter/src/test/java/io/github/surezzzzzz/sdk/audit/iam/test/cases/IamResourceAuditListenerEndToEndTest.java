package io.github.surezzzzzz.sdk.audit.iam.test.cases;

import io.github.surezzzzzz.sdk.audit.iam.resource.model.IamResourceAuditRecord;
import io.github.surezzzzzz.sdk.audit.iam.test.IamResourceAuditListenerTestApplication;
import io.github.surezzzzzz.sdk.audit.iam.test.TestIamResourceAuditHandler;
import io.github.surezzzzzz.sdk.audit.iam.test.TestTraceIdProvider;
import io.github.surezzzzzz.sdk.auth.iam.core.constant.SimpleIamCoreConstant;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IAM 资源审计监听器端到端测试（jakarta 线）：MockMvc 走公共安全链 + IAM Provider + stub 受控验证，
 * 验证完整链路下的审计记录生成与来源过滤。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = IamResourceAuditListenerTestApplication.class,
        properties = {
                "io.github.surezzzzzz.sdk.auth.resource.server.security.protected-paths[0]=/api/**",
                "io.github.surezzzzzz.sdk.auth.iam.resource.server.verification-endpoint="
                        + "http://iam.example/iam/resource/tokens/verify",
                "io.github.surezzzzzz.sdk.auth.iam.resource.server.client-id=verifier",
                "io.github.surezzzzzz.sdk.auth.iam.resource.server.client-secret=secret"
        })
@AutoConfigureMockMvc
class IamResourceAuditListenerEndToEndTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private TestIamResourceAuditHandler testAuditHandler;

    @Autowired
    private TestTraceIdProvider testTraceIdProvider;

    private static String bearer() {
        String header = "{\"alg\":\"dir\",\"enc\":\"A256GCM\",\"kid\":\""
                + SimpleIamCoreConstant.RESOURCE_AUTHENTICATION_SOURCE_ID + "/allowed\"}";
        return "Bearer " + Base64.getUrlEncoder().withoutPadding().encodeToString(
                header.getBytes(StandardCharsets.UTF_8)) + ".encrypted";
    }

    @BeforeEach
    void setUp() {
        testAuditHandler.reset();
        testTraceIdProvider.reset();
    }

    @Test
    void shouldRecordAuditForAuthenticatedIamAccess() throws Exception {
        testTraceIdProvider.setTraceId("trace-jakarta-e2e");
        mockMvc.perform(get("/api/resource")
                        .header("Authorization", bearer())
                        .header("User-Agent", "audit-jakarta-e2e-agent"))
                .andExpect(status().isOk())
                .andExpect(content().string("resource"));

        assertTrue(testAuditHandler.latch.await(5, TimeUnit.SECONDS), "完整链路认证成功后审计处理器必须收到记录");
        assertEquals(1, testAuditHandler.records.size(), "一次已认证访问必须只写入一条审计记录");
        IamResourceAuditRecord record = testAuditHandler.records.get(0);
        assertEquals(SimpleIamCoreConstant.RESOURCE_AUTHENTICATION_SOURCE_ID, record.getAuthenticationSourceId());
        assertEquals("HUMAN", record.getSubjectType(), "IAM人员身份的subjectType恒为HUMAN");
        assertEquals("user-a", record.getSubjectId(), "subjectId必须是受控验证声明中的用户ID");
        assertEquals("resource-app", record.getApplicationCode());
        assertNotNull(record.getRequestId(), "公共链生成的请求标识必须保留");
        assertEquals("/api/resource", record.getRequestUri());
        assertEquals("GET", record.getHttpMethod());
        assertEquals("127.0.0.1", record.getRemoteAddr());
        assertEquals("audit-jakarta-e2e-agent", record.getUserAgent());
        assertEquals("trace-jakarta-e2e", record.getTraceId(), "追踪标识必须透传到审计记录");
        log.info("jakarta 线端到端审计记录验证通过：subjectId={}, uri={}, traceId={}",
                record.getSubjectId(), record.getRequestUri(), record.getTraceId());
    }

    @Test
    void shouldIgnoreEventFromOtherAuthenticationSource() {
        eventPublisher.publishEvent(new io.github.surezzzzzz.sdk.auth.resource.core.event.ResourceAccessEvent(
                verifiedContextWithSource("aksk"), "/api/resource", "GET", "127.0.0.1", "other-agent"));

        assertEquals(0, testAuditHandler.records.size(), "非 IAM 来源的事件必须被监听器忽略");
        log.info("非 IAM 来源事件忽略验证通过：records={}", testAuditHandler.records.size());
    }

    private io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext verifiedContextWithSource(
            String sourceId) {
        io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourcePrincipal principal =
                new io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourcePrincipal(
                        new io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationSourceId(sourceId),
                        io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceSubjectType.HUMAN,
                        "subject-other");
        io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext authorization =
                new io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext(
                        io.github.surezzzzzz.sdk.auth.authorization.application.core.constant
                                .SimpleApplicationAuthorizationConstant.PROTOCOL,
                        io.github.surezzzzzz.sdk.auth.authorization.application.core.constant
                                .SimpleApplicationAuthorizationConstant.VERSION,
                        io.github.surezzzzzz.sdk.auth.authorization.application.core.constant
                                .ApplicationAuthorizationSubjectType.HUMAN,
                        "subject-other",
                        "resource-app",
                        true,
                        java.util.Collections.<String>emptyList(),
                        java.util.Collections.<String>emptyList(),
                        java.util.Collections.<String>emptyList(),
                        null,
                        1L,
                        "audit-manifest",
                        "audit-digest",
                        java.time.Instant.now().minusSeconds(1L),
                        java.time.Instant.now().plusSeconds(60L));
        return new io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext(
                principal, authorization, "request-other");
    }
}
