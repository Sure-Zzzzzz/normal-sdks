package io.github.surezzzzzz.sdk.retry.redis.exception;

import io.github.surezzzzzz.sdk.retry.redis.constant.ErrorCode;
import io.github.surezzzzzz.sdk.retry.redis.constant.ErrorMessage;

/**
 * 重试记录未找到异常
 *
 * @author surezzzzzz
 */
public class RetryRecordNotFoundException extends RedisRetryException {

    private static final long serialVersionUID = 1L;

    public RetryRecordNotFoundException(String key) {
        super(ErrorCode.RETRY_OPERATION_FAILED, String.format(ErrorMessage.RETRY_RECORD_NOT_FOUND, key));
    }
}
