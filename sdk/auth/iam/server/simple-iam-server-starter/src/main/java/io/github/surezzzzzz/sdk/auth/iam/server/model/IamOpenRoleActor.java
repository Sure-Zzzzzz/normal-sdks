package io.github.surezzzzzz.sdk.auth.iam.server.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 事务开始前快照的真实操作者；不是请求体指定的角色所有者。
 *
 * @author surezzzzzz
 */
@Getter
@RequiredArgsConstructor
public final class IamOpenRoleActor {
    /**
     * 可信认证来源。
     */
    private final String sourceId;
    /**
     * 可信主体类型。
     */
    private final String subjectType;
    /**
     * 可信稳定主体。
     */
    private final String subjectId;
    /**
     * 请求关联标识。
     */
    private final String requestId;
}
