package io.github.surezzzzzz.sdk.auth.aksk.httpsession.tokenmanager.configuration;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.SimpleAkskClientCoreConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Simple AKSK HttpSession Token Manager Properties
 * <p>
 * HttpSession TokenManager 的专属配置
 * <p>
 * 目前 HttpSession 实现不需要额外配置，此类预留用于未来扩展
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(SimpleAkskClientCoreConstant.CONFIG_PREFIX)
public class SimpleAkskHttpSessionTokenManagerProperties {
    // HttpSession 不需要额外配置，预留用于未来扩展
}
