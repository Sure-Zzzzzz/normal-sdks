package io.github.surezzzzzz.sdk.auth.iam.server.dto.web.auth.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * Web 登录方式列表响应
 *
 * @author surezzzzzz
 */
@Data
@AllArgsConstructor
public class WebLoginProvidersResponse {

    private String defaultProvider;

    private List<WebLoginProviderResponse> providers;
}
