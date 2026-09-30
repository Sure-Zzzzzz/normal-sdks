package io.github.surezzzzzz.sdk.auth.iam.server.dto.web.auth.request;

import lombok.Data;

/**
 * 手机号验证码登录请求。
 *
 * @author surezzzzzz
 */
@Data
public class WebPhoneLoginRequest {

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
}
