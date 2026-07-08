package io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.query;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * Persistence Query
 *
 * @author surezzzzzz
 */
@Data
@Builder
public class PersistenceQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 原始 JSON 查询 DSL。 */
    private String rawJson;
    /** term 查询条件。 */
    private Map<String, Object> termMap;
    /** range 查询条件。 */
    private Map<String, Object> rangeMap;
}
