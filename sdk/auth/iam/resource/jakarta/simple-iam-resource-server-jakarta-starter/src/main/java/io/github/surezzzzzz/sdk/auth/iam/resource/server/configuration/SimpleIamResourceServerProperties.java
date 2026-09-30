package io.github.surezzzzzz.sdk.auth.iam.resource.server.configuration;

import io.github.surezzzzzz.sdk.auth.iam.resource.server.constant.SimpleIamResourceServerConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Simple IAM Resource Server 配置
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = SimpleIamResourceServerConstant.CONFIG_PREFIX)
public class SimpleIamResourceServerProperties {

    /**
     * IAM资源令牌验证端点。
     */
    private String verificationEndpoint;
    /**
     * 独立资源验证客户端标识。
     */
    private String clientId;
    /**
     * 独立资源验证客户端密钥。
     */
    private String clientSecret;
    /**
     * 连接超时毫秒数。
     */
    private int connectTimeoutMillis = 3000;
    /**
     * 读取超时毫秒数。
     */
    private int readTimeoutMillis = 5000;
}
