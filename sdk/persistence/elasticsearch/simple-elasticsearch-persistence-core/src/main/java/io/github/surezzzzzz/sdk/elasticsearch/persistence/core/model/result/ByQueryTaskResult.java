package io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * By Query Task Result
 *
 * @author surezzzzzz
 */
@Data
@Builder
public class ByQueryTaskResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否已完成。 */
    private boolean completed;
    /** 服务端异步任务 ID。 */
    private String taskId;
    /** 数据源 key。 */
    private String datasource;
    /** 目标索引。 */
    private String index;
    /** 总处理数。 */
    private long total;
    /** 更新数量。 */
    private long updated;
    /** 删除数量。 */
    private long deleted;
    /** 版本冲突数量。 */
    private long versionConflicts;
    /** 耗时（毫秒）。 */
    private long tookMs;
    /** 失败明细。 */
    private List<ByQueryFailure> failureList;
}
