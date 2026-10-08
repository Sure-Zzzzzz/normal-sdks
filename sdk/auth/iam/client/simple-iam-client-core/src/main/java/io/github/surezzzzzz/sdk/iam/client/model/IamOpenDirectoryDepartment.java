package io.github.surezzzzzz.sdk.iam.client.model;

import lombok.Builder;
import lombok.Value;

/**
 * 组织目录部门条目
 *
 * @author surezzzzzz
 */
@Value
@Builder
public class IamOpenDirectoryDepartment {

    /**
     * 部门 ID。
     */
    Long departmentId;

    /**
     * 部门编码。
     */
    String code;

    /**
     * 父部门 ID。
     */
    Long parentId;

    /**
     * 部门名称。
     */
    String name;

    /**
     * 状态。
     */
    Integer status;
}
