package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.test;

import org.junit.jupiter.api.Assertions;

import java.util.Objects;

/**
 * 避免测试失败报告输出访问令牌等敏感值的断言。
 *
 * @author surezzzzzz
 */
public final class SensitiveTestAssertions {

    private SensitiveTestAssertions() {
    }

    public static void assertSameToken(String expected, String actual, String message) {
        Assertions.assertTrue(Objects.equals(expected, actual), message);
    }

    public static void assertAbsent(Object value, String message) {
        Assertions.assertTrue(value == null, message);
    }
}
