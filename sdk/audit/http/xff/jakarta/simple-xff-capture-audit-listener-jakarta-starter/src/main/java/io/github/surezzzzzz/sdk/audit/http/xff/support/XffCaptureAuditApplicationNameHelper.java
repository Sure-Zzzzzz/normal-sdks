package io.github.surezzzzzz.sdk.audit.http.xff.support;

import io.github.surezzzzzz.sdk.audit.http.xff.configuration.SimpleXffCaptureAuditListenerProperties;
import io.github.surezzzzzz.sdk.audit.http.xff.constant.ErrorCode;
import io.github.surezzzzzz.sdk.audit.http.xff.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.audit.http.xff.constant.SimpleXffCaptureAuditListenerConstant;
import io.github.surezzzzzz.sdk.audit.http.xff.exception.XffCaptureAuditValidationException;
import org.springframework.core.env.Environment;

/**
 * XFF Capture 审计应用名称解析 Helper。
 *
 * @author surezzzzzz
 */
public final class XffCaptureAuditApplicationNameHelper {

    private XffCaptureAuditApplicationNameHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 解析生效应用名称。
     *
     * @param properties  Listener 配置
     * @param environment Spring Environment
     * @return 非空应用名称
     */
    public static String resolve(SimpleXffCaptureAuditListenerProperties properties,
                                 Environment environment) {
        String configured = normalize(properties.getApplicationName());
        if (configured != null) {
            return configured;
        }
        String springApplicationName = normalize(environment.getProperty(
                SimpleXffCaptureAuditListenerConstant.SPRING_APPLICATION_NAME_PROPERTY));
        if (springApplicationName != null) {
            return springApplicationName;
        }
        throw new XffCaptureAuditValidationException(ErrorCode.REQUIRED_VALUE_MISSING,
                String.format(ErrorMessage.REQUIRED_VALUE_MISSING,
                        SimpleXffCaptureAuditListenerConstant.FIELD_APPLICATION_NAME));
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
