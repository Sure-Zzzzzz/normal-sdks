package io.github.surezzzzzz.sdk.auth.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.server.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.auth.aksk.server.test.SimpleAkskServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 启动校验 fail-fast 测试。
 * <p>
 * 旧 @Order(2) /api 链删除后兜底消失，以下配置态必须启动即报错（不允许裸奔）：
 * keyId 误配路由前缀、protected-paths 覆盖摘掉 /api/**、公共资源层被显式关闭。
 *
 * @author surezzzzzz
 */
@Slf4j
class StartupValidationFailFastTest {

    private static final String KEY_ID_PROPERTY =
            "io.github.surezzzzzz.sdk.auth.aksk.server.jwt.key-id";
    private static final String PROTECTED_PATHS_PROPERTY =
            "io.github.surezzzzzz.sdk.auth.resource.server.security.protected-paths";
    private static final String RESOURCE_SERVER_ENABLED_PROPERTY =
            "io.github.surezzzzzz.sdk.auth.resource.server.enabled";

    /**
     * 启动并断言因 ConfigurationException 失败。
     * <p>
     * 覆盖配置走命令行参数（优先级最高，压过 application-local.yml 的真实配置——
     * SpringApplicationBuilder.properties() 是 defaultProperties 优先级最低，压不住）；
     * 须用 servlet web：web-application-type=none 下 data-permission MVC 集成的
     * DataPermissionFacade 不装配，控制器构造先失败，轮不到校验器执行。
     *
     * @param expectedFragment 期望的错误信息片段
     * @param args             命令行参数（形如 --key=value）
     */
    private void assertStartupFailsWith(String expectedFragment, String... args) {
        Exception failure = assertThrows(Exception.class, () -> new SpringApplicationBuilder(
                SimpleAkskServerTestApplication.class)
                .web(WebApplicationType.SERVLET)
                .properties("server.port=0")
                .run(args));

        // @PostConstruct 抛出的 ConfigurationException 会被逐层包装（BeanCreationException 等），
        // 断言只认 cause 链中的 ConfigurationException，不依赖包装层级
        Throwable cause = failure;
        while (cause != null) {
            if (cause instanceof ConfigurationException
                    && cause.getMessage().contains(expectedFragment)) {
                log.info("fail-fast验证通过：错误信息命中[{}]", expectedFragment);
                return;
            }
            cause = cause.getCause();
        }
        fail("启动应因[" + expectedFragment + "]的ConfigurationException失败，实际异常: " + failure);
    }

    @Test
    void keyIdWithRoutePrefixMustFailFast() {
        log.info("测试keyId误配aksk/前缀时启动fail-fast");

        assertStartupFailsWith("keyId", "--" + KEY_ID_PROPERTY + "=aksk/wrong-key");
    }

    @Test
    void protectedPathsOverrideWithoutApiMustFailFast() {
        log.info("测试protected-paths覆盖摘掉/api/**时启动fail-fast");

        assertStartupFailsWith("/api/**", "--" + PROTECTED_PATHS_PROPERTY + "=/other/**");
    }

    @Test
    void resourceServerDisabledMustFailFast() {
        log.info("测试公共资源层被显式关闭时启动fail-fast");

        assertStartupFailsWith("resource.server.enabled", "--" + RESOURCE_SERVER_ENABLED_PROPERTY + "=false");
    }
}
