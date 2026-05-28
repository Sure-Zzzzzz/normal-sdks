package io.github.surezzzzzz.sdk.template.doc.exception;

import io.github.surezzzzzz.sdk.template.doc.constant.ErrorCode;
import io.github.surezzzzzz.sdk.template.doc.constant.ErrorMessage;
import lombok.Getter;

/**
 * Condition Block Exception - 条件块处理异常
 *
 * @author surezzzzzz
 */
@Getter
public class ConditionBlockException extends SimpleDocTemplateException {

    private static final long serialVersionUID = 1L;

    public ConditionBlockException(String errorCode, String message) {
        super(errorCode, message);
    }

    public ConditionBlockException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    /**
     * 条件块标记不匹配（start/end key 不一致）
     */
    public static ConditionBlockException mismatch(String prefix, String startKey, String endKey) {
        return new ConditionBlockException(
            ErrorCode.CONDITION_BLOCK_MISMATCH,
            String.format(ErrorMessage.CONDITION_BLOCK_MISMATCH, prefix, startKey, prefix, endKey));
    }

    /**
     * 条件块嵌套（不允许嵌套）
     */
    public static ConditionBlockException nested(String key) {
        return new ConditionBlockException(
            ErrorCode.CONDITION_BLOCK_NESTED,
            String.format(ErrorMessage.CONDITION_BLOCK_NESTED, key));
    }

    /**
     * 条件块处理失败（DOCX 解析异常等）
     */
    public static ConditionBlockException processFailed(String message, Throwable cause) {
        return new ConditionBlockException(
            ErrorCode.CONDITION_PROCESS_FAILED,
            String.format(ErrorMessage.CONDITION_PROCESS_FAILED, message), cause);
    }
}