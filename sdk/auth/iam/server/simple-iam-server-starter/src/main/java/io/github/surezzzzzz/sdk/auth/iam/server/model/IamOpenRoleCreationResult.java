package io.github.surezzzzzz.sdk.auth.iam.server.model;

import io.github.surezzzzzz.sdk.auth.iam.server.dto.openrole.response.OpenRoleResponse;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 区分首次创建与幂等回读，以正确表达 HTTP 201/200。
 *
 * @author surezzzzzz
 */
@Getter
@RequiredArgsConstructor
public final class IamOpenRoleCreationResult {
    /**
     * 当前权威角色事实。
     */
    private final OpenRoleResponse role;
    /**
     * 本次是否首次提交创建。
     */
    private final boolean created;
}
