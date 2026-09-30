package io.github.surezzzzzz.sdk.audit.iam.resource.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * IAM 资源访问审计记录。
 *
 * <p>由公共 {@code ResourceAccessEvent} 转换而来，只承载已验证访问的审计元数据，
 * 不含 Token 原文、凭据或授权声明。</p>
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IamResourceAuditRecord {

    /**
     * 认证来源标识（固定为 IAM 来源）。
     */
    private String authenticationSourceId;
    /**
     * 已验证主体类型（HUMAN）。
     */
    private String subjectType;
    /**
     * 已验证主体标识。
     */
    private String subjectId;
    /**
     * 已授权应用编码。
     */
    private String applicationCode;
    /**
     * 请求关联标识。
     */
    private String requestId;
    /**
     * 请求路径。
     */
    private String requestUri;
    /**
     * HTTP 方法。
     */
    private String httpMethod;
    /**
     * 远端地址。
     */
    private String remoteAddr;
    /**
     * User-Agent 摘要。
     */
    private String userAgent;
    /**
     * 事件时间戳（毫秒）。
     */
    private Long timestamp;
    /**
     * 调用链追踪标识（可选）。
     */
    private String traceId;
}
