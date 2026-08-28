package io.github.surezzzzzz.sdk.audit.aksk.test.cases;

import io.github.surezzzzzz.sdk.audit.aksk.resource.model.AkskAuditRecord;
import io.github.surezzzzzz.sdk.audit.aksk.test.AkskAuditListenerTestApplication;
import io.github.surezzzzzz.sdk.audit.aksk.test.TestAkskAuditHandler;
import io.github.surezzzzzz.sdk.audit.aksk.test.TestTraceIdProvider;
import io.github.surezzzzzz.sdk.audit.aksk.test.config.StubAkskIntrospectorConfig;
import io.github.surezzzzzz.sdk.auth.aksk.core.constant.AkskConstant;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.SimpleApplicationAuthorizationConstant;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceSubjectType;
import io.github.surezzzzzz.sdk.auth.resource.core.event.ResourceAccessEvent;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationSourceId;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourcePrincipal;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AKSK资源审计监听器端到端测试：MockMvc 走公共安全链 + AKSK Provider + stub 内省，
 * 验证完整链路下的审计记录生成与来源过滤。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = AkskAuditListenerTestApplication.class,
        properties = {
                "io.github.surezzzzzz.sdk.auth.resource.server.security.protected-paths[0]=/api/**",
                "io.github.surezzzzzz.sdk.auth.aksk.resource.server.introspect.endpoint=http://aksk.test/introspect",
                "io.github.surezzzzzz.sdk.auth.aksk.resource.server.introspect.local-cache.enabled=false"
        })
@AutoConfigureMockMvc
class AkskAuditListenerEndToEndTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private TestAkskAuditHandler testAuditHandler;

    @Autowired
    private TestTraceIdProvider testTraceIdProvider;

    private static String bearer() {
        String header = "{\"alg\":\"dir\",\"enc\":\"A256GCM\",\"kid\":\""
                + AkskConstant.RESOURCE_AUTHENTICATION_SOURCE_ID + "/allowed\"}";
        return "Bearer " + Base64.getUrlEncoder().withoutPadding().encodeToString(
                header.getBytes(StandardCharsets.UTF_8)) + ".encrypted";
    }

    @BeforeEach
    void setUp() {
        testAuditHandler.reset();
        testTraceIdProvider.reset();
    }

    @Test
    void shouldRecordAuditForAuthenticatedAkskAccess() throws Exception {
        testTraceIdProvider.setTraceId("trace-e2e");
        mockMvc.perform(get("/api/resource")
                        .header("Authorization", bearer())
                        .header("User-Agent", "audit-e2e-agent"))
                .andExpect(status().isOk())
                .andExpect(content().string("resource"));

        assertTrue(testAuditHandler.latch.await(5, TimeUnit.SECONDS), "完整链路认证成功后审计处理器必须收到记录");
        assertEquals(1, testAuditHandler.records.size(), "一次已认证访问必须只写入一条审计记录");
        AkskAuditRecord record = testAuditHandler.records.get(0);
        assertEquals(AkskConstant.RESOURCE_AUTHENTICATION_SOURCE_ID, record.getAuthenticationSourceId());
        assertEquals("SERVICE", record.getSubjectType(), "AKSK服务身份的subjectType恒为SERVICE");
        assertEquals(StubAkskIntrospectorConfig.AKSK_SUBJECT, record.getSubjectId(), "subjectId必须是内省声明中的Client ID");
        assertEquals(StubAkskIntrospectorConfig.APPLICATION_CODE, record.getApplicationCode());
        assertNotNull(record.getRequestId(), "公共链生成的请求标识必须保留");
        assertEquals("/api/resource", record.getRequestUri());
        assertEquals("GET", record.getHttpMethod());
        assertEquals("127.0.0.1", record.getRemoteAddr());
        assertEquals("audit-e2e-agent", record.getUserAgent());
        assertNotNull(record.getTimestamp(), "审计记录必须保留公共事件时间");
        assertEquals("trace-e2e", record.getTraceId());
        log.info("完整链路审计断言通过: subjectId={}, requestId={}, timestamp={}",
                record.getSubjectId(), record.getRequestId(), record.getTimestamp());
    }

    @Test
    void shouldNotRecordAuditForUnauthenticatedAccess() throws Exception {
        mockMvc.perform(get("/api/resource"))
                .andExpect(status().isUnauthorized());

        assertFalse(testAuditHandler.latch.await(200, TimeUnit.MILLISECONDS), "未认证请求不得触发审计处理器");
        assertTrue(testAuditHandler.records.isEmpty(), "未认证请求不得写入AKSK审计记录");
    }

    @Test
    void shouldIgnoreNonAkskResourceAccess() throws InterruptedException {
        eventPublisher.publishEvent(event("iam", ResourceSubjectType.HUMAN, "human-a"));

        assertFalse(testAuditHandler.latch.await(200, TimeUnit.MILLISECONDS), "非AKSK事件不得触发审计处理器");
        assertTrue(testAuditHandler.records.isEmpty(), "非AKSK事件不得写入AKSK审计记录");
    }

    private ResourceAccessEvent event(String sourceId, ResourceSubjectType subjectType, String subjectId) {
        Instant now = Instant.now();
        ApplicationAuthorizationSubjectType authorizationSubjectType = subjectType == ResourceSubjectType.SERVICE
                ? ApplicationAuthorizationSubjectType.SERVICE : ApplicationAuthorizationSubjectType.HUMAN;
        ApplicationAuthorizationContext authorization = new ApplicationAuthorizationContext(
                SimpleApplicationAuthorizationConstant.PROTOCOL, SimpleApplicationAuthorizationConstant.VERSION,
                authorizationSubjectType, subjectId, "application-a", true, Collections.<String>emptyList(),
                Collections.<String>emptyList(), Collections.singletonList("api.read"), null, 1L,
                "manifest-a", "digest-a", now.minusSeconds(1L), now.plusSeconds(60L));
        VerifiedResourceContext context = new VerifiedResourceContext(
                new VerifiedResourcePrincipal(new ResourceAuthenticationSourceId(sourceId), subjectType, subjectId),
                authorization, "request-a");
        return new ResourceAccessEvent(context, "/api/resource", "GET", "127.0.0.1", "test-agent");
    }
}
