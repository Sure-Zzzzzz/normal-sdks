package io.github.surezzzzzz.sdk.iam.feign.client.model;

/**
 * 权限清单 wire
 *
 * <p>Feign wire DTO：公开字段直配 Jackson，字段名逐字对位 server 契约。</p>
 *
 * @author surezzzzzz
 */
public class IamPermissionManifestResponse {

    /**
     * 版本。
     */
    public Long manifestVersion;

    /**
     * 摘要。
     */
    public String manifestDigest;

    /**
     * 页面权限码。
     */
    public java.util.List<String> pagePermissions;

    /**
     * API 权限码。
     */
    public java.util.List<String> apiPermissions;
}
