package io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 重置密码请求
 *
 * @author surezzzzzz
 */
@Getter
@Setter
public class ResetPasswordRequest {

    /**
     * 新密码（明文，service 层做策略校验 + BCrypt 加密）
     */
    private String newPassword;
}
