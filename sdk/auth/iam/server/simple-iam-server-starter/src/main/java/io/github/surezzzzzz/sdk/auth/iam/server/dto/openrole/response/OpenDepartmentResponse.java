package io.github.surezzzzzz.sdk.auth.iam.server.dto.openrole.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 目录接口允许公开的最小部门事实。
 *
 * @author surezzzzzz
 */
@Getter
@AllArgsConstructor
public class OpenDepartmentResponse {
    /**
     * 部门编号。
     */
    private final Long departmentId;
    /**
     * 部门编码。
     */
    private final String code;
    /**
     * 当前直接父节点。
     */
    private final Long parentId;
    /**
     * 展示名称。
     */
    private final String name;
    /**
     * 当前状态。
     */
    private final Integer status;
}
