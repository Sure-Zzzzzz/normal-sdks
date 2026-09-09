package io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request;

import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.request.PortalIntegrationRequest;
import lombok.Data;

/**
 * 创建可信应用请求
 *
 * <p>一应用可关联多个 OAuth2 客户端。创建时必须带 {@code initialClient}，
 * 可选附带 {@code portal} 集成配置（启用后该应用作为 qiankun 微前端出现在 Portal 侧边栏）。
 *
 * @author surezzzzzz
 */
@Data
public class CreateTrustedApplicationRequest {

    /**
     * 应用编码，必填，唯一（如 oa）
     */
    private String applicationCode;

    /**
     * 应用展示名，必填
     */
    private String applicationName;

    /**
     * 应用描述
     */
    private String description;

    /**
     * 应用图标标识
     */
    private String icon;

    /**
     * Portal 集成配置，可选
     */
    private PortalIntegrationRequest portal;

    /**
     * 初始客户端，必填（一应用至少一个 client）
     */
    private CreateTrustedApplicationClientRequest initialClient;

    /**
     * 应用角色编码列表（权限清单），可选，创建时同步登记
     */
    private java.util.List<String> roles;

    /**
     * 页面权限编码列表（权限清单），可选
     */
    private java.util.List<String> pagePermissions;

    /**
     * API 权限编码列表（权限清单），可选
     */
    private java.util.List<String> apiPermissions;

    /**
     * DATA 资源声明列表（权限清单），可选
     */
    private java.util.List<io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest.DataResourceDeclaration> dataResources;
}
