package io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.configuration;

import io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.constant.SimpleIamOidcAdapterConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

/**
 * Simple IAM OIDC Adapter 配置
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(prefix = SimpleIamOidcAdapterConstant.CONFIG_PREFIX)
public class SimpleIamOidcAdapterProperties {

    /**
     * 签发方标识，用于 ID token iss 校验，如 https://sso.example.com
     */
    private String issuer;

    /**
     * 授权端点地址（authorization endpoint）
     */
    private String authorizationUri;

    /**
     * 令牌端点地址（token endpoint）
     */
    private String tokenUri;

    /**
     * 公钥集端点地址（JWKS）
     */
    private String jwkSetUri;

    /**
     * 客户端 ID
     */
    private String clientId;

    /**
     * 客户端密钥，经部署环境管理，不能写入版本库
     */
    private String clientSecret;

    /**
     * 授权 scope 列表
     */
    private List<String> scopes = Arrays.asList(
            SimpleIamOidcAdapterConstant.DEFAULT_SCOPES.split(","));
}
