package io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.PersistenceOperationType;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * Persistence Result
 *
 * @author surezzzzzz
 */
@Data
@Builder
public class PersistenceResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 是否成功。
     */
    private boolean success;
    /**
     * 文档 ID。
     */
    private String id;
    /**
     * 实际索引。
     */
    private String index;
    /**
     * 数据源 key。
     */
    private String datasource;
    /**
     * 操作类型。
     */
    private PersistenceOperationType operationType;
    /**
     * 是否被 route async-write 接管。
     */
    private boolean asyncRouted;
    /**
     * 耗时（毫秒）。
     */
    private long tookMs;
    /**
     * ES 写入结果。
     */
    private String result;
}
