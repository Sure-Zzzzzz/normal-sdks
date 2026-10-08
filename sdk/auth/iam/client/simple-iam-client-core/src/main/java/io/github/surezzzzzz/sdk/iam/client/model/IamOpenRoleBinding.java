package io.github.surezzzzzz.sdk.iam.client.model;

import lombok.Builder;
import lombok.Value;

/**
 * 受委托角色绑定摘要
 *
 * @author surezzzzzz
 */
@Value
@Builder
public class IamOpenRoleBinding {

    /**
     * 对外稳定角色 UUID。
     */
    String openRoleId;

    /**
     * 目标应用 ID。
     */
    Long applicationId;

    /**
     * 授权部门根 ID。
     */
    Long rootDepartmentId;

    /**
     * 修订版本。
     */
    long revision;

    /**
     * 状态。
     */
    String state;
}
