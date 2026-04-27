package io.github.surezzzzzz.sdk.auth.aksk.server.controller.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批量撤销 Token 响应
 *
 * @author surezzzzzz
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchRevokeResponse {
    /**
     * 本次实际撤销的 token 数量（已撤销/已过期的不计入）
     */
    private int revokedCount;
}
