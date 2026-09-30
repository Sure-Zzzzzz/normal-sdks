package io.github.surezzzzzz.sdk.auth.iam.server.dto.web.account.response;

import lombok.Builder;
import lombok.Getter;

/**
 * 手机号状态视图（管理端只读/账户安全页共用口径：脱敏号+状态+绑定时间）。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class WebPhoneStatusResponse {

    /**
     * 脱敏手机号（如 138****0000）
     */
    private final String maskedPhone;

    /**
     * 状态（已登记/已验证）
     */
    private final String status;

    /**
     * 绑定时间（已验证时有值）
     */
    private final String boundAt;
}
