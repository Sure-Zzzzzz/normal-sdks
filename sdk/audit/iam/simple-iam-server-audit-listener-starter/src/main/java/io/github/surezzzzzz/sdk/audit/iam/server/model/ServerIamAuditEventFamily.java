package io.github.surezzzzzz.sdk.audit.iam.server.model;

/**
 * IAM 审计事件族
 *
 * <p>与 simple-iam-server-core 的事件族一一对应；监听器按族归一化统一审计记录。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
public enum ServerIamAuditEventFamily {

    /**
     * OAuth2 Token 生命周期（签发 / 验证 / 撤销 / 移除 / 复用检测）。
     */
    TOKEN("token"),

    /**
     * 认证（登录成功 / 失败 / 登出 / 账号锁定）。
     */
    AUTHENTICATION("authentication"),

    /**
     * IAM 会话生命周期（建立 / 撤销）。
     */
    SESSION("session"),

    /**
     * 管理面操作（实体变更、关系分配、密钥轮换、授权管理等）。
     */
    ADMIN_ACTION("admin-action");

    private final String code;

    ServerIamAuditEventFamily(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
