package io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * Update Options
 *
 * @author surezzzzzz
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class UpdateOptions extends WriteOptions {

    /**
     * 文档不存在时是否将 doc 作为初始内容插入。
     */
    private Boolean docAsUpsert;
    /**
     * 是否返回 source。
     */
    private Boolean fetchSource;
    /**
     * 文档不存在时的兜底初始化内容（Map 或 @Document 实体均可）。
     * 配合 scriptedUpsert=true 使用：脚本负责写字段，upsertDoc 仅用于在文档不存在时触发脚本执行。
     */
    private Object upsertDoc;
    /**
     * true 时无论文档是否存在都执行脚本（scripted_upsert）；false/null 时仅已存在文档才执行脚本。
     */
    private Boolean scriptedUpsert;
    /**
     * 版本冲突时的 ES 端重试次数。
     */
    private Integer retryOnConflict;
    /**
     * 是否启用 ES detect_noop。
     */
    private Boolean detectNoop;
}
