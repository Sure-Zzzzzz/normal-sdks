package io.github.surezzzzzz.sdk.auth.iam.server.entity.user;

/**
 * 须改密原因（1.3.6）：与须改密标记同写同清，wire 值为枚举名（大写下划线），
 * 前端按原因出改密页文案；忘记密码自助重置是清标志路径，无此原因。
 *
 * @author surezzzzzz
 */
public enum MustChangePasswordReason {

    /**
     * 管理员建号（含 Excel 导入）的初始密码未修改过。
     */
    FIRST_LOGIN,

    /**
     * 管理员重置密码后强制设置新密码。
     */
    PASSWORD_RESET,

    /**
     * 口令最长生存期策略判定过期。
     */
    PASSWORD_EXPIRED
}
