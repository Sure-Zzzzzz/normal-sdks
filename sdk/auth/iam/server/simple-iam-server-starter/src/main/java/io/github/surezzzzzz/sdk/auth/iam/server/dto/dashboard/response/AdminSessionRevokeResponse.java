package io.github.surezzzzzz.sdk.auth.iam.server.dto.dashboard.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 管理台强制下线响应
 *
 * @author surezzzzzz
 */
@Data
@AllArgsConstructor
public class AdminSessionRevokeResponse {

    /**
     * 实际撤销的会话数
     */
    private int revoked;
}
