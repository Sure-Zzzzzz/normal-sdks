package io.github.surezzzzzz.sdk.kms.core.exception;

import io.github.surezzzzzz.sdk.kms.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.kms.core.constant.ErrorMessage;

/**
 * KMS 幂等摘要冲突异常。
 *
 * <p>同一幂等作用域已存在记录但请求摘要不同，调用方必须使用新的幂等键。</p>
 *
 * @author surezzzzzz
 */
public class KmsIdempotencyConflictException extends SmartKmsException {

    private static final long serialVersionUID = 1L;

    /**
     * 创建固定错误码和安全消息的幂等冲突异常。
     */
    public KmsIdempotencyConflictException() {
        super(ErrorCode.IDEMPOTENCY_CONFLICT, ErrorMessage.IDEMPOTENCY_CONFLICT);
    }
}
