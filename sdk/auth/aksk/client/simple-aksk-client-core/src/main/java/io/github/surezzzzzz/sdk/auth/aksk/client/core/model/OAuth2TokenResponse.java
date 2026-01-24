package io.github.surezzzzzz.sdk.auth.aksk.client.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OAuth2 Token Response
 *
 * @author surezzzzzz
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OAuth2TokenResponse {

    /**
     * Access Token
     */
    @JsonProperty("access_token")
    private String accessToken;

    /**
     * Token Type (通常为 "Bearer")
     */
    @JsonProperty("token_type")
    private String tokenType;

    /**
     * Token 有效期（秒）
     */
    @JsonProperty("expires_in")
    private Integer expiresIn;

    /**
     * Scope
     */
    private String scope;
}
