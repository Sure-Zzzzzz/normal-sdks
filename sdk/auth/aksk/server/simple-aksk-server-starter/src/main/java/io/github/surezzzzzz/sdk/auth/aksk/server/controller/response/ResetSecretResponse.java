package io.github.surezzzzzz.sdk.auth.aksk.server.controller.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 重置 Client Secret 响应
 *
 * @author surezzzzzz
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResetSecretResponse {
    /**
     * 客户端 ID
     */
    private String clientId;

    /**
     * 新 Client Secret 明文（仅此一次）
     */
    private String clientSecret;
}
