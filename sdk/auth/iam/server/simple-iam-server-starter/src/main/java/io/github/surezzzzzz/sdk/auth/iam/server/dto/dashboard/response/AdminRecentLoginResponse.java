package io.github.surezzzzzz.sdk.auth.iam.server.dto.dashboard.response;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

/**
 * 管理台仪表盘最近登录用户响应
 *
 * @author surezzzzzz
 */
@Data
@AllArgsConstructor
public class AdminRecentLoginResponse {

    private Long userId;

    private String username;

    private String displayName;

    private String departmentName;

    private Instant lastLoginAt;

    /**
     * 登录审计记录转响应视图
     */
    public static AdminRecentLoginResponse from(IamUserEntity user, String departmentName) {
        return new AdminRecentLoginResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                departmentName,
                user.getLastLoginAt()
        );
    }
}
