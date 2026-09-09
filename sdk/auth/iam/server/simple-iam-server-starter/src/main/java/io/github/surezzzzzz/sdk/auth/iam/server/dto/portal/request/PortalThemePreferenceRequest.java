package io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.request;

import lombok.Data;

import java.util.Map;

/**
 * Portal 当前用户主题偏好请求。
 *
 * @author surezzzzzz
 */
@Data
public class PortalThemePreferenceRequest {

    private Integer contractVersion;

    private String mode;

    private Map<String, String> customTokens;
}
