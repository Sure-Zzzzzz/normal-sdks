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

    private boolean success;
    private String id;
    private String index;
    private String datasource;
    private PersistenceOperationType operationType;
    private boolean asyncRouted;
    private long tookMs;
}
