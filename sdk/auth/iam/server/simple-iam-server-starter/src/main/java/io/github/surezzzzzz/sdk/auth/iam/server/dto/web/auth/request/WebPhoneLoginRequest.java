package io.github.surezzzzzz.sdk.auth.iam.server.dto.web.auth.request;

import lombok.Data;

import java.util.List;

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

    /**
     * 当前登录要求配置的修订号；未配置登录要求时可为空。
     */
    private String requirementsRevision;

    /**
     * 用户已同意的文档版本 ID；未配置登录要求时可为空。
     */
    private List<Long> acceptedDocumentVersionIds;
}
