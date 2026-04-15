package io.github.surezzzzzz.sdk.audit.aksk.resource.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * AKSK 审计记录
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AkskAuditRecord {
    private String clientId;
    private String clientType;
    private String userId;
    private String username;
    private String roles;
    private String scope;
    private String requestUri;
    private String httpMethod;
    private String remoteAddr;
    private String userAgent;
    private Long timestamp;
    private String source;
    private String traceId;
    private Map<String, String> context;
}
