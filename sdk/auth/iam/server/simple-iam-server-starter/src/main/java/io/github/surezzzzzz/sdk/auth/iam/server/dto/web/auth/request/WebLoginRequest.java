package io.github.surezzzzzz.sdk.auth.iam.server.dto.web.auth.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Web 登录请求
 *
 * @author surezzzzzz
 */
@Getter
@Setter
public class WebLoginRequest {

    /**
     * 登录方式编码，缺省 local-password；其余取值来自 /providers 返回的凭证校验型登录方式
     */
    private String provider;

    private String username;

    private String password;

    /**
     * 人机验证挑战 id（登录策略要求验证码时必填，取自 /iam/web/auth/captcha 返回）
     */
    private String captchaId;

    /**
     * 人机验证答案（登录策略要求验证码时必填）
     */
    private String captchaAnswer;

    private String requirementsRevision;

    private List<Long> acceptedDocumentVersionIds;
}
