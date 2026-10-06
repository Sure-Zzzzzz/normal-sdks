package io.github.surezzzzzz.sdk.auth.iam.server.dto.openrole.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 开放角色安全错误体，错误类别仅通过 HTTP 状态表达。
 *
 * @author surezzzzzz
 */
@Getter
@RequiredArgsConstructor
public class OpenRoleErrorResponse {
    /**
     * 安全面向调用方的消息。
     */
    private final String message;
    /**
     * 错误时间。
     */
    private final String timestamp;
    /**
     * 请求关联标识，不是认证凭据。
     */
    private final String requestId;
}
