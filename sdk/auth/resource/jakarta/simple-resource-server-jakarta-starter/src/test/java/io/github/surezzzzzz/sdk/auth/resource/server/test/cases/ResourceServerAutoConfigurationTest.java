package io.github.surezzzzzz.sdk.auth.resource.server.test.cases;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.SimpleApplicationAuthorizationConstant;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceSubjectType;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationResult;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationSourceId;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceCredential;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourcePrincipal;
import io.github.surezzzzzz.sdk.auth.resource.core.spi.ResourceAuthenticationAdapter;
import io.github.surezzzzzz.sdk.auth.resource.server.configuration.ResourceServerAutoConfiguration;
import io.github.surezzzzzz.sdk.auth.resource.server.support.ResourceServerEngine;
import io.github.surezzzzzz.sdk.auth.resource.server.support.ResourceSecurityPathHelper;
import io.github.surezzzzzz.sdk.auth.resource.server.test.SimpleResourceServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 通用资源服务自动配置测试。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleResourceServerTestApplication.class)
@Import({ResourceServerAutoConfigurationTest.TestConfiguration.class,
        ResourceServerAutoConfigurationTest.HostWideSecurityConfiguration.class})
@AutoConfigureMockMvc
class ResourceServerAutoConfigurationTest {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private final WebApplicationContextRunner noProtectedPathContextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DispatcherServletAutoConfiguration.class,
                    WebMvcAutoConfiguration.class, ResourceServerAutoConfiguration.class));
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ResourceServerEngine engine;

    /**
     * 验证自动配置链在认证、精确权限和公共路径上的HTTP行为。
     *
     * @throws Exception MockMvc调用异常
     */
    @Test
    void shouldApplyUniqueResourceChainAndExactApiPermission() throws Exception {
        mockMvc.perform(get("/public/ping").header(AUTHORIZATION_HEADER, "Bearer malformed"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("public"));

        mockMvc.perform(get("/api/allowed"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/allowed").header(AUTHORIZATION_HEADER, bearer("iam")))
                .andExpect(status().isOk())
                .andExpect(content().string("allowed"));

        mockMvc.perform(get("/api/denied").header(AUTHORIZATION_HEADER, bearer("iam")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/unannotated").header(AUTHORIZATION_HEADER, bearer("iam")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/class-annotation").header(AUTHORIZATION_HEADER, bearer("iam")))
                .andExpect(status().isOk())
                .andExpect(content().string("class-annotation"));

        log.info("公共路径、401、精确权限200与403及类注解回退均已验证");
        assertNotNull(engine, "显式受保护路径必须装配唯一资源认证编排引擎");
    }

    /**
     * 验证application.yml中的精确规则可授权无注解接口、隔离HTTP方法并优先于注解。
     *
     * @throws Exception MockMvc调用异常
     */
    @Test
    void shouldApplyConfiguredApiPermissionRuleBeforeAnnotation() throws Exception {
        mockMvc.perform(get("/api/configured"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/configured").header(AUTHORIZATION_HEADER, bearer("iam")))
                .andExpect(status().isOk())
                .andExpect(content().string("configured-get"));

        mockMvc.perform(get("/api/configured").queryParam("tenantId", "tenant-a")
                        .header(AUTHORIZATION_HEADER, bearer("iam")))
                .andExpect(status().isOk())
                .andExpect(content().string("configured-get"));

        mockMvc.perform(get("/api/configured").queryParam("tenantId", "tenant-b")
                        .queryParam("apiPermission", "write")
                        .header(AUTHORIZATION_HEADER, bearer("iam")))
                .andExpect(status().isOk())
                .andExpect(content().string("configured-get"));

        mockMvc.perform(post("/api/configured").header(AUTHORIZATION_HEADER, bearer("iam")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/configured").header(AUTHORIZATION_HEADER, bearer("aksk")))
                .andExpect(status().isOk())
                .andExpect(content().string("configured-post"));

        mockMvc.perform(get("/api/configuration-priority").header(AUTHORIZATION_HEADER, bearer("iam")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/configuration-priority").header(AUTHORIZATION_HEADER, bearer("aksk")))
                .andExpect(status().isOk())
                .andExpect(content().string("configuration-priority"));

        log.info("application.yml规则已验证无注解授权、HTTP方法隔离与配置优先");
    }

    /**
     * 验证资源安全链优先于宿主宽匹配链。
     *
     * @throws Exception MockMvc调用异常
     */
    @Test
    void shouldApplyResourceChainBeforeHostWideSecurityChain() throws Exception {
        mockMvc.perform(get("/api/allowed"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/allowed").header(AUTHORIZATION_HEADER, bearer("iam")))
                .andExpect(status().isOk())
                .andExpect(content().string("allowed"));

        mockMvc.perform(get("/host/ping"))
                .andExpect(status().isOk())
                .andExpect(content().string("host"));

        log.info("资源窄链优先于宿主宽链，且未劫持宿主非资源路径");
    }

    /**
     * 验证资源自动配置可被Boot发现，且不存在受保护路径时不会装配资源认证引擎。
     */
    @Test
    void shouldNotClaimHostWhenProtectedPathsAreAbsent() {
        noProtectedPathContextRunner.run(context -> {
            log.info("无受保护路径时资源自动配置Bean数量: {}", context.getBeansOfType(
                    ResourceServerAutoConfiguration.class).size());
            assertFalse(context.containsBean("resourceServerEngine"), "无受保护路径不得创建认证编排引擎");
            assertEquals(Integer.valueOf(0), Integer.valueOf(context.getBeansOfType(
                    ResourceServerAutoConfiguration.class).size()), "无受保护路径不得接管资源安全链");
            assertEquals(Integer.valueOf(0), Integer.valueOf(context.getBeansOfType(
                    SecurityFilterChain.class).size()), "无受保护路径不得创建资源安全链");
        });
    }

    /**
     * 匹配器语义对照（jakarta 线回归防线）：链级 AntPathRequestMatcher 与 SDK 内部
     * spring-core AntPathMatcher 同为 Ant 系，核心命中必须一致（实证 spring-security-web 6.4.2
     * 无 PathPatternRequestMatcher，Security 6.5 才引入；链级保持 Ant 系即与 javax 线语义
     * 逐字一致，见配置器注释）。
     */
    @Test
    void shouldKeepChainMatcherSemanticsAlignedWithSdkPathMatcher() {
        AntPathMatcher antMatcher = new AntPathMatcher();

        log.info("匹配器语义对照开始：链级与SDK内部逐模式断言");

        assertTrue(AntPathRequestMatcher.antMatcher("/api/**").matches(mockRequest("GET", "/api/orders")),
                "链级匹配器必须命中受保护子路径");
        assertTrue(antMatcher.match("/api/**", "/api/orders"), "SDK内部匹配器必须命中受保护子路径");
        assertTrue(AntPathRequestMatcher.antMatcher("/public/**").matches(mockRequest("GET", "/public/ping")),
                "链级匹配器必须命中公开子路径");
        assertFalse(AntPathRequestMatcher.antMatcher("/api/**").matches(mockRequest("GET", "/host/ping")),
                "链级匹配器不得命中资源链外路径");
        assertFalse(antMatcher.match("/api/**", "/host/ping"), "SDK内部匹配器不得命中资源链外路径");
        assertTrue(AntPathRequestMatcher.antMatcher("/api/configured").matches(mockRequest("GET", "/api/configured")),
                "精确模式必须命中精确路径");
        assertFalse(AntPathRequestMatcher.antMatcher("/api/configured").matches(mockRequest("GET", "/api/configured/other")),
                "精确模式不得命中更长路径");
        assertFalse(AntPathRequestMatcher.antMatcher("/api/configured").matches(mockRequest("GET", "/api/configured/")),
                "Ant系不容忍精确模式尾斜杠，链级与旧线一致");
        assertFalse(antMatcher.match("/api/configured", "/api/configured/"),
                "Ant系不容忍精确模式尾斜杠，SDK内部与旧线一致");

        log.info("匹配器语义对照通过：核心命中一致，尾斜杠行为一致");
        assertEquals(Collections.singletonList("/api/**"), ResourceSecurityPathHelper.normalizePaths(
                        Collections.singletonList("/api/**"), null, true),
                "SDK内部路径归一化语义与javax线保持一致");
    }

    private org.springframework.mock.web.MockHttpServletRequest mockRequest(String method, String uri) {
        org.springframework.mock.web.MockHttpServletRequest request =
                new org.springframework.mock.web.MockHttpServletRequest(method, uri);
        // Security 6 的 AntPathRequestMatcher 按 servletPath+pathInfo 取应用内路径；
        // 裸 MockHttpServletRequest 的 servletPath 为空串会导致匹配器取不到路径
        request.setServletPath(uri);
        return request;
    }

    private String bearer(String sourceId) {
        String header = "{\"alg\":\"dir\",\"enc\":\"A256GCM\",\"kid\":\"" + sourceId + "/key-a\"}";
        String encodedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(header.getBytes(StandardCharsets.UTF_8));
        return BEARER_PREFIX + encodedHeader + ".encrypted-key.iv.cipher-text.authentication-tag";
    }

    /**
     * 自动配置测试应用。
     */
    @org.springframework.context.annotation.Configuration
    static class TestConfiguration {

        /**
         * 注册测试Controller。
         *
         * @return 测试Controller
         */
        @Bean
        TestController testController() {
            return new TestController();
        }

        /**
         * 注册类注解回退测试Controller。
         *
         * @return 测试Controller
         */
        @Bean
        TestController.ClassPermissionController classPermissionController() {
            return new TestController.ClassPermissionController();
        }

        /**
         * 注册无凭据的测试用户服务，避免测试报告输出随机密码。
         *
         * @return 测试用户服务
         */
        @Bean
        UserDetailsService testUserDetailsService() {
            return username -> {
                throw new UsernameNotFoundException("测试不提供表单用户");
            };
        }

        /**
         * 注册来源中立认证适配器。
         *
         * @return 测试认证适配器
         */
        @Bean
        ResourceAuthenticationAdapter resourceAuthenticationAdapter() {
            return new ResourceAuthenticationAdapter() {
                private final ResourceAuthenticationSourceId sourceId = new ResourceAuthenticationSourceId("iam");

                @Override
                public ResourceAuthenticationSourceId sourceId() {
                    return sourceId;
                }

                @Override
                public ResourceAuthenticationResult authenticate(ResourceCredential credential) {
                    Instant now = Instant.now();
                    ApplicationAuthorizationContext authorization = new ApplicationAuthorizationContext(
                            SimpleApplicationAuthorizationConstant.PROTOCOL,
                            SimpleApplicationAuthorizationConstant.VERSION,
                            ApplicationAuthorizationSubjectType.HUMAN, "subject-a", "app-a", true,
                            Collections.<String>emptyList(), Collections.<String>emptyList(),
                            Collections.singletonList("read"), null, 1L, "manifest-a", "digest-a",
                            now.minusSeconds(1L), now.plusSeconds(60L));
                    return ResourceAuthenticationResult.authenticated(new VerifiedResourcePrincipal(sourceId,
                            ResourceSubjectType.HUMAN, "subject-a"), authorization);
                }
            };
        }

        /**
         * 注册具有写权限的第二认证适配器。
         *
         * @return 测试认证适配器
         */
        @Bean
        ResourceAuthenticationAdapter writeResourceAuthenticationAdapter() {
            return new ResourceAuthenticationAdapter() {
                private final ResourceAuthenticationSourceId sourceId = new ResourceAuthenticationSourceId("aksk");

                @Override
                public ResourceAuthenticationSourceId sourceId() {
                    return sourceId;
                }

                @Override
                public ResourceAuthenticationResult authenticate(ResourceCredential credential) {
                    Instant now = Instant.now();
                    ApplicationAuthorizationContext authorization = new ApplicationAuthorizationContext(
                            SimpleApplicationAuthorizationConstant.PROTOCOL,
                            SimpleApplicationAuthorizationConstant.VERSION,
                            ApplicationAuthorizationSubjectType.SERVICE, "subject-b", "app-a", true,
                            Collections.<String>emptyList(), Collections.<String>emptyList(),
                            Collections.singletonList("write"), null, 1L, "manifest-b", "digest-b",
                            now.minusSeconds(1L), now.plusSeconds(60L));
                    return ResourceAuthenticationResult.authenticated(new VerifiedResourcePrincipal(sourceId,
                            ResourceSubjectType.SERVICE, "subject-b"), authorization);
                }
            };
        }

    }

    /**
     * 宿主宽匹配安全链配置（jakarta 线唯一形态，用于验证资源窄链排序）。
     */
    @org.springframework.context.annotation.Configuration
    static class HostWideSecurityConfiguration {

        /**
         * 注册宿主宽匹配安全链，用于验证资源窄链排序。
         *
         * @param http Spring Security配置器
         * @return 宿主安全链
         * @throws Exception 配置异常
         */
        @Bean
        @Order(100)
        SecurityFilterChain hostWideSecurityFilterChain(HttpSecurity http) throws Exception {
            http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
            return http.build();
        }
    }

    /**
     * 测试Controller。
     */
    @RestController
    public static class TestController {

        /**
         * 公开请求。
         *
         * @return 固定公开响应
         */
        @GetMapping("/public/ping")
        public String publicPing() {
            return "public";
        }

        /**
         * 已授权请求。
         *
         * @return 固定授权响应
         */
        @GetMapping("/api/allowed")
        @RequireApiPermission("read")
        public String allowed() {
            return "allowed";
        }

        /**
         * 无权限请求。
         *
         * @return 固定响应
         */
        @GetMapping("/api/denied")
        @RequireApiPermission("write")
        public String denied() {
            return "denied";
        }

        /**
         * 未标注权限请求。
         *
         * @return 固定响应
         */
        @GetMapping("/api/unannotated")
        public String unannotated() {
            return "unannotated";
        }

        /**
         * 配置化GET权限请求。
         *
         * @return 固定配置化响应
         */
        @GetMapping("/api/configured")
        public String configuredGet() {
            return "configured-get";
        }

        /**
         * 配置化POST权限请求。
         *
         * @return 固定配置化响应
         */
        @PostMapping("/api/configured")
        public String configuredPost() {
            return "configured-post";
        }

        /**
         * 配置优先请求。
         *
         * @return 固定配置化响应
         */
        @GetMapping("/api/configuration-priority")
        @RequireApiPermission("read")
        public String configurationPriority() {
            return "configuration-priority";
        }

        /**
         * 宿主路径请求。
         *
         * @return 固定宿主响应
         */
        @GetMapping("/host/ping")
        public String hostPing() {
            return "host";
        }

        /**
         * 类注解权限回退测试Controller。
         */
        @RestController
        @RequireApiPermission("read")
        public static class ClassPermissionController {

            /**
             * 验证未配置规则时使用类级权限注解。
             *
             * @return 固定响应
             */
            @GetMapping("/api/class-annotation")
            public String classAnnotation() {
                return "class-annotation";
            }
        }
    }
}
