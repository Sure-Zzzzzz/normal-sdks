package io.github.surezzzzzz.sdk.auth.iam.server.event;

import lombok.Getter;

/**
 * IAM 管理面操作的目标实体类型。
 *
 * @author surezzzzzz
 */
@Getter
public enum AdminSubjectType {

    USER("user", "用户"),
    ROLE("role", "角色"),
    PERMISSION("permission", "权限"),
    DEPARTMENT("department", "部门"),
    USER_GROUP("user-group", "用户组"),
    MESSAGE("message", "站内信"),
    APPLICATION("application", "可信应用"),
    OAUTH_CLIENT("oauth-client", "OAuth2 客户端"),
    VERIFICATION_CLIENT("verification-client", "资源验证客户端"),
    APPLICATION_AUTHORIZATION("application-authorization", "应用授权"),
    EXTERNAL_BINDING("external-binding", "外部身份绑定"),
    PASSWORD_RESET_TOKEN("password-reset-token", "密码重置凭证"),
    BOOTSTRAP_ADMIN("bootstrap-admin", "内置管理员");

    private final String code;
    private final String description;

    AdminSubjectType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据 code 获取枚举
     *
     * @param code 目标类型 code
     * @return 枚举，不存在返回 null
     */
    public static AdminSubjectType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (AdminSubjectType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }
}
