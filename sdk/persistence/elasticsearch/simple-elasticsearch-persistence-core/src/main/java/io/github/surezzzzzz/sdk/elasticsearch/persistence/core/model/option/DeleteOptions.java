package io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * Delete Options
 *
 * @author surezzzzzz
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class DeleteOptions extends WriteOptions {

    /** 版本号。 */
    private Long version;
    /** 版本类型。 */
    private String versionType;
    /** 删除目标不存在时是否视为成功。 */
    private Boolean notFoundAsSuccess;
}
