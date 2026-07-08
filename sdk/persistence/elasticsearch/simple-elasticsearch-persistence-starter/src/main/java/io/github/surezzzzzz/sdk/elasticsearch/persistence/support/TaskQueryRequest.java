package io.github.surezzzzzz.sdk.elasticsearch.persistence.support;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.PersistenceRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * Task Query Request
 *
 * @author surezzzzzz
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class TaskQueryRequest extends PersistenceRequest {

    private static final long serialVersionUID = 1L;

    private String datasource;
    private String taskId;
}
