package io.github.surezzzzzz.sdk.kms.core.exception;

import io.github.surezzzzzz.sdk.kms.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.kms.core.constant.ErrorMessage;

/**
 * KMS 持久化异常。
 *
 * <p>不携带数据库、SQL 或驱动异常链；敏感操作中的审计持久化失败必须被上层转换为失败关闭。</p>
 *
 * @author surezzzzzz
 */
public class KmsPersistenceException extends SmartKmsException {

    private static final long serialVersionUID = 1L;

    /**
     * 创建固定错误码和安全消息的持久化异常。
     */
    public KmsPersistenceException() {
        super(ErrorCode.PERSISTENCE_FAILED, ErrorMessage.PERSISTENCE_FAILED);
    }
}
