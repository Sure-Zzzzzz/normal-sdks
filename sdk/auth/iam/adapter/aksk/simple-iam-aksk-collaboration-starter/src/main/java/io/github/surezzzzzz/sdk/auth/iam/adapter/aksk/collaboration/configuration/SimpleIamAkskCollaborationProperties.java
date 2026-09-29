package io.github.surezzzzzz.sdk.auth.iam.adapter.aksk.collaboration.configuration;

import io.github.surezzzzzz.sdk.auth.iam.adapter.aksk.collaboration.constant.SimpleIamAkskCollaborationConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Simple IAM AKSK Collaboration 配置。
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(prefix = SimpleIamAkskCollaborationConstant.CONFIG_PREFIX)
public class SimpleIamAkskCollaborationProperties {

    /**
     * 是否启用 IAM owner authorization 适配器（默认 false）。
     */
    private Boolean enabled = Boolean.FALSE;
    /**
     * IAM OAuth2 service token 端点。
     */
    private String tokenUri;
    /**
     * IAM 协作契约基础地址。
     */
    private String baseUri;
    /**
     * 协作客户端标识。
     */
    private String clientId;
    /**
     * 协作客户端密钥。
     */
    private String clientSecret;
    /**
     * 连接超时（毫秒）。
     */
    private Integer connectTimeoutMillis = SimpleIamAkskCollaborationConstant.DEFAULT_CONNECT_TIMEOUT_MILLIS;
    /**
     * 读取超时（毫秒）。
     */
    private Integer readTimeoutMillis = SimpleIamAkskCollaborationConstant.DEFAULT_READ_TIMEOUT_MILLIS;
    /**
     * 单次调用最大尝试次数。
     */
    private Integer maxAttempts = SimpleIamAkskCollaborationConstant.DEFAULT_MAX_ATTEMPTS;
}
