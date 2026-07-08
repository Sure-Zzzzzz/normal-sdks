package io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * Bulk Result
 *
 * @author surezzzzzz
 */
@Data
@Builder
public class BulkResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否整体成功。 */
    private boolean success;
    /** 是否存在 item 失败。 */
    private boolean hasFailure;
    /** 总 item 数。 */
    private int total;
    /** 成功 item 数。 */
    private int succeeded;
    /** 失败 item 数。 */
    private int failed;
    /** 数据源 key。 */
    private String datasource;
    /** 耗时（毫秒）。 */
    private long tookMs;
    /** item 失败明细。 */
    private List<BulkItemFailure> failureList;
    /** 已提交的批次数。 */
    private Integer batchTotal;
    /** 无 item 失败的批次数。 */
    private Integer batchSucceeded;
    /** 存在 item 失败的批次数。 */
    private Integer batchFailed;
    /** 是否因失败批次停止后续提交。 */
    private Boolean stoppedOnFailure;
    /** 是否出现部分批次已提交后的请求级异常。 */
    private Boolean partial;
}
