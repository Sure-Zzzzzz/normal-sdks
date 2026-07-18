package io.github.surezzzzzz.sdk.limiter.redis.smart.test.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.SmartRedisLimiterPolicyValidationHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SmartRedisLimiter 动态策略字段校验测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class SmartRedisLimiterPolicyValidationTest {

    @Test
    public void testStableCodeBoundaries() {
        log.info("开始测试稳定编码边界");
        String serviceCode = repeat('a', 128);
        assertEquals(serviceCode,
                SmartRedisLimiterPolicyValidationHelper.normalizeServiceCode(serviceCode));
        assertEquals("Test_1.0-service",
                SmartRedisLimiterPolicyValidationHelper.normalizeResourceCode(" Test_1.0-service "));

        assertInvalid(() -> SmartRedisLimiterPolicyValidationHelper.normalizeServiceCode(null));
        assertInvalid(() -> SmartRedisLimiterPolicyValidationHelper.normalizeServiceCode(" "));
        assertInvalid(() -> SmartRedisLimiterPolicyValidationHelper.normalizeServiceCode("-test"));
        assertInvalid(() -> SmartRedisLimiterPolicyValidationHelper.normalizeServiceCode("test:service"));
        assertInvalid(() -> SmartRedisLimiterPolicyValidationHelper.normalizeServiceCode(repeat('a', 129)));
        log.info("稳定编码边界测试通过");
    }

    @Test
    public void testSubjectAndOperatorValidation() {
        log.info("开始测试 subject 和 operator 校验");
        assertEquals("测试对象",
                SmartRedisLimiterPolicyValidationHelper.normalizeSubject(" 测试对象 "));
        assertEquals("管理员",
                SmartRedisLimiterPolicyValidationHelper.normalizeOperator(" 管理员 "));

        SmartRedisLimiterException subjectException = assertThrows(SmartRedisLimiterException.class,
                () -> SmartRedisLimiterPolicyValidationHelper.normalizeSubject("secret\tvalue"));
        assertEquals(ErrorCode.POLICY_KEY_INVALID, subjectException.getErrorCode());
        assertFalse(subjectException.getMessage().contains("secret"));

        SmartRedisLimiterException operatorException = assertThrows(SmartRedisLimiterException.class,
                () -> SmartRedisLimiterPolicyValidationHelper.normalizeOperator("admin\nname"));
        assertEquals(ErrorCode.MANAGEMENT_PAYLOAD_INVALID, operatorException.getErrorCode());
        assertFalse(operatorException.getMessage().contains("admin"),
                "异常消息不应泄露 operator 原始值");
        log.info("subject 和 operator 校验测试通过");
    }

    private void assertInvalid(Runnable action) {
        SmartRedisLimiterException exception = assertThrows(SmartRedisLimiterException.class, action::run);
        assertEquals(ErrorCode.POLICY_KEY_INVALID, exception.getErrorCode());
    }

    private String repeat(char value, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
