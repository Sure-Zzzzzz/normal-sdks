package io.github.surezzzzzz.sdk.iam.client.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * IAM Portal 可信应用路由投影。
 *
 * <p>模型只承载路由元数据和受控转发策略，不包含 client secret、Token、Cookie 值或用户资料。</p>
 *
 * @author surezzzzzz
 */
@Value
@Builder
public class IamPortalApplicationRoute {

    /**
     * 应用编码。
     */
    String applicationCode;
    /**
     * 应用名称。
     */
    String applicationName;
    /**
     * 应用状态（1=启用）。
     */
    Integer status;
    /**
     * 是否启用 Portal 路由。
     */
    Boolean portalEnabled;
    /**
     * Portal 公开路由前缀。
     */
    String routePrefix;
    /**
     * 页面入口公共路径。
     */
    String entry;
    /**
     * 非空 API 公共路径。
     */
    String apiBase;
    /**
     * 业务自有 Nginx 的唯一 HTTP 机器目标 origin。
     */
    String targetOrigin;
    /**
     * 应用配置版本。
     */
    Long configVersion;
    /**
     * Authorization 转发策略。
     */
    String forwardAuthorization;
    /**
     * 允许转发的 Cookie 名称白名单。
     */
    List<String> forwardCookieNames;
}
