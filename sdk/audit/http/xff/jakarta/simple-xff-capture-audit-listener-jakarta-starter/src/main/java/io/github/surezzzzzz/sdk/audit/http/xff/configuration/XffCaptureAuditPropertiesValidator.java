package io.github.surezzzzzz.sdk.audit.http.xff.configuration;

import io.github.surezzzzzz.sdk.audit.http.xff.annotation.SimpleXffCaptureAuditListenerComponent;
import io.github.surezzzzzz.sdk.audit.http.xff.constant.SimpleXffCaptureAuditListenerConstant;
import io.github.surezzzzzz.sdk.audit.http.xff.provider.LoggingXffCaptureAuditPersistenceProvider;
import io.github.surezzzzzz.sdk.audit.http.xff.support.XffCaptureAuditApplicationNameHelper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;

/**
 * XFF Capture 审计配置启动期校验器。
 *
 * <p>独立 Bean 确保调用方覆盖执行器时仍会执行完整配置校验。</p>
 *
 * @author surezzzzzz
 */
@SimpleXffCaptureAuditListenerComponent
public class XffCaptureAuditPropertiesValidator {

    /**
     * 创建校验器并执行启动期校验。
     *
     * @param properties      Listener 配置
     * @param environment     Spring Environment
     * @param loggingProvider 默认日志 Provider
     */
    public XffCaptureAuditPropertiesValidator(SimpleXffCaptureAuditListenerProperties properties,
                                              Environment environment,
                                              @Qualifier(SimpleXffCaptureAuditListenerConstant.LOGGING_PROVIDER_BEAN_NAME)
                                              LoggingXffCaptureAuditPersistenceProvider loggingProvider) {
        properties.validate();
        XffCaptureAuditApplicationNameHelper.resolve(properties, environment);
    }
}
