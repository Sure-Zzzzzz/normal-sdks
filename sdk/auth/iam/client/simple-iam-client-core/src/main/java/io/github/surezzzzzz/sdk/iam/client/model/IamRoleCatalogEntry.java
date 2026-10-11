package io.github.surezzzzzz.sdk.iam.client.model;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * 角色目录条目（role-catalog 族响应模型，1.3.7）。
 *
 * @author surezzzzzz
 */
@Value
@Builder
public class IamRoleCatalogEntry {

    /**
     * 角色数字 ID（挂角色操作用）。
     */
    Long id;

    /**
     * 角色编码。
     */
    String code;

    /**
     * 角色名称。
     */
    String name;

    /**
     * 描述（可空）。
     */
    String description;

    /**
     * 是否内置（1=内置）。
     */
    Integer builtIn;

    /**
     * 创建时间。
     */
    Instant createdAt;

    /**
     * 更新时间。
     */
    Instant updatedAt;
}
