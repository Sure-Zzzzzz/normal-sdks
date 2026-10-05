package io.github.surezzzzzz.sdk.elasticsearch.persistence.model.request;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.PersistenceRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * 原数据源上的服务端任务查询请求
 *
 * @author surezzzzzz
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class TaskQueryRequest extends PersistenceRequest {

    private static final long serialVersionUID = 1L;

    /**
     * 服务端任务所属的原数据源键。
     */
    private String datasource;
    /**
     * ES 返回的节点与任务编号，不能跨数据源广播。
     */
    private String taskId;
}
