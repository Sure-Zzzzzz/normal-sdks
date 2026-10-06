package io.github.surezzzzzz.sdk.auth.iam.server.dto.openrole.response;

import lombok.Builder;
import lombok.Getter;

/**
 * 管理台角色详情的只读委托摘要，不包含归属编辑入口。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class OpenRoleBindingResponse {
    /**
     * 对外稳定 UUID。
     */
    private final String openRoleId;
    /**
     * 固定应用。
     */
    private final Long applicationId;
    /**
     * 固定部门根。
     */
    private final Long rootDepartmentId;
    /**
     * 当前版本。
     */
    private final long revision;
    /**
     * 委托状态。
     */
    private final String state;
}
