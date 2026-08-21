package io.github.surezzzzzz.sdk.http.xff.test.cases;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.boot.SpringBootVersion;

/**
 * Security 集成测试仅在实际兼容基线 Spring Boot 2.7.9 执行。
 *
 * @author surezzzzzz
 */
final class SpringBoot279SecurityTestCondition implements ExecutionCondition {

    private static final String SUPPORTED_BOOT_VERSION = "2.7.9";

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(
            ExtensionContext context) {
        String bootVersion = SpringBootVersion.getVersion();
        if (SUPPORTED_BOOT_VERSION.equals(bootVersion)) {
            return ConditionEvaluationResult.enabled(
                    "当前 Spring Boot 版本为 Security 集成测试基线 2.7.9");
        }
        return ConditionEvaluationResult.disabled(
                "Security 集成测试仅支持 Spring Boot 2.7.9，当前版本为 "
                        + bootVersion);
    }
}
