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
 * Context path aware disabled integration test
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = SimpleAkskResourceServerTestApplication.class,
        properties = {
                "server.servlet.context-path=/api",
                "io.github.surezzzzzz.sdk.auth.aksk.resource.server.enabled=true",
                "io.github.surezzzzzz.sdk.auth.aksk.resource.server.security.context-path-aware=false",
                "io.github.surezzzzzz.sdk.auth.aksk.resource.server.security.protected-paths[0]=/api/**",
                "io.github.surezzzzzz.sdk.auth.aksk.resource.server.security.permit-all-paths="
        }
)
@AutoConfigureMockMvc
class ContextPathAwareDisabledIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testContextPathAwareFalseShouldKeepOldBehavior() throws Exception {
        String requestUri = "/api/context-path/basic";
        String contextPath = "/api";
        log.info("请求 URI: {}, contextPath: {}, protectedPaths: {}, permitAllPaths: {}, contextPathAware={}",
                requestUri, contextPath, "/api/**", "[]", false);
        MvcResult result = mockMvc.perform(get(requestUri).contextPath(contextPath)).andReturn();
        int actualStatus = result.getResponse().getStatus();
        String responseBody = result.getResponse().getContentAsString();
        log.info("响应状态: {}, 响应体: {}", actualStatus, responseBody);
        assertEquals(200, actualStatus, "关闭 context-path-aware 后应保留 2.0.0 行为");
        org.assertj.core.api.Assertions.assertThat(responseBody).contains("context-path-basic");
    }
}
