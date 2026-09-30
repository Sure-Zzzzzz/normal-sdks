package io.github.surezzzzzz.sdk.auth.iam.server.dto.web.auth.request;

import lombok.Data;

/**
 * 匿名手机号挑战创建请求（登录/忘记密码）。
 *
 * @author surezzzzzz
 */
@Data
public class WebPhoneChallengeRequest {

    /**
     * 手机号（E.164 或国内 11 位，入口统一规范化）
     */
    private String phone;

    /**
     * 用途（login/forgot-password）
     */
    private String purpose;
}
