package io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest.response;

import io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest.DataResourceDeclaration;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

/**
 * 可信应用权限清单响应
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class ApplicationPermissionManifestResponse {

    /**
     * 可信应用ID
     */
    private Long applicationId;

    /**
     * 应用局部角色编码列表
     */
    private List<String> roles;

    /**
     * 应用页面权限编码列表
     */
    private List<String> pagePermissions;

    /**
     * 应用接口权限编码列表
     */
    private List<String> apiPermissions;

    /**
     * DATA 资源声明列表
     */
    private List<DataResourceDeclaration> dataResources;

    /**
     * 清单版本（服务端单调递增）
     */
    private Long manifestVersion;

    /**
     * 清单内容摘要（SHA-256）
     */
    private String manifestDigest;

    /**
     * 创建时间
     */
    private Instant createdAt;

    /**
     * 最近更新时间
     */
    private Instant updatedAt;
}
