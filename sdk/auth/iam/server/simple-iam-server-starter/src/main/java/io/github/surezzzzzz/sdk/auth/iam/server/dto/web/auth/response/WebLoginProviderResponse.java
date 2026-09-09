package io.github.surezzzzzz.sdk.auth.iam.server.dto.web.auth.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Web 登录方式响应
 *
 * @author surezzzzzz
 */
@Data
@AllArgsConstructor
public class WebLoginProviderResponse {

    private String code;

    private String name;

    private String type;

    private boolean enabled;

    private String authorizeUrl;

    private String description;
}
