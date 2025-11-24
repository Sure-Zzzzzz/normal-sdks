package io.github.surezzzzzz.sdk.retry.redis.exception;

/**
 * 重试记录未找到异常
 */
public class RetryRecordNotFoundException extends RedisRetryException {
    public RetryRecordNotFoundException(String key) {
        super("重试记录未找到: " + key);
    }
}