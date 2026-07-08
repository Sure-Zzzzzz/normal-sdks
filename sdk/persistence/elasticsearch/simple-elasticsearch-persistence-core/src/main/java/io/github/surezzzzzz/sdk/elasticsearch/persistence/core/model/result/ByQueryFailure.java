package io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * By Query Failure
 *
 * @author surezzzzzz
 */
@Data
@Builder
public class ByQueryFailure implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 失败索引。 */
    private String index;
    /** 文档 ID。 */
    private String id;
    /** 失败原因。 */
    private String cause;
    /** 失败状态。 */
    private String status;
}
