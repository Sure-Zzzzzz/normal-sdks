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

    private String index;
    private String id;
    private String cause;
    private String status;
}
