package io.github.surezzzzzz.sdk.auth.resource.core.event;

import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceSubjectType;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext;
import lombok.Getter;

/**
 * 已验证资源访问事件。
 *
 * <p>仅包含统一资源认证链完成验证后的主体、应用和请求摘要，不承载认证凭据、声明原文或Servlet对象。
 * 纯模型对象（不依赖Spring事件基类），经 {@code ApplicationEventPublisher#publishEvent(Object)} 发布。
 *
 * @author surezzzzzz
 */
@Getter
public final class ResourceAccessEvent {

    /**
     * 认证来源标识。
     */
    private final String authenticationSourceId;
    /**
     * 已验证主体类型。
     */
    private final ResourceSubjectType subjectType;
    /**
     * 已验证主体标识。
     */
    private final String subjectId;
    /**
     * 已授权应用编码。
     */
    private final String applicationCode;
    /**
     * 请求关联标识。
     */
    private final String requestId;
    /**
     * 请求路径。
     */
    private final String requestUri;
    /**
     * HTTP方法。
     */
    private final String httpMethod;
    /**
     * 远端地址。
     */
    private final String remoteAddr;
    /**
     * User-Agent摘要。
     */
    private final String userAgent;
    /**
     * 事件创建时间戳（毫秒）。
     */
    private final long timestamp;

    /**
     * 创建已验证资源访问事件。
     *
     * @param context    已验证资源上下文
     * @param requestUri 请求路径
     * @param httpMethod HTTP方法
     * @param remoteAddr 远端地址
     * @param userAgent  User-Agent摘要
     */
    public ResourceAccessEvent(VerifiedResourceContext context, String requestUri,
                               String httpMethod, String remoteAddr, String userAgent) {
        this.authenticationSourceId = context.getPrincipal().getSourceId().getValue();
        this.subjectType = context.getPrincipal().getSubjectType();
        this.subjectId = context.getPrincipal().getSubjectId();
        this.applicationCode = context.getApplicationAuthorization().getApplicationCode();
        this.requestId = context.getRequestId();
        this.requestUri = requestUri;
        this.httpMethod = httpMethod;
        this.remoteAddr = remoteAddr;
        this.userAgent = userAgent;
        this.timestamp = System.currentTimeMillis();
    }
}
