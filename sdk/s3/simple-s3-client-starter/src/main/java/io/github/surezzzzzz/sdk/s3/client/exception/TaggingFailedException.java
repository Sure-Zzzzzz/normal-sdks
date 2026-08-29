package io.github.surezzzzzz.sdk.s3.client.exception;

import io.github.surezzzzzz.sdk.s3.client.constant.ErrorCode;

/**
 * 对象标签操作失败（设置/获取/删除与参数校验）。
 *
 * @author surezzzzzz
 */
public class TaggingFailedException extends S3ClientException {

    /**
     * 以错误码与受控消息创建异常。
     *
     * @param message 受控错误消息
     */
    public TaggingFailedException(String message) {
        super(ErrorCode.TAGGING_FAILED, message);
    }

    /**
     * 以错误码、受控消息与原因创建异常。
     *
     * @param message 受控错误消息
     * @param cause   原因异常
     */
    public TaggingFailedException(String message, Throwable cause) {
        super(ErrorCode.TAGGING_FAILED, message, cause);
    }
}
