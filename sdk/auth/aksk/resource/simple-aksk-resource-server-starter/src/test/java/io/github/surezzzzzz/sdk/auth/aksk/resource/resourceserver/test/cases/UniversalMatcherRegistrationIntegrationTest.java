package io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.test.SimpleAkskResourceServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Universal matcher registration integration test
 *
 * @author surezzzzzz
 */
@Slf4j
class UniversalMatcherRegistrationIntegrationTest {

    @Nested
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
    class ProtectedUniversalTest {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void testProtectedUniversalShouldStartAndReturn401() throws Exception {
            assertStatusAndBody(mockMvc, "/api/context-path/basic", "/api", 401,
                    "/api/**", "[]", null);
        }
    }

    @Nested
    @SpringBootTest(
            classes = SimpleAkskResourceServerTestApplication.class,
            properties = {
                    "server.servlet.context-path=/api",
                    "io.github.surezzzzzz.sdk.auth.aksk.resource.server.enabled=true",
                    "io.github.surezzzzzz.sdk.auth.aksk.resource.server.security.context-path-aware=true",
                    "io.github.surezzzzzz.sdk.auth.aksk.resource.server.security.protected-paths=",
                    "io.github.surezzzzzz.sdk.auth.aksk.resource.server.security.permit-all-paths[0]=/api/**"
            }
    )
    @AutoConfigureMockMvc
    class PermitUniversalTest {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void testPermitUniversalWithoutProtectedShouldStartAndReturn200() throws Exception {
            assertStatusAndBody(mockMvc, "/api/context-path/basic", "/api", 200,
                    "[]", "/api/**", "context-path-basic");
        }
    }

    private void assertStatusAndBody(MockMvc mockMvc, String requestUri, String contextPath, int expectedStatus,
                                     String protectedPaths, String permitAllPaths,
                                     String expectedMessage) throws Exception {
        log.info("请求 URI: {}, contextPath: {}, protectedPaths: {}, permitAllPaths: {}",
                requestUri, contextPath, protectedPaths, permitAllPaths);
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
