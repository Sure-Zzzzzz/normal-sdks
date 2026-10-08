package io.github.surezzzzzz.sdk.iam.client.model;

import lombok.Builder;
import lombok.Value;

/**
 * IAM 部门投影（departments 族响应模型）
 *
 * @author surezzzzzz
 */
@Value
@Builder
public class IamDepartment {

    /**
     * 部门 ID。
     */
    Long id;

    /**
     * 部门编码。
     */
    String code;

    /**
     * 部门名称。
     */
    String name;

    /**
     * 父部门 ID（null=根部门）。
     */
    Long parentId;

    /**
     * 完整路径。
     */
    String fullPath;

    /**
     * 排序号。
     */
    Integer sortOrder;

    /**
     * 状态。
     */
    Integer status;
}
