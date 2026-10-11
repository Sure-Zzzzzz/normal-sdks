package io.github.surezzzzzz.sdk.iam.client.model;

import lombok.Builder;
import lombok.Value;

/**
 * 协作组成员投影（user-groups 族响应模型，1.3.7）。
 *
 * @author surezzzzzz
 */
@Value
@Builder
public class IamUserGroupMember {

    /**
     * 成员公开主体。
     */
    String subjectId;

    /**
     * 用户名。
     */
    String username;

    /**
     * 显示名（可空）。
     */
    String displayName;

    /**
     * 状态（1=启用）。
     */
    Integer status;
}
