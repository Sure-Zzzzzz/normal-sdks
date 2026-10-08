package io.github.surezzzzzz.sdk.auth.aksk.openapi.client.model;

/**
 * 按 Client 批量撤销 Token 响应
 *
 * @author surezzzzzz
 */
public class BatchRevokeResponse {

    /**
     * 撤销计数
     */
    private int revokedCount;


    public int getRevokedCount() {
        return revokedCount;
    }

    public void setRevokedCount(int revokedCount) {
        this.revokedCount = revokedCount;
    }
}
