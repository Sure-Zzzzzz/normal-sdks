package io.github.surezzzzzz.sdk.auth.aksk.resource.securitycontext.configuration;

import io.github.surezzzzzz.sdk.auth.aksk.resource.securitycontext.constant.SimpleAkskSecurityContextConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Simple AKSK Security Context 配置属性
 *
 * <p>配置项说明：
 * <ul>
 *   <li>enable: 是否启用 Security Context（默认：true）</li>
 *   <li>headerPrefix: Header 前缀（默认：x-sure-auth-aksk-）</li>
 * </ul>
 *
 * <p>配置示例：
 * <pre>{@code
 * io:
 *   github:
 *     surezzzzzz:
 *       sdk:
 *         auth:
 *           aksk:
 *             resource:
 *               security-context:
 *                 enable: true
 *                 header-prefix: x-sure-auth-aksk-
 * }</pre>
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = SimpleAkskSecurityContextConstant.CONFIG_PREFIX)
public class SimpleAkskSecurityContextProperties {

    /**
     * 是否启用 Security Context
     */
    private Boolean enable = true;

    /**
     * Header 前缀（默认：x-sure-auth-aksk-）
     */
    private String headerPrefix = SimpleAkskSecurityContextConstant.DEFAULT_HEADER_PREFIX;
}
