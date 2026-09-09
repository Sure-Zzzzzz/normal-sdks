package io.github.surezzzzzz.sdk.auth.iam.server.dto.authorization.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 用户应用授权摘要响应
 *
 * <p>列表与单条查询共用；不含授权内容明细，明细见
 * {@link ApplicationAuthorizationDetailResponse}。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class ApplicationAuthorizationResponse {

    /**
     * 可信应用ID
     */
    private Long applicationId;

    /**
     * 该用户是否平台管理员（挂内置 iam_admin，解析层特权不受本行授权状态影响，
     * 管理面据此禁用撤销入口）
     */
    private Boolean platformAdmin;

    /**
     * 是否通过应用准入（true=准入）
     */
    private Boolean admitted;

    /**
     * 应用授权版本（服务端单调递增）
     */
    private Long authorizationVersion;

    /**
     * 权限清单版本
     */
    private String manifestVersion;

    /**
     * 状态（1=有效，0=已撤销）
     */
    private Integer status;

    /**
     * 创建时间
     */
    private Instant createdAt;

    /**
     * 最近更新时间（最近一次替换 / 撤销 / 重激活）
     */
    private Instant updatedAt;

    /**
     * 撤销时间（未撤销为 null）
     */
    private Instant revokedAt;
}
