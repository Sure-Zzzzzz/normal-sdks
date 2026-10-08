package io.github.surezzzzzz.sdk.auth.aksk.openapi.client.model;

/**
 * 轮换 Secret 响应（新 clientSecret 仅此一次返回）
 *
 * @author surezzzzzz
 */
public class ResetSecretResponse {

    /**
     * Client 标识
     */
    private String clientId;

    /**
     * 新 Client 密钥（仅此一次）
     */
    private String clientSecret;


    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }
}
