package io.github.surezzzzzz.sdk.auth.aksk.openapi.client.model;

/**
 * OAuth2 client_credentials 换 Token 响应（E2E 接入闭环用；正式令牌管理归 AKSK 底座）。
 *
 * @author surezzzzzz
 */
public class TokenExchangeResponse {

    @com.fasterxml.jackson.annotation.JsonProperty("access_token")
    private String accessToken;

    @com.fasterxml.jackson.annotation.JsonProperty("expires_in")
    private Long expiresIn;

    @com.fasterxml.jackson.annotation.JsonProperty("token_type")
    private String tokenType;

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public Long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(Long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }
}
