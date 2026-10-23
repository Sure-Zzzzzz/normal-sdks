package io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

/**
 * IAM Portal 路由投影的中性副本。
 *
 * <p>该模型只保留路由元数据和显式转发白名单，不携带凭据、令牌或 Cookie 值。</p>
 *
 * @author surezzzzzz
 */
@Value
@Builder
public class PortalApplicationRoute {

    /**
     * 应用编码。
     */
    String applicationCode;
    /**
     * 应用显示名称。
     */
    String applicationName;
    /**
     * 应用状态，1 表示启用。
     */
    Integer status;
    /**
     * 是否启用 Portal 路由。
     */
    Boolean portalEnabled;
    /**
     * IAM 返回的公开路由前缀。
     */
    String routePrefix;
    /**
     * 浏览器可见的公共页面路径。
     */
    String entry;
    /**
     * 浏览器可见的公共 API 路径。
     */
    String apiBase;
    /**
     * 页面和 API 共用的业务自有 Nginx HTTP origin。
     */
    String targetOrigin;
    /**
     * 应用路由配置版本。
     */
    Long configVersion;
    /**
     * Authorization 转发策略。
     */
    String forwardAuthorization;
    /**
     * 允许转发的 Cookie 名称。
     */
    @Singular
    List<String> forwardCookieNames;
}
