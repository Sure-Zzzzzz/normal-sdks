package io.github.surezzzzzz.sdk.auth.iam.server.dto.openrole.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * 在同一委托读锁下获得的部门分页和修改版本。
 *
 * @author surezzzzzz
 */
@Getter
@RequiredArgsConstructor
public class OpenDepartmentRolePageResponse {
    /**
     * 当前受委托角色版本。
     */
    private final long revision;
    /**
     * 本页受授权关系。
     */
    private final List<OpenDepartmentRoleResponse> items;
    /**
     * 受授权关系总数。
     */
    private final long total;
    /**
     * 从一开始的页码。
     */
    private final int page;
    /**
     * 每页容量。
     */
    private final int size;
}
