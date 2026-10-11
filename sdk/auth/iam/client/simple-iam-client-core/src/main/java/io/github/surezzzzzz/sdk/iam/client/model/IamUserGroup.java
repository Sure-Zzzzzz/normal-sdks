package io.github.surezzzzzz.sdk.iam.client.model;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * 协作组投影（user-groups 族响应模型，1.3.7）。
 *
 * @author surezzzzzz
 */
@Value
@Builder
public class IamUserGroup {

    /**
     * 组 ID。
     */
    Long id;

    /**
     * 组编码（唯一）。
     */
    String code;

    /**
     * 组名称。
     */
    String name;

    /**
     * 描述（可空）。
     */
    String description;

    /**
     * 状态（1=启用）。
     */
    Integer status;

    /**
     * 创建时间。
     */
    Instant createdAt;

    /**
     * 更新时间。
     */
    Instant updatedAt;
}
