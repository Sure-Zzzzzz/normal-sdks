package io.github.surezzzzzz.sdk.kms.core.exception;

import io.github.surezzzzzz.sdk.kms.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.kms.core.constant.ErrorMessage;

/**
 * KMS 授权策略冲突异常。
 *
 * <p>用于拒绝违反策略唯一性或并发撤销约束的管理变更，不暴露具体策略内容。</p>
 *
 * @author surezzzzzz
 */
public class KmsPolicyConflictException extends SmartKmsException {

    private static final long serialVersionUID = 1L;

    /**
     * 创建固定错误码和安全消息的策略冲突异常。
     */
    public KmsPolicyConflictException() {
        super(ErrorCode.POLICY_CONFLICT, ErrorMessage.POLICY_CONFLICT);
    }
}
