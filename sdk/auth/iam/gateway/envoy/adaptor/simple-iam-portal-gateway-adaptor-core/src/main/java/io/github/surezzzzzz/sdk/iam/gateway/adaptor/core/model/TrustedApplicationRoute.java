package io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

/**
 * 可供 starter 转换为 xDS 路由的归一化路由。
 *
 * @author surezzzzzz
 */
@Value
@Builder
public class TrustedApplicationRoute {

    /**
     * 应用编码。
     */
    String applicationCode;
    /**
     * 应用名称。
     */
    String applicationName;
    /**
     * 页面或 API 路由。
     */
    RouteKind kind;
    /**
     * 浏览器访问的公开路径。
     */
    String publicPath;
    /**
     * 上游请求路径前缀。
     */
    String upstreamPath;
    /**
     * 固定解析端点。
     */
    ResolvedTarget target;
    /**
     * 应用路由配置版本。
     */
    Long configVersion;
    /**
     * 上游 Host/authority。
     */
    String upstreamHost;
    /**
     * Authorization 转发策略。
     */
    String forwardAuthorization;
    /**
     * Cookie 转发白名单。
     */
    @Singular
    List<String> forwardCookieNames;
}
