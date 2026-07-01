package io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.test.SimpleAkskResourceServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Context path permit all integration test
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = SimpleAkskResourceServerTestApplication.class,
        properties = {
                "server.servlet.context-path=/api",
                "io.github.surezzzzzz.sdk.auth.aksk.resource.server.enabled=true",
                "io.github.surezzzzzz.sdk.auth.aksk.resource.server.security.context-path-aware=true",
                "io.github.surezzzzzz.sdk.auth.aksk.resource.server.security.protected-paths[0]=/api/**",
                "io.github.surezzzzzz.sdk.auth.aksk.resource.server.security.permit-all-paths[0]=/api/public/**",
                "io.github.surezzzzzz.sdk.auth.aksk.resource.server.security.permit-all-paths[1]=/api/actuator/health"
        }
)
@AutoConfigureMockMvc
class ContextPathPermitAllIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testPermitAllShouldPassWhenContextPathApi() throws Exception {
        assertStatusAndBody("/api/public/ping", "/api", 200, "public-pong");
    }

    @Test
    void testPrivateShouldReturn401WhenContextPathApi() throws Exception {
        assertStatusAndBody("/api/private/ping", "/api", 401, null);
    }

    @Test
    void testFakeHealthShouldPassAsNormalPath() throws Exception {
        assertStatusAndBody("/api/actuator/health", "/api", 200, "up");
    }

    @Test
    void testQueryStringShouldNotAffectPermitMatch() throws Exception {
        assertStatusAndBody("/api/public/ping?x=1", "/api", 200, "public-pong");
    }

    private void assertStatusAndBody(String requestUri, String contextPath, int expectedStatus,
                                     String expectedMessage) throws Exception {
        log.info("请求 URI: {}, contextPath: {}, protectedPaths: {}, permitAllPaths: {}",
                requestUri, contextPath, "/api/**", "[/api/public/**, /api/actuator/health]");
        MvcResult result = mockMvc.perform(get(requestUri).contextPath(contextPath)).andReturn();
        int actualStatus = result.getResponse().getStatus();
        String responseBody = result.getResponse().getContentAsString();
        log.info("响应状态: {}, 响应体: {}", actualStatus, responseBody);
        assertEquals(expectedStatus, actualStatus, "响应状态不符合预期");
        if (expectedMessage != null) {
            org.assertj.core.api.Assertions.assertThat(responseBody).contains(expectedMessage);
        }
    }
}
