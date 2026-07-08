package io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.UpdateOptions;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/**
 * Update Request
 *
 * @author surezzzzzz
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class UpdateRequest extends PersistenceRequest {

    private static final long serialVersionUID = 1L;

    /** 目标索引。 */
    private String index;
    /** 文档 ID。 */
    private String id;
    /** 局部更新字段。 */
    private Map<String, Object> fieldMap;
    /** Painless 脚本。 */
    private String scriptSource;
    /** 脚本参数。 */
    private Map<String, Object> scriptParamMap;
    /** Update 写入选项。 */
    private UpdateOptions options;
}
