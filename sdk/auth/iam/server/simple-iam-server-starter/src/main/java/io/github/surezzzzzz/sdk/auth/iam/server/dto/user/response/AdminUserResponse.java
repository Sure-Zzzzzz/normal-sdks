package io.github.surezzzzzz.sdk.auth.iam.server.dto.user.response;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

/**
 * 管理台用户响应
 *
 * @author surezzzzzz
 */
@Data
@AllArgsConstructor
public class AdminUserResponse {

    private Long id;

    private String username;

    private String displayName;

    private String email;

    private String phone;

    private Long departmentId;

    private String departmentName;

    private String identitySource;

    private String externalId;

    private Integer status;

    private Instant lockedUntil;

    private Instant lastLoginAt;

    private Instant createdAt;

    private Instant updatedAt;

    /**
     * 用户实体转响应（部门名置空）
     */
    public static AdminUserResponse from(IamUserEntity user) {
        return from(user, null);
    }

    /**
     * 用户实体转响应（携带部门名）
     */
    public static AdminUserResponse from(IamUserEntity user, String departmentName) {
        return new AdminUserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.getPhone(),
                user.getDepartmentId(),
                departmentName,
                user.getIdentitySource(),
                user.getExternalId(),
                user.getStatus(),
                user.getLockedUntil(),
                user.getLastLoginAt(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
