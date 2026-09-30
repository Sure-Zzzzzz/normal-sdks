package io.github.surezzzzzz.sdk.auth.iam.server.dto.web.account.request;

import lombok.Data;

/**
 * 绑定挑战创建请求（登录态，验证新号）。
 *
 * @author surezzzzzz
 */
@Data
public class WebPhoneBindChallengeRequest {

    /**
     * 新手机号（E.164 或国内 11 位，入口统一规范化）
     */
    private String phone;
}
