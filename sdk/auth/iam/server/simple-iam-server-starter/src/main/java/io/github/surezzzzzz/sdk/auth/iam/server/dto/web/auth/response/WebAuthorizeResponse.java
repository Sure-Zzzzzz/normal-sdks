package io.github.surezzzzzz.sdk.auth.iam.server.dto.web.auth.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 跳转型登录发起响应
 *
 * @author surezzzzzz
 */
@Data
@AllArgsConstructor
public class WebAuthorizeResponse {

    private String authorizeUrl;
}
