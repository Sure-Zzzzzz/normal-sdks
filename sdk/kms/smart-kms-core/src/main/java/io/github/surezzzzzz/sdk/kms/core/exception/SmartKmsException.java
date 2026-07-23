package io.github.surezzzzzz.sdk.kms.core.exception;

import lombok.Getter;

/**
 * KMS 基础异常。
 *
 * <p>只暴露稳定错误码与安全中文消息，不支持携带底层异常原因，避免驱动、Provider 或敏感数据通过异常链泄露。</p>
 *
 * @author surezzzzzz
 */
@Getter
public class SmartKmsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 可供调用方稳定处理的 KMS 错误码。
     */
    private final String errorCode;

    /**
     * 创建不包含异常原因链的 KMS 异常。
     *
     * @param errorCode 稳定错误码
     * @param message   安全错误消息
     */
    public SmartKmsException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
