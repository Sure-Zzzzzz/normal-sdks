package io.github.surezzzzzz.sdk.oss.s3.exception.server;

import io.github.surezzzzzz.sdk.oss.s3.constant.ErrorCode;
import io.github.surezzzzzz.sdk.oss.s3.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.oss.s3.exception.base.S3ServerException;

/**
 * 删除对象标签失败异常
 */
public class DeleteObjectTaggingFailedException extends S3ServerException {

    /**
     * 构造删除对象标签失败异常
     *
     * @param message 错误信息
     */
    public DeleteObjectTaggingFailedException(String message) {
        super(ErrorCode.DELETE_OBJECT_TAGGING_FAILED, String.format(ErrorMessage.DELETE_OBJECT_TAGGING_FAILED, message));
    }

    /**
     * 构造删除对象标签失败异常
     *
     * @param message 错误信息
     * @param cause   原始异常
     */
    public DeleteObjectTaggingFailedException(String message, Throwable cause) {
        super(ErrorCode.DELETE_OBJECT_TAGGING_FAILED, String.format(ErrorMessage.DELETE_OBJECT_TAGGING_FAILED, message), cause);
    }
}
