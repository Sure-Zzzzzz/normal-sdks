package io.github.surezzzzzz.sdk.auth.iam.server.dto.department.response;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamDepartmentEntity;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 开放 API 部门响应（组织同步所需字段集，fullPath 便于外部系统重建树）。
 *
 * @author surezzzzzz
 */
@Data
@AllArgsConstructor
public class DepartmentRestResponse {

    private Long id;

    private String code;

    private String name;

    private Long parentId;

    private String fullPath;

    private Integer sortOrder;

    private Integer status;

    /**
     * 部门实体转开放 API 响应。
     *
     * @param department 部门实体
     * @param fullPath   自根起的全路径（如 /root/研发/后端）
     */
    public static DepartmentRestResponse from(IamDepartmentEntity department, String fullPath) {
        return new DepartmentRestResponse(
                department.getId(),
                department.getCode(),
                department.getName(),
                department.getParentId(),
                fullPath,
                department.getSortOrder(),
                department.getStatus());
    }
}
