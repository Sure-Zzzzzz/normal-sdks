package io.github.surezzzzzz.sdk.auth.iam.server.dto.authorization.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 用户应用授权详情响应
 *
 * <p>在摘要基础上投影四类授权内容；dataGrantDocument 为 null 表示无数据授权。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class ApplicationAuthorizationDetailResponse {

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
     * 应用局部角色编码列表
     */
    private List<String> roles;

    /**
     * 应用页面权限编码列表
     */
    private List<String> pagePermissions;

    /**
     * 应用精确 API 权限编码列表
     */
    private List<String> apiPermissions;

    /**
     * 数据授权文档原始结构（null 表示无数据授权）
     */
    private Map<String, Object> dataGrantDocument;

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
