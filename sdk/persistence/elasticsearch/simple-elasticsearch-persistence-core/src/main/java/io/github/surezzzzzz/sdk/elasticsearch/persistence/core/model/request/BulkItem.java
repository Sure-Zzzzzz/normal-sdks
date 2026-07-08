package io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.BulkItemType;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * Bulk Item
 *
 * @author surezzzzzz
 */
@Data
@Builder
public class BulkItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Bulk item 操作类型。 */
    private BulkItemType type;
    /** 写入文档。 */
    private Object document;
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
    /** 文档不存在时是否将 doc 作为初始内容插入。 */
    private Boolean docAsUpsert;
    /** 路由值。 */
    private String routing;
    /** 当前 item 写入时使用的 ingest pipeline。 */
    private String pipeline;
    /** Bulk update item 版本冲突时的 ES 端重试次数。 */
    private Integer retryOnConflict;
    /** Bulk update item 是否启用 ES detect_noop。 */
    private Boolean detectNoop;
    /** Bulk update item 文档不存在时的兜底初始化内容。 */
    private Object upsertDoc;
    /** Bulk update item 是否启用 scripted_upsert。 */
    private Boolean scriptedUpsert;
}
