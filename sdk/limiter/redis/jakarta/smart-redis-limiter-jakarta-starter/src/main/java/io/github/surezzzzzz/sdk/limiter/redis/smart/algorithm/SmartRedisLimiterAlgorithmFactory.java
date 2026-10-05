package io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm;

/**
 * 算法工厂接口
 *
 * @author Sure.
 * @Date: 2026-05-08
 */
public interface SmartRedisLimiterAlgorithmFactory {

    /**
     * 根据算法名称获取算法实现
     *
     * @param algorithm 算法名称（fixed/sliding）
     * @return 算法实现
     */
    SmartRedisLimiterAlgorithm getAlgorithm(String algorithm);
}
