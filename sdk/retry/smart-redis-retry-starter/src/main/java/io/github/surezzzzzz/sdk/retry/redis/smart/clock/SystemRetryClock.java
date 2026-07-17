package io.github.surezzzzzz.sdk.retry.redis.smart.clock;

/**
 * 系统重试时钟
 *
 * @author surezzzzzz
 */
public class SystemRetryClock implements RetryClock {

    /**
     * 获取当前系统毫秒时间戳。
     *
     * @return 当前系统毫秒时间戳
     */
    @Override
    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }
}
