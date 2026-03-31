package io.github.surezzzzzz.sdk.auth.aksk.resource.core.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.Map;

/**
 * AKSK 访问事件
 *
 * <p>在 AKSK 认证成功后发布，包含完整的访问信息，用于审计、监控等场景。
 *
 * <p>事件内容：
 * <ul>
 *   <li>客户端信息：clientId、clientType</li>
 *   <li>用户信息：userId、username、roles、scope</li>
 *   <li>请求信息：requestUri、httpMethod、remoteAddr、userAgent</li>
 *   <li>元数据：source（header/jwt）、traceId、context</li>
 * </ul>
 *
 * <p>时间戳通过 {@link ApplicationEvent#getTimestamp()} 获取。
 *
 * @author surezzzzzz
 * @since 1.0.1
 */
@Getter
public class AkskAccessEvent extends ApplicationEvent {

    // ==================== 客户端信息 ====================

    /**
     * 客户端ID（即 accessKey）
     */
    private final String clientId;

    /**
     * 客户端类型（platform/user）
     */
    private final String clientType;

    /**
     * 用户ID
     */
    private final String userId;

    /**
     * 用户名
     */
    private final String username;

    /**
     * 角色
     */
    private final String roles;

    /**
     * 权限范围
     */
    private final String scope;

    // ==================== 请求信息 ====================

    /**
     * 请求URI
     */
    private final String requestUri;

    /**
     * HTTP方法（GET/POST/...）
     */
    private final String httpMethod;

    /**
     * 来源IP
     */
    private final String remoteAddr;

    /**
     * User-Agent（可选）
     */
    private final String userAgent;

    // ==================== 元数据 ====================

    /**
     * 来源类型（header/jwt）
     */
    private final String source;

    /**
     * 链路追踪ID（从配置的Header提取）
     */
    private final String traceId;

    /**
     * 完整上下文（用于扩展）
     */
    private final Map<String, String> context;

    public AkskAccessEvent(Object source, String clientId, String clientType,
                           String userId, String username, String roles, String scope,
                           String requestUri, String httpMethod, String remoteAddr,
                           String userAgent, String sourceType,
                           String traceId, Map<String, String> context) {
        super(source);
        this.clientId = clientId;
        this.clientType = clientType;
        this.userId = userId;
        this.username = username;
        this.roles = roles;
        this.scope = scope;
        this.requestUri = requestUri;
        this.httpMethod = httpMethod;
        this.remoteAddr = remoteAddr;
        this.userAgent = userAgent;
        this.source = sourceType;
        this.traceId = traceId;
        this.context = context;
    }
}
