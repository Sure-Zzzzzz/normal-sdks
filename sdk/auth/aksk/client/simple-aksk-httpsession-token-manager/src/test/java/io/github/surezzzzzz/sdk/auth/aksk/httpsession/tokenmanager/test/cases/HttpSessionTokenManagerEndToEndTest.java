package io.github.surezzzzzz.sdk.auth.aksk.httpsession.tokenmanager.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.httpsession.tokenmanager.manager.HttpSessionTokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.httpsession.tokenmanager.test.SimpleAkskHttpSessionTokenManagerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HttpSessionTokenManager 端到端测试
 * <p>
 * 测试 HttpSessionTokenManager 的端到端功能
 * <p>
 * 注意：由于 HttpSession 依赖 Web 请求上下文，这些测试主要验证：
 * <ul>
 *   <li>TokenManager 能正确获取 Token</li>
 *   <li>Token 是有效的 JWT 格式</li>
 *   <li>Token 可以被解析</li>
 * </ul>
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleAkskHttpSessionTokenManagerTestApplication.class)
class HttpSessionTokenManagerEndToEndTest {

    @Autowired
    private HttpSessionTokenManager tokenManager;

    @Test
    @DisplayName("测试获取 Token 并验证格式")
    void testGetTokenAndVerifyFormat() {
        log.info("======================================");
        log.info("测试获取 Token 并验证格式");
        log.info("======================================");

        log.info("开始获取 Token...");
        String token = tokenManager.getToken();

        log.info("获取的 Token: {}", token);
        assertNotNull(token, "Token 不应为 null");
        assertTrue(token.length() > 0, "Token 不应为空字符串");
        assertTrue(token.startsWith("eyJ"), "Token 应该是 JWT 格式（以 eyJ 开头）");

        // 验证 Token 包含三个部分（header.payload.signature）
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length, "JWT Token 应包含三个部分");

        log.info("Token 格式验证通过");
        log.info("======================================");
    }

    @Test
    @DisplayName("测试多次获取 Token")
    void testGetTokenMultipleTimes() {
        log.info("======================================");
        log.info("测试多次获取 Token");
        log.info("======================================");

        log.info("第一次获取 Token...");
        String firstToken = tokenManager.getToken();
        assertNotNull(firstToken, "第一次获取的 Token 不应为 null");
        log.info("第一次获取成功");

        log.info("第二次获取 Token...");
        String secondToken = tokenManager.getToken();
        assertNotNull(secondToken, "第二次获取的 Token 不应为 null");
        log.info("第二次获取成功");

        log.info("第三次获取 Token...");
        String thirdToken = tokenManager.getToken();
        assertNotNull(thirdToken, "第三次获取的 Token 不应为 null");
        log.info("第三次获取成功");

        // 注意：由于没有 HttpSession，每次都会重新获取 Token
        // 服务器可能返回相同或不同的 Token
        log.info("所有 Token 获取成功");
        log.info("======================================");
    }

    @Test
    @DisplayName("测试清除 Token 后重新获取")
    void testClearAndGetToken() {
        log.info("======================================");
        log.info("测试清除 Token 后重新获取");
        log.info("======================================");

        log.info("第一次获取 Token...");
        String firstToken = tokenManager.getToken();
        assertNotNull(firstToken, "第一次获取的 Token 不应为 null");
        log.info("第一次获取成功: {}", firstToken.substring(0, Math.min(20, firstToken.length())) + "...");

        log.info("清除 Token...");
        tokenManager.clearToken();
        log.info("Token 已清除");

        log.info("清除后重新获取 Token...");
        String secondToken = tokenManager.getToken();
        assertNotNull(secondToken, "清除后获取的 Token 不应为 null");
        log.info("重新获取成功: {}", secondToken.substring(0, Math.min(20, secondToken.length())) + "...");

        // 验证两个 Token 都是有效的 JWT 格式
        assertTrue(firstToken.startsWith("eyJ"), "第一个 Token 应该是 JWT 格式");
        assertTrue(secondToken.startsWith("eyJ"), "第二个 Token 应该是 JWT 格式");

        log.info("清除和重新获取测试通过");
        log.info("======================================");
    }
}
