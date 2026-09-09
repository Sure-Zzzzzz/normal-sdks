package io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * Portal 当前用户主题偏好响应。
 *
 * @author surezzzzzz
 */
@Data
@AllArgsConstructor
public class PortalThemePreferenceResponse {

    private int contractVersion;

    private String mode;

    private Map<String, String> customTokens;

    private Instant updatedAt;
}
