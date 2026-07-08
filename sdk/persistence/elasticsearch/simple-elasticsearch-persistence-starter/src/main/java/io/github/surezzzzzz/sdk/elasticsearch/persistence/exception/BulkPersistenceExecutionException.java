package io.github.surezzzzzz.sdk.elasticsearch.persistence.exception;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.SimpleElasticsearchPersistenceCoreConstant;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.BulkResult;
import lombok.Getter;

/**
 * Bulk Persistence Execution Exception
 *
 * <p>分批 bulk 已有部分批次成功提交后，后续批次发生请求级异常时抛出，
 * 携带已聚合的 partial {@link BulkResult}。
 *
 * @author surezzzzzz
 */
@Getter
public class BulkPersistenceExecutionException extends PersistenceExecutionException {

    private static final long serialVersionUID = 1L;

    private final BulkResult partialResult;

    public BulkPersistenceExecutionException(BulkResult partialResult, Throwable cause) {
        super(ErrorCode.EXECUTION_FAILED,
                String.format(ErrorMessage.EXECUTION_FAILED,
                        SimpleElasticsearchPersistenceCoreConstant.BULK_PARTIAL_EXECUTION_FAILED),
                cause);
        this.partialResult = partialResult;
    }
}
