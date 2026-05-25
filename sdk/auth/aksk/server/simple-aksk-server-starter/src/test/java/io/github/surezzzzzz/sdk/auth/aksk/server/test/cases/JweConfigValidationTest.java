package io.github.surezzzzzz.sdk.auth.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.server.test.SimpleAkskServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * JWE 配置校验测试
 * <p>
 * 验证 encryption-key 缺失、格式错误、长度错误时启动失败。
 * 每个测试用独立的 @TestPropertySource 覆盖配置，触发 @PostConstruct 校验。
 *
 * @author surezzzzzz
 */
@Slf4j
class JweConfigValidationTest {

    @Test
    void testMissingEncryptionKeyFailsStartup() {
        log.info("测试 encryption-key 未配置时启动失败");

        Exception ex = assertThrows(Exception.class, () ->
                org.springframework.boot.SpringApplication.run(
                        SimpleAkskServerTestApplication.class,
                        "--spring.profiles.active=local",
                        "--io.github.surezzzzzz.sdk.auth.aksk.server.jwt.encryption-key="
                )
        );

        Throwable root = getRootCause(ex);
        assertTrue(root.getMessage() != null &&
                        root.getMessage().contains("AES-256 encryption key 未配置"),
                "应提示 AES-256 encryption key 未配置，实际: " + root.getMessage());

        log.info("✓ encryption-key 未配置时启动失败验证通过");
    }

    @Test
    void testInvalidBase64EncryptionKeyFailsStartup() {
        log.info("测试 encryption-key 非 Base64 格式时启动失败");

        Exception ex = assertThrows(Exception.class, () ->
                org.springframework.boot.SpringApplication.run(
                        SimpleAkskServerTestApplication.class,
                        "--spring.profiles.active=local",
                        "--io.github.surezzzzzz.sdk.auth.aksk.server.jwt.encryption-key=not-valid-base64!!!"
                )
        );

        Throwable root = getRootCause(ex);
        assertTrue(root.getMessage() != null &&
                        root.getMessage().contains("Base64"),
                "应提示 Base64 格式错误，实际: " + root.getMessage());

        log.info("✓ encryption-key 非 Base64 时启动失败验证通过");
    }

    @Test
    void testWrongLengthEncryptionKeyFailsStartup() {
        log.info("测试 encryption-key 长度不足（非 32 字节）时启动失败");

        // 16 字节的 Base64（AES-128，不是 AES-256）
        String shortKey = java.util.Base64.getEncoder().encodeToString(new byte[16]);

        Exception ex = assertThrows(Exception.class, () ->
                org.springframework.boot.SpringApplication.run(
                        SimpleAkskServerTestApplication.class,
                        "--spring.profiles.active=local",
                        "--io.github.surezzzzzz.sdk.auth.aksk.server.jwt.encryption-key=" + shortKey
                )
        );

        Throwable root = getRootCause(ex);
        assertTrue(root.getMessage() != null &&
                        root.getMessage().contains("密钥长度错误"),
                "应提示密钥长度错误，实际: " + root.getMessage());

        log.info("✓ encryption-key 长度不足时启动失败验证通过");
    }

    private static Throwable getRootCause(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
