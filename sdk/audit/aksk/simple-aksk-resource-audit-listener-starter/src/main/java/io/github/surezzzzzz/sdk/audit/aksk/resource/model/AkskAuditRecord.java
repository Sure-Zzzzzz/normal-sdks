package io.github.surezzzzzz.sdk.audit.aksk.resource.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AKSK资源访问审计记录。
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AkskAuditRecord {
    private String authenticationSourceId;
    private String subjectType;
    private String subjectId;
    private String applicationCode;
    private String requestId;
    private String requestUri;
    private String httpMethod;
    private String remoteAddr;
    private String userAgent;
    private Long timestamp;
    private String traceId;
}
