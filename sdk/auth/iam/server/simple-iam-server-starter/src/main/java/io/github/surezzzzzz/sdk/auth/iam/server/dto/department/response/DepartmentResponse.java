package io.github.surezzzzzz.sdk.auth.iam.server.dto.department.response;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamDepartmentEntity;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

/**
 * 部门响应
 *
 * @author surezzzzzz
 */
@Data
@AllArgsConstructor
public class DepartmentResponse {

    private Long id;

    private String code;

    private String name;

    private Long parentId;

    private String parentName;

    private Integer sortOrder;

    private Integer status;

    private Instant createdAt;

    private Instant updatedAt;

    /**
     * 部门实体转响应（上级部门名置空）
     */
    public static DepartmentResponse from(IamDepartmentEntity department) {
        return from(department, null);
    }

    /**
     * 部门实体转响应（携带上级部门名）
     */
    public static DepartmentResponse from(IamDepartmentEntity department, String parentName) {
        return new DepartmentResponse(
                department.getId(),
                department.getCode(),
                department.getName(),
                department.getParentId(),
                parentName,
                department.getSortOrder(),
                department.getStatus(),
                department.getCreatedAt(),
                department.getUpdatedAt()
        );
    }
}
