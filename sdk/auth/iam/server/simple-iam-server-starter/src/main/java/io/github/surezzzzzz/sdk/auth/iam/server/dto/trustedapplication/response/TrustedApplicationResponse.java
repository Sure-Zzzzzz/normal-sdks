package io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.response;

import lombok.Builder;
import lombok.Getter;

/**
 * 可信应用响应（列表级摘要）
 *
 * <p>列表查询返回，不含 client 明细，只含统计信息。详情聚合用 {@link TrustedApplicationDetailResponse}。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class TrustedApplicationResponse {

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
     * 关联客户端数量
     */
    private int clientCount;

    /**
     * 是否启用 Portal 集成
     */
    private boolean portalEnabled;

    /**
     * 是否内置应用（平台引导注册，禁止删除）
     */
    private boolean builtIn;
}
