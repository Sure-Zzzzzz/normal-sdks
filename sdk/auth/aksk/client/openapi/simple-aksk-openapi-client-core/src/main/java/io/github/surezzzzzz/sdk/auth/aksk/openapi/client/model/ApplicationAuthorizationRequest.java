package io.github.surezzzzzz.sdk.auth.aksk.openapi.client.model;

import java.util.List;
import java.util.Map;

/**
 * 应用授权创建/完整替换请求（完整替换会事务性撤销该 Client 全部活跃 Token）
 *
 * @author surezzzzzz
 */
public class ApplicationAuthorizationRequest {

    /**
     * 目标应用编码
     */
    private String applicationCode;

    /**
     * 准入开关（true=允许签发应用授权快照）
     */
    private Boolean admitted;

    /**
     * 角色列表
     */
    private List<String> roles;

    /**
     * 页面权限码列表
     */
    private List<String> pagePermissions;

    /**
     * 精确 API permission 码列表
     */
    private List<String> apiPermissions;

    /**
     * DATA 授权文档
     */
    private Map<String, Object> dataGrantDocument;

    /**
     * 清单版本
     */
    private String manifestVersion;

    /**
     * 清单摘要
     */
    private String manifestDigest;


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

    public Map<String, Object> getDataGrantDocument() {
        return dataGrantDocument;
    }

    public void setDataGrantDocument(Map<String, Object> dataGrantDocument) {
        this.dataGrantDocument = dataGrantDocument;
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
}
