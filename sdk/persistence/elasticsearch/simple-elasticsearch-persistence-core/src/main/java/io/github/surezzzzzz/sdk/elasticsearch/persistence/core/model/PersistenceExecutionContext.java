package io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.PersistenceOperationType;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * Persistence Execution Context
 *
 * @author surezzzzzz
 */
@Data
@Builder
public class PersistenceExecutionContext implements Serializable {

    private static final long serialVersionUID = 1L;

    private PersistenceOperationType operationType;
    private String index;
    private String datasource;
    private boolean clientAsync;
    private boolean routeAsyncWrite;
    private boolean serverAsyncTask;
    private String taskId;
    private long startTimeMs;
    private long tookMs;
}
