package io.github.surezzzzzz.sdk.limiter.redis.smart.typed;

import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterContext;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterDataDimension;

/**
 * 类型化维度事实提供方 SPI
 * <p>宿主实现本接口为身份维度（USER/SERVICE/CREDENTIAL/CUSTOMER）提供已验证的稳定计数事实；
 * 实现类必须是单例并线程安全。每个维度只允许一个提供方，重复声明启动拒绝。
 * IP 与 RESOURCE 维度不使用本接口：IP 取限流上下文中经可信入口清洗的数字地址，
 * RESOURCE 使用固定共享对象。</p>
 * <p>实现不得调用限流运行端内部组件；提供方抛出的未分类异常按不可用处理，
 * 不得被猜测为匿名身份。事实必须绑定当前已验证请求身份，跨请求复用即实现错误。</p>
 *
 * @author surezzzzzz
 */
public interface SmartRedisLimiterTypedFactProvider {

    /**
     * 声明本提供方负责的计数维度
     *
     * @return 计数维度
     */
    SmartRedisLimiterDataDimension dimension();

    /**
     * 为当前请求提供该维度的事实
     *
     * @param context 限流上下文（含请求标识与已解析的客户端地址等）
     * @return 维度事实；返回 null 按不可用处理
     */
    SmartRedisLimiterTypedDimensionFact provide(SmartRedisLimiterContext context);
}
