package io.github.surezzzzzz.sdk.audit.iam.resource.configuration;

import io.github.surezzzzzz.sdk.audit.iam.resource.constant.SimpleIamResourceAuditListenerConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Simple IAM Resource Audit Listener 配置。
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(prefix = SimpleIamResourceAuditListenerConstant.CONFIG_PREFIX)
public class SimpleIamResourceAuditListenerProperties {

    /**
     * 是否启用审计监听（默认 true；handler 缺失时监听器不注册）。
     */
    private Boolean enable = Boolean.TRUE;
}
