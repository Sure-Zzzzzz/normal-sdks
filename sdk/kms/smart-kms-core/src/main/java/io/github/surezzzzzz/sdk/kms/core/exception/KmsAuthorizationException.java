package io.github.surezzzzzz.sdk.kms.core.exception;

import io.github.surezzzzzz.sdk.kms.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.kms.core.constant.ErrorMessage;

/**
 * KMS 授权拒绝异常。
 *
 * <p>不暴露资源是否存在、策略是否匹配或 scope 缺失等授权决策细节。</p>
 *
 * @author surezzzzzz
 */
public class KmsAuthorizationException extends SmartKmsException {

    private static final long serialVersionUID = 1L;

    /**
     * 创建固定错误码和安全消息的授权拒绝异常。
     */
    public KmsAuthorizationException() {
        super(ErrorCode.AUTHORIZATION_DENIED, ErrorMessage.AUTHORIZATION_DENIED);
    }
}
