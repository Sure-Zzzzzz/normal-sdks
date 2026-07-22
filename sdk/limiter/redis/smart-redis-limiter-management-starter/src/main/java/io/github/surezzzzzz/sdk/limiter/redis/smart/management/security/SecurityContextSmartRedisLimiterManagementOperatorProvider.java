package io.github.surezzzzzz.sdk.limiter.redis.smart.management.security;

import io.github.surezzzzzz.sdk.limiter.redis.smart.management.configuration.SmartRedisLimiterManagementProperties;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 基于安全上下文和配置管理员的操作人 Provider
 *
 * @author surezzzzzz
 */
public class SecurityContextSmartRedisLimiterManagementOperatorProvider
        implements SmartRedisLimiterManagementOperatorProvider {

    private final SmartRedisLimiterManagementProperties properties;

    /**
     * 构造操作人 Provider
     *
     * @param properties management 配置
     */
    public SecurityContextSmartRedisLimiterManagementOperatorProvider(
            SmartRedisLimiterManagementProperties properties) {
        this.properties = properties;
    }

    @Override
    public String getOperator() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getName() != null) {
            return authentication.getName();
        }
        return properties.getAdmin().getUsername();
    }
}
