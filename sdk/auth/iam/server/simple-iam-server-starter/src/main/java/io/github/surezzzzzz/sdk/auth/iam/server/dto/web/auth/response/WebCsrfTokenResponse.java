package io.github.surezzzzzz.sdk.auth.iam.server.dto.web.auth.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Web CSRF Token 响应
 *
 * @author surezzzzzz
 */
@Data
@AllArgsConstructor
public class WebCsrfTokenResponse {

    private String headerName;

    private String parameterName;

    private String token;
}
