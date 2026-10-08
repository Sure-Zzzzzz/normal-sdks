package io.github.surezzzzzz.sdk.iam.client.model;

import lombok.Builder;
import lombok.Value;

/**
 * 受委托角色-部门挂载条目
 *
 * @author surezzzzzz
 */
@Value
@Builder
public class IamOpenRoleDepartment {

    /**
     * 部门 ID。
     */
    Long departmentId;

    /**
     * 是否在当前授权根内。
     */
    boolean inCurrentRoot;

    /**
     * 挂载时间（ISO-8601 原文）。
     */
    String createdAt;
}
