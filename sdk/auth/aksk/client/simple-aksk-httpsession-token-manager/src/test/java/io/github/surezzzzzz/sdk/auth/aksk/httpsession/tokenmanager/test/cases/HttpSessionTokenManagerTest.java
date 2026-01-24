package io.github.surezzzzzz.sdk.auth.aksk.httpsession.tokenmanager.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.provider.SecurityContextProvider;
import io.github.surezzzzzz.sdk.auth.aksk.httpsession.tokenmanager.manager.HttpSessionTokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.httpsession.tokenmanager.strategy.HttpSessionTokenCacheStrategy;
import io.github.surezzzzzz.sdk.auth.aksk.httpsession.tokenmanager.test.SimpleAkskHttpSessionTokenManagerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HttpSessionTokenManager 集成测试
 * <p>
 * 测试 HttpSessionTokenManager 的核心功能
 * <p>
 * 注意：HttpSession Token Manager 依赖 Web 请求上下文，
 * 这些测试会在没有 HttpSession 的环境下测试 TokenManager 的基本逻辑。
 * 因为没有 HttpSession，每次获取都会从服务器重新获取 Token。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleAkskHttpSessionTokenManagerTestApplication.class)
class HttpSessionTokenManagerTest {

    @Autowired
    private HttpSessionTokenManager tokenManager;

    @Autowired
    private HttpSessionTokenCacheStrategy tokenCacheStrategy;

    @Autowired
    private SecurityContextProvider securityContextProvider;

    @Test
    @DisplayName("测试首次获取 Token - 无 HttpSession 环境")
    void testGetTokenFirstTime() {
        log.info("======================================");
        log.info("测试首次获取 Token - 无 HttpSession 环境");
        log.info("======================================");

        log.info("开始获取 Token（无 HttpSession，应从服务器获取）...");
        String token = tokenManager.getToken();

        log.info("获取的 Token: {}", token);
        assertNotNull(token, "Token 不应为 null");
        assertTrue(token.length() > 0, "Token 不应为空字符串");
        assertTrue(token.startsWith("eyJ"), "Token 应该是 JWT 格式（以 eyJ 开头）");

        log.info("======================================");
    }

    @Test
    @DisplayName("测试重复获取 Token - 无缓存场景")
    void testGetTokenMultipleTimes() {
        log.info("======================================");
        log.info("测试重复获取 Token - 无缓存场景");
        log.info("======================================");

        log.info("第一次获取 Token...");
        String firstToken = tokenManager.getToken();
        log.info("第一次获取的 Token: {}", firstToken);

        log.info("第二次获取 Token（无 HttpSession 缓存，会重新获取）...");
        String secondToken = tokenManager.getToken();
        log.info("第二次获取的 Token: {}", secondToken);

        assertNotNull(firstToken, "第一次获取的 Token 不应为 null");
        assertNotNull(secondToken, "第二次获取的 Token 不应为 null");
        // 注意：因为没有 HttpSession，每次都会重新获取 Token
        // 服务器可能返回相同或不同的 Token

        log.info("======================================");
    }

    @Test
    @DisplayName("测试清除 Token - 无 HttpSession 环境")
    void testClearToken() {
        log.info("======================================");
        log.info("测试清除 Token - 无 HttpSession 环境");
        log.info("======================================");

        log.info("先获取 Token...");
        String firstToken = tokenManager.getToken();
        log.info("第一次获取的 Token: {}", firstToken);

        log.info("清除 Token（无 HttpSession，操作不会报错）...");
        tokenManager.clearToken();

        log.info("再次获取 Token...");
        String secondToken = tokenManager.getToken();
        log.info("第二次获取的 Token: {}", secondToken);

        assertNotNull(secondToken, "第二次获取的 Token 不应为 null");

        log.info("======================================");
    }

    @Test
    @DisplayName("测试带 security_context 获取 Token")
    void testGetTokenWithSecurityContext() {
        log.info("======================================");
        log.info("测试带 security_context 获取 Token");
        log.info("======================================");

        // 注意：默认的 SecurityContextProvider 返回 null
        // 这个测试验证在无 security_context 的场景下能正常工作
        String token = tokenManager.getToken();

        log.info("获取的 Token: {}", token);
        assertNotNull(token, "Token 不应为 null");

        // 验证使用了默认 Key
        String securityContext = securityContextProvider.getSecurityContext();
        String cacheKey = tokenCacheStrategy.generateCacheKey(securityContext);
        log.info("缓存 Key: {}", cacheKey);
        assertTrue(cacheKey.contains("simple_aksk_access_token"), "无 security_context 时应使用默认 Key");

        log.info("======================================");
    }

    @Test
    @DisplayName("测试验证 TokenManager Bean 注入")
    void testTokenManagerInjection() {
        log.info("======================================");
        log.info("测试验证 TokenManager Bean 注入");
        log.info("======================================");

        assertNotNull(tokenManager, "TokenManager 应该被正确注入");
        assertNotNull(tokenCacheStrategy, "TokenCacheStrategy 应该被正确注入");
        assertNotNull(securityContextProvider, "SecurityContextProvider 应该被正确注入");

        log.info("TokenManager 类型: {}", tokenManager.getClass().getName());
        log.info("TokenCacheStrategy 类型: {}", tokenCacheStrategy.getClass().getName());
        log.info("SecurityContextProvider 类型: {}", securityContextProvider.getClass().getName());

        log.info("======================================");
    }
}
