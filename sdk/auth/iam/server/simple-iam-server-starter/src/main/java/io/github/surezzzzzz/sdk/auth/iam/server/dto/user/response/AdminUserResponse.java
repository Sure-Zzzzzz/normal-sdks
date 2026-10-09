package io.github.surezzzzzz.sdk.auth.iam.server.dto.user.response;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.user.IamUserEntity;
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

    private String subjectId;

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
     * 密码最近一次设置时刻（1.3.6，可空：null=生存期策略未激活）；正常/临期/过期由前端按本字段
     * 与剩余天数渲染，不设状态枚举
     */
    private Instant passwordUpdatedAt;

    /**
     * 口令剩余天数（1.3.6，可空：策略关闭/未激活为 null；已过期为 0）
     */
    private Integer passwordExpiresInDays;

    /**
     * 用户实体转响应（部门名置空）
     */
    /**
     * 用户实体转响应（组织树/角色/协作组等成员列表场景：不携带口令生存期字段）
     */
    public static AdminUserResponse from(IamUserEntity user, String departmentName) {
        return from(user, departmentName, null);
    }

    /**
     * 用户实体转响应（携带部门名与口令生存期两字段，1.3.6）
     *
     * @param passwordExpiresInDays 口令剩余天数（由调用方经 IamPasswordMaxAgeSupport 计算；
     *                              策略关闭/未激活传 null）
     */
    public static AdminUserResponse from(IamUserEntity user, String departmentName,
                                         Integer passwordExpiresInDays) {
        return new AdminUserResponse(
                user.getSubjectId(),
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
                user.getUpdatedAt(),
                user.getPasswordUpdatedAt(),
                passwordExpiresInDays
        );
    }
}
