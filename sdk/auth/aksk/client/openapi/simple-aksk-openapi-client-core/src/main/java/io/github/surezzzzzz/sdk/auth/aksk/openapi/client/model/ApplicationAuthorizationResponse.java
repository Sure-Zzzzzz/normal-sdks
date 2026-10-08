package io.github.surezzzzzz.sdk.auth.aksk.openapi.client.model;

import java.util.List;

/**
 * 应用授权响应
 *
 * @author surezzzzzz
 */
public class ApplicationAuthorizationResponse {

    /**
     * Client 标识
     */
    private String clientId;

    /**
     * Client 类型码
     */
    private Integer clientType;

    /**
     * 归属主体标识
     */
    private String ownerUserId;

    /**
     * 目标应用编码
     */
    private String applicationCode;

    /**
     * 准入状态
     */
    private Boolean admitted;

    /**
     * 启用状态
     */
    private Boolean enabled;

    /**
     * 角色列表
     */
    private List<String> roles;

    /**
     * 页面权限码列表
     */
    private List<String> pagePermissions;

    /**
     * API permission 码列表
     */
    private List<String> apiPermissions;

    /**
     * 授权版本（单调递增）
     */
    private Long authorizationVersion;

    /**
     * 清单版本
     */
    private String manifestVersion;

    /**
     * 清单摘要
     */
    private String manifestDigest;

    /**
     * 创建时间（wire 原文）
     */
    private String createdAt;

    /**
     * 更新时间（wire 原文）
     */
    private String updatedAt;

    /**
     * 撤销时间（wire 原文；未撤销为 null）
     */
    private String revokedAt;


    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public Integer getClientType() {
        return clientType;
    }

    public void setClientType(Integer clientType) {
        this.clientType = clientType;
    }

    public String getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(String ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getApplicationCode() {
        return applicationCode;
    }

    public void setApplicationCode(String applicationCode) {
        this.applicationCode = applicationCode;
    }

    public Boolean getAdmitted() {
        return admitted;
    }

    public void setAdmitted(Boolean admitted) {
        this.admitted = admitted;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public List<String> getPagePermissions() {
        return pagePermissions;
    }

    public void setPagePermissions(List<String> pagePermissions) {
        this.pagePermissions = pagePermissions;
    }

    public List<String> getApiPermissions() {
        return apiPermissions;
    }

    public void setApiPermissions(List<String> apiPermissions) {
        this.apiPermissions = apiPermissions;
    }

    public Long getAuthorizationVersion() {
        return authorizationVersion;
    }

    public void setAuthorizationVersion(Long authorizationVersion) {
        this.authorizationVersion = authorizationVersion;
    }

    public String getManifestVersion() {
        return manifestVersion;
    }

    public void setManifestVersion(String manifestVersion) {
        this.manifestVersion = manifestVersion;
    }

    public String getManifestDigest() {
        return manifestDigest;
    }

    public void setManifestDigest(String manifestDigest) {
        this.manifestDigest = manifestDigest;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(String revokedAt) {
        this.revokedAt = revokedAt;
    }
}
