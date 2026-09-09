package io.github.surezzzzzz.sdk.auth.iam.server.dto.department.request;

import lombok.Data;

/**
 * 更新部门请求
 *
 * @author surezzzzzz
 */
@Data
public class UpdateDepartmentRequest {

    private String name;

    private Long parentId;

    private Integer sortOrder;

    private Integer status;
}
