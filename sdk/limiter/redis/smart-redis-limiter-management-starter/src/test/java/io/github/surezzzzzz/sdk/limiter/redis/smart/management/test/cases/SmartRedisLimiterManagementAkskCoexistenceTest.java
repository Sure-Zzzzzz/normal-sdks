package io.github.surezzzzzz.sdk.limiter.redis.smart.management.test.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.management.configuration.SmartRedisLimiterManagementProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.configuration.SmartRedisLimiterManagementRestSecurityConfiguration;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.SmartRedisLimiterManagementConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.test.SmartRedisLimiterManagementTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Management 与 AKSK resource-server 共存安全隔离测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = SmartRedisLimiterManagementTestApplication.class,
        properties = {
                "io.github.surezzzzzz.sdk.auth.aksk.resource.server.enabled=true",
                "io.github.surezzzzzz.sdk.auth.aksk.resource.server.introspect.endpoint=http://127.0.0.1:65535/introspect",
                "io.github.surezzzzzz.sdk.auth.aksk.resource.server.security.protected-paths[0]=/api/v1/policy/**"
        })
@AutoConfigureMockMvc
public class SmartRedisLimiterManagementAkskCoexistenceTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SmartRedisLimiterManagementProperties managementProperties;
    @Autowired
    private ApplicationContext applicationContext;

    @Test
    public void testManagementRestTokenChainDoesNotRegisterWhenResourceServerEnabled() {
        assertEquals(0, applicationContext.getBeansOfType(SmartRedisLimiterManagementRestSecurityConfiguration.class).size(),
                "resource-server 开启时 management 不得注册 REST 固定 token 安全链");
    }

    @Test
    public void testManagementUiLoginPageIsAccessibleWhenAkskResourceCoexists() throws Exception {
        String url = managementProperties.getUi().getBasePath() + SmartRedisLimiterManagementConstant.PATH_LOGIN;
        log.info("测试 UI 登录页共存访问: url={}", url);
        mockMvc.perform(get(url))
                .andExpect(status().isOk());
        log.info("UI 登录页共存访问验证通过: status=200");
    }

    @Test
    public void testManagementUiPoliciesRedirectsToLoginWhenUnauthenticated() throws Exception {
        String url = managementProperties.getUi().getBasePath() + SmartRedisLimiterManagementConstant.PATH_POLICY_PAGE;
        log.info("测试未认证访问策略页面: url={}", url);
        mockMvc.perform(get(url))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        log.info("未认证访问策略页面重定向验证通过: status=3xx, redirectTo=**/login");
    }

    @Test
    public void testManagementUiCanLogInWhenAkskResourceCoexists() throws Exception {
        String loginUrl = managementProperties.getUi().getBasePath() + SmartRedisLimiterManagementConstant.PATH_LOGIN;
        String policyUrl = managementProperties.getUi().getBasePath() + SmartRedisLimiterManagementConstant.PATH_POLICY_PAGE;
        log.info("测试 Management 管理员共存登录: loginUrl={}, policyUrl={}, username={}",
                loginUrl, policyUrl, managementProperties.getAdmin().getUsername());
        MvcResult loginResult = mockMvc.perform(post(loginUrl)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("username", managementProperties.getAdmin().getUsername())
                        .param("password", managementProperties.getAdmin().getPassword()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(policyUrl))
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertNotNull(session, "管理登录成功必须建立认证会话");

        mockMvc.perform(get(policyUrl).session(session))
                .andExpect(status().isOk());
        log.info("Management 管理员共存登录验证通过: redirectTo={}, policyStatus=200", policyUrl);
    }

    @Test
    public void testManagementSnapshotIsProtectedByResourceServerWhenAkskResourceCoexists() throws Exception {
        String url = managementProperties.getApi().getBasePath() + SmartRedisLimiterManagementConstant.PATH_POLICY_SNAPSHOT;
        log.info("测试快照接口由 resource-server 接管: url={}", url);
        mockMvc.perform(get(url)
                        .param(SmartRedisLimiterManagementConstant.QUERY_PARAM_SERVICE_CODE, "test-service"))
                .andExpect(status().isUnauthorized());
        log.info("快照接口 resource-server 接管验证通过: status=401");
    }

    @Test
    public void testManagementAdminApiReturns401ForAnonymousWhenAkskResourceCoexists() throws Exception {
        String url = managementProperties.getApi().getBasePath() + SmartRedisLimiterManagementConstant.PATH_POLICY_ADMIN;
        log.info("测试管理 API 匿名访问共存拦截: url={}", url);
        mockMvc.perform(post(url).with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isUnauthorized());
        log.info("管理 API 匿名访问共存拦截验证通过: status=401");
    }

    @Test
    public void testRestApiIsHandledByResourceServerWhenEnabled() throws Exception {
        String url = managementProperties.getApi().getBasePath() + "/v1/policy";
        log.info("测试对外 REST API 由 resource-server 接管: url={}", url);
        mockMvc.perform(get(url))
                .andExpect(status().isUnauthorized());
        log.info("对外 REST API resource-server 接管验证通过: status=401");
    }
}
