package io.github.surezzzzzz.sdk.kms.core.exception;

import io.github.surezzzzzz.sdk.kms.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.kms.core.constant.ErrorMessage;

/**
 * KMS 服务不可用异常。
 *
 * <p>用于向调用方安全归并基础设施不可用或审计写入失败等应当失败关闭的情形。</p>
 *
 * @author surezzzzzz
 */
public class KmsServiceUnavailableException extends SmartKmsException {

    private static final long serialVersionUID = 1L;

    /**
     * 创建固定错误码和安全消息的服务不可用异常。
     */
    public KmsServiceUnavailableException() {
        super(ErrorCode.SERVICE_UNAVAILABLE, ErrorMessage.SERVICE_UNAVAILABLE);
    }
}
