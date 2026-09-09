package io.github.surezzzzzz.sdk.auth.iam.server.dto.web.auth.request;

import lombok.Getter;
import lombok.Setter;

/**
 * Web 自助修改密码请求
 *
 * @author surezzzzzz
 */
@Getter
@Setter
public class ChangePasswordRequest {

    /**
     * 原密码
     */
    private String oldPassword;

    /**
     * 新密码（须满足密码策略）
     */
    private String newPassword;
}
