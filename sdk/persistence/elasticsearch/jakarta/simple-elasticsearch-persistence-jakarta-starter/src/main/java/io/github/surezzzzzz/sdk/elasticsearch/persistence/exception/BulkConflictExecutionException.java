package io.github.surezzzzzz.sdk.elasticsearch.persistence.exception;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.BulkResult;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 双阶段补偿失败：保留 CREATE 结果、UPDATE 部分结果及原 item 下标映射。
 */
@Getter
public class BulkConflictExecutionException extends BulkPersistenceExecutionException {
    private static final long serialVersionUID = 1L;
    private final BulkResult compensationResult;
    private final List<Integer> originalItemIndices;

    /**
     * 保留第一阶段、补偿阶段及原 item 下标，避免丢失部分提交事实。
     */
    public BulkConflictExecutionException(BulkResult initial, BulkResult compensation, List<Integer> offsets, Throwable cause) {
        super(initial, cause);
        this.compensationResult = compensation;
        this.originalItemIndices = Collections.unmodifiableList(new ArrayList<>(offsets));
    }
}
