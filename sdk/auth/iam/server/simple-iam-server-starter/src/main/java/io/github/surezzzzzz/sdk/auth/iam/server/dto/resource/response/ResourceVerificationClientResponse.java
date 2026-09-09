package io.github.surezzzzzz.sdk.auth.iam.server.dto.resource.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 资源验证客户端摘要 / 详情响应
 *
 * <p>不回显 client_secret_hash 或任何密钥形态。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class ResourceVerificationClientResponse {

    /**
     * 客户端ID
     */
    private String clientId;

    /**
     * 绑定的可信应用ID
     */
    private Long applicationId;

    /**
     * 状态（1=活跃，0=已撤销）
     */
    private Integer status;

    /**
     * 创建时间
     */
    private Instant createdAt;

    /**
     * 最近更新时间（最近一次轮换 / 撤销）
     */
    private Instant updatedAt;

    /**
     * 撤销时间（未撤销为 null）
     */
    private Instant revokedAt;
}
