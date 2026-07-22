package io.github.surezzzzzz.sdk.limiter.redis.smart.management.security;

/**
 * 管理操作人提供接口
 *
 * @author surezzzzzz
 */
public interface SmartRedisLimiterManagementOperatorProvider {

    /**
     * 获取当前管理操作人
     *
     * @return 管理操作人标识
     */
    String getOperator();
}
