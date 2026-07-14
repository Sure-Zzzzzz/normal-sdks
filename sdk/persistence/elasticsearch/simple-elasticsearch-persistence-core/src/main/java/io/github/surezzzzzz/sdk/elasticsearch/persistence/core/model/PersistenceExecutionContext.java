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

    /**
     * 操作类型。
     */
    private PersistenceOperationType operationType;
    /**
     * 目标索引。
     */
    private String index;
    /**
     * 数据源 key。
     */
    private String datasource;
    /**
     * 是否为客户端异步。
     */
    private boolean clientAsync;
    /**
     * 是否被 route async-write 接管。
     */
    private boolean routeAsyncWrite;
    /**
     * 是否为服务端异步任务。
     */
    private boolean serverAsyncTask;
    /**
     * 服务端异步任务 ID。
     */
    private String taskId;
    /**
     * 开始时间戳（毫秒）。
     */
    private long startTimeMs;
    /**
     * 耗时（毫秒）。
     */
    private long tookMs;
}
