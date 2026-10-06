package io.github.surezzzzzz.sdk.audit.aksk.resource.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AKSK 已认证资源访问记录；字段与旧线 3.0.0 保持一致，不包含认证凭据或权限集合。
 *
 * @author surezzzzzz
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AkskAuditRecord {
    /**
     * 已验证的认证来源，官方转换结果恒为 aksk。
     */
    private String authenticationSourceId;
    /**
     * 主体类型，保留公共事件中的枚举名称。
     */
    private String subjectType;
    /**
     * 主体标识，服务主体通常为客户端 ID。
     */
    private String subjectId;
    /**
     * 目标应用编码。
     */
    private String applicationCode;
    /**
     * 公共资源链生成或传入的请求关联标识。
     */
    private String requestId;
    /**
     * 资源路径，不包含查询参数。
     */
    private String requestUri;
    /**
     * 请求方法。
     */
    private String httpMethod;
    /**
     * 事件记录的网络来源地址，不据此推断已验证身份。
     */
    private String remoteAddr;
    /**
     * 事件记录的客户端标识原值，存储前由业务进行必要脱敏。
     */
    private String userAgent;
    /**
     * 公共访问事件的原始毫秒时间戳，不改为消费时间。
     */
    private Long timestamp;
    /**
     * 可选提供者在异步消费线程返回的追踪标识。
     */
    private String traceId;
}
