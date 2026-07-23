package io.github.surezzzzzz.sdk.kms.core.exception;

import io.github.surezzzzzz.sdk.kms.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.kms.core.constant.ErrorMessage;

/**
 * KMS 资源不存在异常。
 *
 * <p>仅在调用方已通过相应资源可见性校验的管理路径返回；普通密码学路径应使用统一安全错误语义。</p>
 *
 * @author surezzzzzz
 */
public class KmsNotFoundException extends SmartKmsException {

    private static final long serialVersionUID = 1L;

    /**
     * 创建固定错误码和安全消息的资源不存在异常。
     */
    public KmsNotFoundException() {
        super(ErrorCode.RESOURCE_NOT_FOUND, ErrorMessage.RESOURCE_NOT_FOUND);
    }
}
