package io.github.surezzzzzz.sdk.auth.iam.server.dto.web.auth.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Web 登录响应
 *
 * @author surezzzzzz
 */
@Data
@AllArgsConstructor
public class WebLoginResponse {

    private String message;

    private WebAuthUserResponse user;

    /**
     * 本次登录是否要求人机验证（true 时前端需取挑战并要求用户输入验证码后重试）
     */
    private boolean captchaRequired;

    /**
     * 本次登录是否须先修改密码（管理员建号/重置密码后的首次登录为 true，
     * 前端跳改密页；改密完成前其余 API 被服务端 403 拦截）
     */
    private boolean mustChangePassword;

    /**
     * 须改密原因（1.3.6，可空）：FIRST_LOGIN / PASSWORD_RESET / PASSWORD_EXPIRED，
     * 与 mustChangePassword 同真；前端按原因出改密页文案
     */
    private String mustChangePasswordReason;

    /**
     * 口令剩余天数（1.3.6，可空）：生存期开启且临期时携带供前端提示横幅；其余为 null
     */
    private Integer passwordExpiresInDays;
}
