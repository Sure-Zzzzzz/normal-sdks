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

    private boolean completed;
    private String taskId;
    private String datasource;
    private String index;
    private long total;
    private long updated;
    private long deleted;
    private long versionConflicts;
    private long tookMs;
    private List<ByQueryFailure> failureList;
}
