package io.github.surezzzzzz.sdk.license.server.limiter.configuration;

import io.github.surezzzzzz.sdk.license.server.limiter.constant.SimpleLicenseLimiterPolicyAdaptorConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * License 客户维度限频命名空间配置。
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(prefix = SimpleLicenseLimiterPolicyAdaptorConstant.CONFIG_PREFIX)
public class SimpleLicenseLimiterPolicyAdaptorProperties {

    /**
     * Management 服务声明的 CUSTOMER 命名空间。
     */
    private String customerNamespace;
}
