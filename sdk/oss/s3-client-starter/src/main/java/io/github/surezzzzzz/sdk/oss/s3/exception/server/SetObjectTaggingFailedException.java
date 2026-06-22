package io.github.surezzzzzz.sdk.oss.s3.exception.server;

import io.github.surezzzzzz.sdk.oss.s3.constant.ErrorCode;
import io.github.surezzzzzz.sdk.oss.s3.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.oss.s3.exception.base.S3ServerException;

/**
 * 设置对象标签失败异常
 */
public class SetObjectTaggingFailedException extends S3ServerException {

    /**
     * 构造设置对象标签失败异常
     *
     * @param message 错误信息
     */
    public SetObjectTaggingFailedException(String message) {
        super(ErrorCode.SET_OBJECT_TAGGING_FAILED, String.format(ErrorMessage.SET_OBJECT_TAGGING_FAILED, message));
    }

    /**
     * 构造设置对象标签失败异常
     *
     * @param message 错误信息
     * @param cause   原始异常
     */
    public SetObjectTaggingFailedException(String message, Throwable cause) {
        super(ErrorCode.SET_OBJECT_TAGGING_FAILED, String.format(ErrorMessage.SET_OBJECT_TAGGING_FAILED, message), cause);
    }
}