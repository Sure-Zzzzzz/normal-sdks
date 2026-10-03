package io.github.surezzzzzz.sdk.s3.client.exception;

import io.github.surezzzzzz.sdk.s3.client.constant.ErrorCode;

/**
 * STS 临时凭证获取失败（含 target 配置不符）。
 *
 * @author surezzzzzz
 */
public class StsCredentialsFailedException extends S3ClientException {

    /**
     * 以错误码与受控消息创建异常。
     *
     * @param message 受控错误消息
     */
    public StsCredentialsFailedException(String message) {
        super(ErrorCode.STS_CREDENTIALS_FAILED, message);
    }

    /**
     * 以错误码、受控消息与原因创建异常。
     *
     * @param message 受控错误消息
     * @param cause   原因异常
     */
    public StsCredentialsFailedException(String message, Throwable cause) {
        super(ErrorCode.STS_CREDENTIALS_FAILED, message, cause);
    }
}
