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
 * Context path protected only integration test
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
                "io.github.surezzzzzz.sdk.auth.aksk.resource.server.security.permit-all-paths="
        }
)
@AutoConfigureMockMvc
class ContextPathProtectedOnlyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testNoTokenShouldReturn401WhenContextPathApi() throws Exception {
        assertStatus("/api/context-path/basic", "/api", 401);
    }

    @Test
    void testRootPathShouldReturn401WhenContextPathApiPattern() throws Exception {
        assertStatus("/api", "/api", 401);
    }

    @Test
    void testRootSlashShouldReturn401WhenContextPathApiPattern() throws Exception {
        assertStatus("/api/", "/api", 401);
    }

    @Test
    void testQueryStringShouldNotAffectProtectedMatch() throws Exception {
        assertStatus("/api/context-path/basic?x=1", "/api", 401);
    }

    private void assertStatus(String requestUri, String contextPath, int expectedStatus) throws Exception {
        log.info("请求 URI: {}, contextPath: {}, protectedPaths: {}, permitAllPaths: {}",
                requestUri, contextPath, "/api/**", "[]");
        MvcResult result = mockMvc.perform(get(requestUri).contextPath(contextPath)).andReturn();
        int actualStatus = result.getResponse().getStatus();
        log.info("响应状态: {}", actualStatus);
        assertEquals(expectedStatus, actualStatus, "响应状态不符合预期");
    }
}
