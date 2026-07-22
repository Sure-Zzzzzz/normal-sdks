package io.github.surezzzzzz.sdk.limiter.redis.smart.management.test.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.management.configuration.SmartRedisLimiterManagementProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.SmartRedisLimiterManagementConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.test.SmartRedisLimiterManagementTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.web.servlet.function.RequestPredicates.GET;

/**
 * Management 窄安全链隔离测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = {
                SmartRedisLimiterManagementTestApplication.class,
                SmartRedisLimiterManagementSecurityIsolationTest.HostApplicationConfiguration.class
        },
        properties = {
                "io.github.surezzzzzz.sdk.limiter.redis.smart.management.api.base-path=/internal/limiter-api",
                "io.github.surezzzzzz.sdk.limiter.redis.smart.management.ui.base-path=/ops/limiter-ui",
                "io.github.surezzzzzz.sdk.limiter.redis.smart.management.rest.policy-token=test-policy-token",
                "io.github.surezzzzzz.sdk.auth.aksk.resource.server.enabled=false"
        })
@AutoConfigureMockMvc
public class SmartRedisLimiterManagementSecurityIsolationTest {

    private static final String HOST_PUBLIC_PATH = "/host/public";
    private static final String HOST_PUBLIC_RESPONSE = "host-public";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SmartRedisLimiterManagementProperties managementProperties;

    @Test
    public void testApiAndUiSecurityChainsUseIndependentNarrowPaths() throws Exception {
        String apiBasePath = managementProperties.getApi().getBasePath();
        String uiBasePath = managementProperties.getUi().getBasePath();
        log.info("management 安全链路径: api={}, ui={}", apiBasePath, uiBasePath);

        mockMvc.perform(get(HOST_PUBLIC_PATH))
                .andExpect(status().isOk())
                .andExpect(content().string(HOST_PUBLIC_RESPONSE));
        log.info("宿主路径 {} 验证通过: status=200, body={}", HOST_PUBLIC_PATH, HOST_PUBLIC_RESPONSE);

        mockMvc.perform(get(uiBasePath + SmartRedisLimiterManagementConstant.PATH_POLICY_PAGE))
                .andExpect(status().is3xxRedirection());
        log.info("UI 策略页 {} 未认证验证通过: status=3xx", uiBasePath + SmartRedisLimiterManagementConstant.PATH_POLICY_PAGE);

        mockMvc.perform(get(apiBasePath + SmartRedisLimiterManagementConstant.PATH_POLICY_SNAPSHOT)
                        .param(SmartRedisLimiterManagementConstant.QUERY_PARAM_SERVICE_CODE, "security-service"))
                .andExpect(status().isUnauthorized());
        log.info("快照接口 {} 匿名访问验证通过: status=401", apiBasePath + SmartRedisLimiterManagementConstant.PATH_POLICY_SNAPSHOT);

        mockMvc.perform(get(apiBasePath + SmartRedisLimiterManagementConstant.PATH_POLICY_SNAPSHOT)
                        .header(SmartRedisLimiterManagementConstant.HEADER_POLICY_TOKEN, "test-policy-token")
                        .param(SmartRedisLimiterManagementConstant.QUERY_PARAM_SERVICE_CODE, "security-service"))
                .andExpect(status().isOk());
        log.info("快照接口 {} 固定 token 访问验证通过: status=200", apiBasePath
                + SmartRedisLimiterManagementConstant.PATH_POLICY_SNAPSHOT);

        mockMvc.perform(get(apiBasePath + SmartRedisLimiterManagementConstant.PATH_POLICY_ADMIN))
                .andExpect(status().isUnauthorized());
        log.info("管理 API {} 匿名访问验证通过: status=401", apiBasePath + SmartRedisLimiterManagementConstant.PATH_POLICY_ADMIN);

        mockMvc.perform(get(apiBasePath + "/v1/policy"))
                .andExpect(status().isUnauthorized());
        log.info("对外 REST API {}/v1/policy 匿名访问验证通过: status=401", apiBasePath);
    }

    /**
     * 宿主应用的路径与安全链（模拟共存场景，order > management 链，不干扰 management 路径）
     */
    @TestConfiguration
    public static class HostApplicationConfiguration {

        @Bean
        @Order(50)
        public SecurityFilterChain hostSecurityFilterChain(HttpSecurity http) throws Exception {
            http.antMatcher("/host/**")
                    .authorizeRequests()
                    .anyRequest()
                    .permitAll();
            return http.build();
        }

        @Bean
        public RouterFunction<ServerResponse> hostPublicRoute() {
            return RouterFunctions.route(GET(HOST_PUBLIC_PATH),
                    request -> ServerResponse.ok().body(HOST_PUBLIC_RESPONSE));
        }
    }
}
