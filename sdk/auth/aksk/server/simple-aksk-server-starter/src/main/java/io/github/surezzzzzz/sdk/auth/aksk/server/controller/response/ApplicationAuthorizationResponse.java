package io.github.surezzzzzz.sdk.auth.aksk.server.controller.response;

import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 应用授权响应。
 *
 * @author surezzzzzz
 */
@Data
public class ApplicationAuthorizationResponse {

    private String clientId;
    private Integer clientType;
    private String ownerUserId;
    private String applicationCode;
    private Boolean admitted;
    private Boolean enabled;
    private List<String> roles;
    private List<String> pagePermissions;
    private List<String> apiPermissions;
    private Map<String, Object> dataGrantDocument;
    private Long authorizationVersion;
    private String manifestVersion;
    private String manifestDigest;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant revokedAt;
}
