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

    private Boolean docAsUpsert;
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
}
