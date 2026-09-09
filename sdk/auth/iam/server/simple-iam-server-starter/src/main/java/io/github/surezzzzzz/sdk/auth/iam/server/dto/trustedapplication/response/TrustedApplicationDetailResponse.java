package io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.response;

import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response.PortalIntegrationResponse;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 可信应用详情响应（聚合）
 *
 * <p>聚合应用基本信息 + Portal 集成配置 + 全部关联客户端。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class TrustedApplicationDetailResponse {

    /**
     * 应用主键ID
     */
    private Long id;

    /**
     * 应用编码
     */
    private String applicationCode;

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
     * 是否内置应用（平台引导注册，禁止删除）
     */
    private boolean builtIn;

    /**
     * Portal 集成配置（未配置时为 null）
     */
    private PortalIntegrationResponse portal;

    /**
     * 全部关联客户端
     */
    private List<TrustedApplicationClientResponse> clients;
}
