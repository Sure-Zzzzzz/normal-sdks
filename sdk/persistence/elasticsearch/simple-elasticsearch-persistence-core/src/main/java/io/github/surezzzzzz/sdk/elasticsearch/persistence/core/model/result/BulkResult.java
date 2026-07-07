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

    private boolean success;
    private boolean hasFailure;
    private int total;
    private int succeeded;
    private int failed;
    private String datasource;
    private long tookMs;
    private List<BulkItemFailure> failureList;
}
