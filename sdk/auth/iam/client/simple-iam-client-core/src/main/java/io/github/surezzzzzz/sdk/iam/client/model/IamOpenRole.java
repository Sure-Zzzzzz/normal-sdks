package io.github.surezzzzzz.sdk.iam.client.model;

import lombok.Builder;
import lombok.Value;

/**
 * 受委托角色投影（含 revision——If-Match 条件值的来源）
 *
 * @author surezzzzzz
 */
@Value
@Builder
public class IamOpenRole {

    /**
     * 对外稳定角色 UUID。
     */
    String openRoleId;

    /**
     * 调用方创建幂等 UUID。
     */
    String externalId;

    /**
     * 角色编码。
     */
    String code;

    /**
     * 固定目标应用 ID。
     */
    Long applicationId;

    /**
     * 固定授权部门根 ID。
     */
    Long rootDepartmentId;

    /**
     * 角色名。
     */
    String name;

    /**
     * 描述。
     */
    String description;

    /**
     * 单调修改版本（If-Match 值来源：open-role:<openRoleId>:<revision>）。
     */
    long revision;

    /**
     * 状态：ACTIVE 有效，DELETED 已删除。
     */
    String state;

    /**
     * 是否已设置规则。
     */
    Boolean rulePresent;

    /**
     * 绑定信息（普通角色为 null）。
     */
    IamOpenRoleBinding binding;
}
