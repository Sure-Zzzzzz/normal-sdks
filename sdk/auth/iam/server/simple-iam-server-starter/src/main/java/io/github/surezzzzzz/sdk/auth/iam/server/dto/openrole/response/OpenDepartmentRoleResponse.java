package io.github.surezzzzzz.sdk.auth.iam.server.dto.openrole.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

/**
 * 本角色的部门关系；迁出后不暴露外部部门详情。
 *
 * @author surezzzzzz
 */
@Getter
@AllArgsConstructor
public class OpenDepartmentRoleResponse {
    /**
     * 关系目标编号。
     */
    private final Long departmentId;
    /**
     * 是否仍位于固定根。
     */
    private final boolean inCurrentRoot;
    /**
     * 关系创建时间。
     */
    private final Instant createdAt;
}
