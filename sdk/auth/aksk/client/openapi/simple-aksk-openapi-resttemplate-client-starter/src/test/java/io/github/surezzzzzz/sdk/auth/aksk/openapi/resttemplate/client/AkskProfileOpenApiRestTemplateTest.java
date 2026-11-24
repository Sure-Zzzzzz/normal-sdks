package io.github.surezzzzzz.sdk.auth.aksk.openapi.resttemplate.client;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.ProfileTokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.openapi.client.AkskOpenApiClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 管理 OpenAPI RestTemplate 传输件的 AKP Profile 端到端测试
 *
 * <p>命名 Profile {@code second} 取本地 {@code application-local.yml} 的第二组已准入 AKP；
 * 传输件经 `aksk-profile` 选择后，底层模板、缓存与认证都切换到该 AKP。
 *
 * @author surezzzzzz
 */
@SpringBootTest(classes = OpenApiE2eTestApplication.class, properties = {
        "io.github.surezzzzzz.sdk.auth.aksk.openapi.client.aksk-profile=second"
})
class AkskProfileOpenApiRestTemplateTest {

    private static final String SECOND = "second";

    @Autowired
    private AkskOpenApiClient akskOpenApiClient;
    @Autowired
    private ProfileTokenManager profileTokenManager;

    @Test
    @DisplayName("aksk-profile=second 时经命名 Profile 调用真实 AKSK Server")
    void testNamedProfileCallSucceeds() {
        String secondToken = profileTokenManager.getToken(SECOND);
        String defaultToken = profileTokenManager.getToken();
        assertFalse(secondToken.isEmpty());
        assertFalse(defaultToken.isEmpty());
        // 不用 assertNotEquals：失败时不把 Token 明文打进测试报告
        assertFalse(secondToken.equals(defaultToken), "两个 Profile 使用不同 AKP，Token 不应相同");
        assertNotNull(akskOpenApiClient.getTokenStatistics());
    }
}

/**
 * aksk-profile 指向未配置 Profile 时启动失败
 *
 * <p>解析发生在默认客户端 Bean 创建期，属于启动期响亮失败。
 *
 * @author surezzzzzz
 */
class AkskProfileMissingStartupTest {

    @Test
    @DisplayName("aksk-profile 指向未配置 Profile 时启动失败")
    void testMissingProfileFailsStartup() {
        String key = "io.github.surezzzzzz.sdk.auth.aksk.openapi.client.aksk-profile";
        System.setProperty(key, "missing-profile");
        try {
            BeanCreationException failure = assertThrows(BeanCreationException.class,
                    () -> new SpringApplicationBuilder(OpenApiE2eTestApplication.class)
                            .web(WebApplicationType.NONE)
                            .run());
            Throwable cause = failure.getCause();
            boolean found = false;
            while (cause != null) {
                if (cause instanceof ConfigurationException) {
                    found = true;
                    break;
                }
                cause = cause.getCause();
            }
            assertTrue(found, "启动失败原因应是指向未配置 Profile 的配置异常");
        } finally {
            System.clearProperty(key);
        }
    }
}
