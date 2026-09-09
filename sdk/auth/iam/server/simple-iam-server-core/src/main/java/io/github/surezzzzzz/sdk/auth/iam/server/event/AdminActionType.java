package io.github.surezzzzzz.sdk.auth.iam.server.event;

import lombok.Getter;

/**
 * IAM 管理面操作动作类型。
 *
 * @author surezzzzzz
 */
@Getter
public enum AdminActionType {

    /**
     * 创建实体
     */
    CREATED("created", "创建"),
    /**
     * 更新实体
     */
    UPDATED("updated", "更新"),
    /**
     * 删除实体
     */
    DELETED("deleted", "删除"),
    /**
     * 启用（用户等有状态实体）
     */
    ENABLED("enabled", "启用"),
    /**
     * 禁用（用户等有状态实体）
     */
    DISABLED("disabled", "禁用"),
    /**
     * 手动解锁（清除登录失败锁定与失败计数）
     */
    UNLOCKED("unlocked", "手动解锁"),
    /**
     * 密钥轮换（OAuth 客户端 / 资源验证客户端 secret）
     */
    SECRET_ROTATED("secret-rotated", "密钥轮换"),
    /**
     * 分配关系（用户↔角色、角色↔权限、用户↔用户组等）
     */
    ASSIGNED("assigned", "分配"),
    /**
     * 解除分配关系
     */
    UNASSIGNED("unassigned", "解除分配"),
    /**
     * 绑定外部身份
     */
    BOUND("bound", "绑定外部身份"),
    /**
     * 解绑外部身份
     */
    UNBOUND("unbound", "解绑外部身份"),
    /**
     * 管理员重置用户密码
     */
    PASSWORD_RESET("password-reset", "重置密码"),
    /**
     * 用户自助修改密码
     */
    PASSWORD_CHANGED("password-changed", "修改密码"),
    /**
     * 部署级恢复（bootstrap 管理员密码恢复等高危操作）
     */
    RECOVERED("recovered", "部署级恢复"),
    /**
     * 授予应用授权（首次）
     */
    GRANTED("granted", "授予授权"),
    /**
     * 替换应用授权（全量更新既有授权）
     */
    REPLACED("replaced", "替换授权"),
    /**
     * 撤销（应用授权、验证客户端等）
     */
    REVOKED("revoked", "撤销");

    private final String code;
    private final String description;

    AdminActionType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据 code 获取枚举
     *
     * @param code 动作类型 code
     * @return 枚举，不存在返回 null
     */
    public static AdminActionType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (AdminActionType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }
}
