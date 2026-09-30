package io.github.surezzzzzz.sdk.auth.iam.server.dto.web.auth.request;

import lombok.Data;

/**
 * 忘记密码重置请求（手机验证一步完成，不建会话）。
 *
 * @author surezzzzzz
 */
@Data
public class WebPasswordResetRequest {

    /**
     * 挑战 ID
     */
    private String challengeId;

    /**
     * 验证码
     */
    private String code;
    /**
     * 手机号（须与挑战创建号一致，消费时按号哈希原子校验）
     */
    private String phone;

    /**
     * 新密码（复用既有改密策略校验链）
     */
    private String newPassword;
}
