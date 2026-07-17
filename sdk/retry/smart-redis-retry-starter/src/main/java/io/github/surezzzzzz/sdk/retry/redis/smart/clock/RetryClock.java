package io.github.surezzzzzz.sdk.retry.redis.smart.clock;

/**
 * 重试时钟
 *
 * @author surezzzzzz
 */
public interface RetryClock {

    /**
     * 获取当前毫秒时间戳。
     *
     * @return 当前毫秒时间戳
     */
    long currentTimeMillis();
}
