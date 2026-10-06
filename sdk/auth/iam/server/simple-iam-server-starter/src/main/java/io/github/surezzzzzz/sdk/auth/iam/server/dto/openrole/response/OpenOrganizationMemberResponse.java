package io.github.surezzzzzz.sdk.auth.iam.server.dto.openrole.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 指定成员的最小组织事实，不返回个人资料或全量角色。
 *
 * @author surezzzzzz
 */
@Getter
@AllArgsConstructor
public class OpenOrganizationMemberResponse {
    /**
     * 可信人员主体。
     */
    private final String subjectId;
    /**
     * 当前状态。
     */
    private final Integer status;
    /**
     * 当前直属部门。
     */
    private final Long departmentId;
    /**
     * 成功返回仅表示位于批准子树，停用状态仍由调用方拒绝。
     */
    private final boolean inCurrentRoot;
}
