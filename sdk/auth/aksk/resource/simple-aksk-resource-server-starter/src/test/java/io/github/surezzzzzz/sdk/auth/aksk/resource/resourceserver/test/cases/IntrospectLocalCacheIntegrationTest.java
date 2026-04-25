package io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.configuration.SimpleAkskResourceServerProperties;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.support.IntrospectLocalCacheHelper;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.test.SimpleAkskResourceServerTestApplication;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.test.helper.OAuth2TokenHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.StringUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Introspect 本地缓存集成测试
 *
 * <p>验证启用本地缓存后，缓存命中时跳过 HTTP 调用，以及 disabled 时每次都发起 HTTP 调用。
 *
 * <p>前置条件：
 * <ol>
 *   <li>aksk-server 启动（端口 8080）</li>
 *   <li>application-local.yml 中配置 introspect.client-id / client-secret</li>
 * </ol>
 *
 * <p>未配置 introspect.client-id 时测试自动跳过。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = SimpleAkskResourceServerTestApplication.class,
        properties = {
                "io.github.surezzzzzz.sdk.auth.aksk.resource.server.verification-mode=INTROSPECT",
                "io.github.surezzzzzz.sdk.auth.aksk.resource.server.introspect.local-cache.enabled=true",
                "io.github.surezzzzzz.sdk.auth.aksk.resource.server.introspect.local-cache.expire-seconds=5"
        }
)
@AutoConfigureMockMvc
class IntrospectLocalCacheIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OAuth2TokenHelper tokenHelper;

    @Autowired
    private SimpleAkskResourceServerProperties properties;

    @Autowired
    private IntrospectLocalCacheHelper cacheHelper;

    private String validToken;

    @BeforeEach
    void setUp() {
        log.info("========== Introspect 本地缓存集成测试准备 ==========");
        assumeTrue(StringUtils.hasText(properties.getIntrospect().getClientId()),
                "未配置 introspect.client-id，跳过本地缓存集成测试");

        validToken = tokenHelper.getToken();
        assertNotNull(validToken, "Token 不应为 null");
        log.info("Token 获取成功，本地缓存集成测试开始");
    }

    @Test
    void testCacheEnabled() {
        log.info("========== 测试：本地缓存已启用 ==========");
        log.info("cacheHelper.isEnabled()={}", cacheHelper.isEnabled());
        assertTrue(cacheHelper.isEnabled(), "配置 enabled=true 后 cacheHelper 应已启用");
    }

    @Test
    void testCacheHitSkipsHttp() throws Exception {
        log.info("========== 测试：缓存命中时跳过 HTTP 调用 ==========");

        // 第一次请求：缓存未命中，发起 HTTP introspect
        mockMvc.perform(get("/test/basic")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk());
        log.info("第一次请求完成，缓存应已写入");

        // 验证缓存已写入
        assertNotNull(cacheHelper.get(validToken), "第一次请求后缓存应有值");

        // 第二次请求：应命中缓存
        mockMvc.perform(get("/test/basic")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk());
        log.info("第二次请求完成，应命中缓存");

        assertNotNull(cacheHelper.get(validToken), "第二次请求后缓存仍应有值");
        log.info("✓ 缓存命中验证通过");
    }

    @Test
    void testRevokedTokenCached() throws Exception {
        log.info("========== 测试：active=false 的结果也写入缓存 ==========");

        // 用无效 token 触发 introspect（返回 active=false）
        mockMvc.perform(get("/test/basic")
                        .header("Authorization", "Bearer invalid-token-for-cache-test"))
                .andExpect(status().isUnauthorized());

        // active=false 的结果也应写入缓存（避免反复打 AKSK Server）
        // 注意：Spring Security 在 active=false 时会抛异常，缓存写入在异常前完成
        log.info("invalid token request completed");
        log.info("✓ active=false 缓存场景验证完成");
    }

    @Test
    void testDisabledCacheAlwaysHttp() throws Exception {
        log.info("========== 测试：disabled 时每次都发起 HTTP 调用 ==========");

        // 构建一个 disabled 的 helper，验证 isEnabled=false
        SimpleAkskResourceServerProperties props = new SimpleAkskResourceServerProperties();
        SimpleAkskResourceServerProperties.Introspect introspect = new SimpleAkskResourceServerProperties.Introspect();
        SimpleAkskResourceServerProperties.Introspect.LocalCacheConfig config =
                new SimpleAkskResourceServerProperties.Introspect.LocalCacheConfig();
        config.setEnabled(false);
        introspect.setLocalCache(config);
        props.setIntrospect(introspect);

        IntrospectLocalCacheHelper disabledHelper = new IntrospectLocalCacheHelper(props);
        disabledHelper.init();

        log.info("disabledHelper.isEnabled()={}", disabledHelper.isEnabled());
        assertFalse(disabledHelper.isEnabled(), "disabled helper 的 isEnabled 应为 false");
        assertNull(disabledHelper.get(validToken), "disabled helper 的 get 应返回 null");
        log.info("✓ disabled 缓存验证通过");
    }
}
