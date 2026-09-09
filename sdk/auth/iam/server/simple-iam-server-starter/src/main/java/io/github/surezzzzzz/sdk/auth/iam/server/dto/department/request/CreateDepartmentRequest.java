package io.github.surezzzzzz.sdk.auth.iam.server.dto.department.request;

import lombok.Data;

import java.util.List;

/**
 * 创建部门请求
 *
 * @author surezzzzzz
 */
@Data
public class CreateDepartmentRequest {

    private String code;

    private String name;

    private Long parentId;

    private Integer sortOrder;

    private Integer status;

    /**
     * 创建时同步绑定的已有成员用户 ID 列表（同一事务内挂到新部门）
     */
    private List<Long> memberIds;
}
