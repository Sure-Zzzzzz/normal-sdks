package io.github.surezzzzzz.sdk.auth.iam.server.dto.web.auth.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * Web 当前登录用户响应
 *
 * @author surezzzzzz
 */
@Data
@AllArgsConstructor
public class WebAuthUserResponse {

    private String subjectId;

    private String username;

    private String displayName;

    private boolean admin;

    private List<String> authorities;

    /**
     * 须改密原因（1.3.6，可空）：FIRST_LOGIN / PASSWORD_RESET / PASSWORD_EXPIRED；/me 场景
     * 供改密页刷新后恢复文案（/me 在强制改密 Filter 白名单内，受限期可读）
     */
    private String mustChangePasswordReason;

    /**
     * 口令剩余天数（1.3.6，可空）：生存期策略开启且临期（≤提醒窗口）时为 1..N，已过期为 0；
     * 策略关闭或未激活为 null
     */
    private Integer passwordExpiresInDays;
}
