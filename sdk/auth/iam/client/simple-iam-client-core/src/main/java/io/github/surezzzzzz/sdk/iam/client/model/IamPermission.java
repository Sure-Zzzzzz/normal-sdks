package io.github.surezzzzzz.sdk.iam.client.model;

import lombok.Builder;
import lombok.Value;

/**
 * 权限清单投影（permissions 族响应模型，1.3.7）。
 *
 * @author surezzzzzz
 */
@Value
@Builder
public class IamPermission {

    /**
     * 权限编码（如 iam:user:api）。
     */
    String code;

    /**
     * 名称。
     */
    String name;

    /**
     * 描述（可空）。
     */
    String description;

    /**
     * 类型（page/api/data）。
     */
    String type;

    /**
     * 是否内置（1=内置）。
     */
    Integer builtIn;
}
