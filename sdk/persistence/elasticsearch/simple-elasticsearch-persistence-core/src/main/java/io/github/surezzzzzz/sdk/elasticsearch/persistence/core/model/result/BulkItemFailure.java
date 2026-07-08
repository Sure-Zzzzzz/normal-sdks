package io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.BulkItemType;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * Bulk Item Failure
 *
 * @author surezzzzzz
 */
@Data
@Builder
public class BulkItemFailure implements Serializable {

    private static final long serialVersionUID = 1L;

    /** item 下标。 */
    private int itemIndex;
    /** item 操作类型。 */
    private BulkItemType type;
    /** 文档 ID。 */
    private String id;
    /** 目标索引。 */
    private String index;
    /** 数据源 key。 */
    private String datasource;
    /** SDK 错误码。 */
    private String errorCode;
    /** 错误信息。 */
    private String errorMessage;
    /** ES 返回的 HTTP 状态码。 */
    private Integer status;
    /** ES 错误类型。 */
    private String errorType;
    /** ES 错误原因。 */
    private String errorReason;
    /** 是否建议重试。 */
    private Boolean retryable;
}
