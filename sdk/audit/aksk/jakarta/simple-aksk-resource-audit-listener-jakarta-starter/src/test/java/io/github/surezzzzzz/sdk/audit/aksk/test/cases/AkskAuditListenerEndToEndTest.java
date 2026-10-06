package io.github.surezzzzzz.sdk.audit.aksk.test.cases;

import io.github.surezzzzzz.sdk.audit.aksk.resource.listener.AkskAuditEventListener;
import io.github.surezzzzzz.sdk.audit.aksk.resource.model.AkskAuditRecord;
import io.github.surezzzzzz.sdk.audit.aksk.test.AkskAuditListenerTestApplication;
import io.github.surezzzzzz.sdk.audit.aksk.test.support.AkskAuditTestSupport;
import io.github.surezzzzzz.sdk.audit.aksk.test.support.AkskAuditTestSupport.RecordingHandler;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc 走正式公共安全链与 AKSK Provider，验证认证到官方审计 Handler 的消费闭环。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = AkskAuditListenerTestApplication.class)
@AutoConfigureMockMvc
class AkskAuditListenerEndToEndTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ApplicationContext context;
    @Autowired
    private RecordingHandler handler;
    @Autowired
    private ThreadPoolTaskExecutor taskExecutor;

    @BeforeEach
    void reset() throws Exception {
        AkskAuditTestSupport.awaitDispatch(taskExecutor);
        handler.records.clear();
    }

    @Test
    void shouldAutomaticallyDiscoverListenerAndConsumeAuthenticatedAccess() throws Exception {
        assertThat(context.getBeansOfType(AkskAuditEventListener.class)).hasSize(1);
        mockMvc.perform(get("/api/resource").header("Authorization", bearer())
                        .header("User-Agent", "audit-http-agent"))
                .andExpect(status().isOk()).andExpect(content().string("resource"));
        AkskAuditTestSupport.awaitDispatch(taskExecutor);
        assertThat(handler.records).hasSize(1);
        AkskAuditRecord record = handler.records.remove();
        assertThat(record.getAuthenticationSourceId()).isEqualTo("aksk");
        assertThat(record.getSubjectType()).isEqualTo("SERVICE");
        assertThat(record.getSubjectId()).isEqualTo("service-client");
        assertThat(record.getApplicationCode()).isEqualTo("resource-app");
        assertThat(record.getRequestId()).isNotBlank();
        assertThat(record.getRequestUri()).isEqualTo("/api/resource");
        assertThat(record.getHttpMethod()).isEqualTo("GET");
        assertThat(record.getUserAgent()).isEqualTo("audit-http-agent");
        assertThat(record.getTimestamp()).isPositive();
        assertThat(record.getTraceId()).isNull();
    }

    @Test
    void shouldNotAuditUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/resource")).andExpect(status().isUnauthorized());
        AkskAuditTestSupport.awaitDispatch(taskExecutor);
        assertThat(handler.records).isEmpty();
    }

    @Test
    void shouldNotAuditUnknownAuthenticationSource() throws Exception {
        mockMvc.perform(get("/api/resource").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        AkskAuditTestSupport.awaitDispatch(taskExecutor);
        assertThat(handler.records).isEmpty();
    }

    private String bearer() {
        String header = "{\"alg\":\"dir\",\"enc\":\"A256GCM\",\"kid\":\"aksk/allowed\"}";
        return "Bearer " + Base64.getUrlEncoder().withoutPadding().encodeToString(
                header.getBytes(StandardCharsets.UTF_8)) + ".encrypted";
    }
}
