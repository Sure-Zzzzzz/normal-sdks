package io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request;

import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.request.PortalIntegrationRequest;
import lombok.Data;

/**
 * 更新可信应用请求
 *
 * <p>更新应用基本信息 + Portal 集成配置 + 菜单项。{@code applicationCode} 不可变更。
 * client 维度的增删改走 {@code /trusted-applications/{applicationId}/clients} 系列接口。
 *
 * @author surezzzzzz
 */
@Data
public class UpdateTrustedApplicationRequest {

    /**
     * 应用展示名
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
     * Portal 集成配置（整体覆盖：传则按传入值重写 portal + 菜单，不传则保持不动）
     */
    private PortalIntegrationRequest portal;
}
