package io.github.surezzzzzz.sdk.audit.iam.resource.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * IAM 资源访问审计记录
 *
 * <p>由公共 {@code ResourceAccessEvent} 转换而来，只承载已验证访问的审计元数据，
 * 不含 Token 原文、凭据或授权声明。
 *
 * @author surezzzzzz
 * @since 1.0.0
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
     * 已验证主体标识（IAM 用户 ID）。
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
     * 链路追踪标识（由可选 Provider 补充，无 Provider 时为 null）。
     */
    private String traceId;
}
