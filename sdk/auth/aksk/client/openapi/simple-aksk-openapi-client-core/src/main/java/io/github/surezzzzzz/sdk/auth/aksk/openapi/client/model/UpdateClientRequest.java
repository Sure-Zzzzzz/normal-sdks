package io.github.surezzzzzz.sdk.auth.aksk.openapi.client.model;

import java.util.List;

/**
 * 更新 Client 请求（PATCH；body 含 name 即改名，enabled 即启停——覆盖 server 两种 PATCH body 形态）
 *
 * @author surezzzzzz
 */
public class UpdateClientRequest {

    /**
     * 启用/停用
     */
    private Boolean enabled;

    /**
     * scope 列表
     */
    private List<String> scopes;

    /**
     * 新名称（改名）
     */
    private String name;

    /**
     * 归属主体标识
     */
    private String ownerUserId;

    /**
     * 归属展示名
     */
    private String ownerUsername;


    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getScopes() {
        return scopes;
    }

    public void setScopes(List<String> scopes) {
        this.scopes = scopes;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(String ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public void setOwnerUsername(String ownerUsername) {
        this.ownerUsername = ownerUsername;
    }
}
