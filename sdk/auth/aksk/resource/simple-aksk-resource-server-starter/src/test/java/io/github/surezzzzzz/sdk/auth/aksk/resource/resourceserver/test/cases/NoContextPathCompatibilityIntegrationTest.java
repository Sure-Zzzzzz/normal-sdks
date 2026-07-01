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
 * No context path compatibility integration test
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = SimpleAkskResourceServerTestApplication.class,
        properties = {
                "io.github.surezzzzzz.sdk.auth.aksk.resource.server.enabled=true",
                "io.github.surezzzzzz.sdk.auth.aksk.resource.server.security.context-path-aware=true",
                "io.github.surezzzzzz.sdk.auth.aksk.resource.server.security.protected-paths[0]=/api/**",
                "io.github.surezzzzzz.sdk.auth.aksk.resource.server.security.permit-all-paths="
        }
)
@AutoConfigureMockMvc
class NoContextPathCompatibilityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testNoContextPathShouldKeepOldBehavior() throws Exception {
        assertStatusAndBody("/context-path/basic", 200, "context-path-basic");
    }

    @Test
    void testNoContextPathShouldStillProtectApiPath() throws Exception {
        assertStatusAndBody("/api/legacy-protected", 401, null);
    }

    private void assertStatusAndBody(String requestUri, int expectedStatus, String expectedMessage) throws Exception {
        log.info("请求 URI: {}, contextPath: {}, protectedPaths: {}, permitAllPaths: {}",
                requestUri, "", "/api/**", "[]");
        MvcResult result = mockMvc.perform(get(requestUri)).andReturn();
        int actualStatus = result.getResponse().getStatus();
        String responseBody = result.getResponse().getContentAsString();
        log.info("响应状态: {}, 响应体: {}", actualStatus, responseBody);
        assertEquals(expectedStatus, actualStatus, "响应状态不符合预期");
        if (expectedMessage != null) {
            org.assertj.core.api.Assertions.assertThat(responseBody).contains(expectedMessage);
        }
    }
}
