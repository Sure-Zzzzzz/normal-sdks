package io.github.surezzzzzz.sdk.auth.iam.server.dto.web.account.request;

import lombok.Data;

/**
 * 手机号绑定/换绑请求（验证通过即整体替换：无绑定写入/有绑定原子覆盖）。
 *
 * @author surezzzzzz
 */
@Data
public class WebPhoneBindRequest {

    /**
     * 挑战 ID
     */
    private String challengeId;

    /**
     * 验证码
     */
    private String code;

    /**
     * 绑定手机号（须与挑战创建时的号一致，消费时按号哈希原子校验）
     */
    private String phone;
}
