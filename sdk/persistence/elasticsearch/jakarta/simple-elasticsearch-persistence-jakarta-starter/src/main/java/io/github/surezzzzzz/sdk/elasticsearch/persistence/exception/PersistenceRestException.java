package io.github.surezzzzzz.sdk.elasticsearch.persistence.exception;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorCode;
import lombok.Getter;

/**
 * 脱敏的 REST 拒绝，保留状态码供冲突补偿判定。
 */
@Getter
public class PersistenceRestException extends PersistenceExecutionException {
    private final int status;
    private final boolean documentMissing;

    /**
     * 保留 HTTP 状态和可确认的文档不存在标识。
     */
    public PersistenceRestException(int status) {
        this(status, false);
    }

    /**
     * 保留 HTTP 状态和可确认的文档不存在标识。
     */
    public PersistenceRestException(int status, boolean documentMissing) {
        super(ErrorCode.EXECUTION_FAILED, ErrorMessage.EXECUTION_FAILED);
        this.status = status;
        this.documentMissing = documentMissing;
    }
}
