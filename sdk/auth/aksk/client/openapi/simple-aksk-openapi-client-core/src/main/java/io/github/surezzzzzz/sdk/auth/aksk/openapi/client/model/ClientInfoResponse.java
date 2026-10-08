package io.github.surezzzzzz.sdk.auth.aksk.openapi.client.model;

import java.util.List;

/**
 * Client 详情响应（server ClientInfo 全字段 + 自助扩展两字段投影）
 *
 * @author surezzzzzz
 */
public class ClientInfoResponse {

    /**
     * Client 标识
     */
    private String clientId;

    /**
     * Client 密钥（管理查询通常不填充；出现时同样按敏感值处理）
     */
    private String clientSecret;

    /**
     * Client 名称
     */
    private String clientName;

    /**
     * Client 类型码
     */
    private Integer clientType;

    /**
     * 归属主体标识
     */
    private String ownerUserId;

    /**
     * 归属展示名
     */
    private String ownerUsername;

    /**
     * scope 列表
     */
    private List<String> scopes;

    /**
     * 启用状态
     */
    private boolean enabled;

    /**
     * 签发时间（wire 原文，UTC 毫秒字符串）
     */
    private String clientIdIssuedAt;

    /**
     * 并发生命周期版本（仅自助接口填充）
     */
    private Long lifecycleVersion;

    /**
     * 自助 AKU 绑定的目标业务应用（仅自助接口填充）
     */
    private Long targetApplicationId;


    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
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

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public void setOwnerUsername(String ownerUsername) {
        this.ownerUsername = ownerUsername;
    }

    public List<String> getScopes() {
        return scopes;
    }

    public void setScopes(List<String> scopes) {
        this.scopes = scopes;
    }

    public boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getClientIdIssuedAt() {
        return clientIdIssuedAt;
    }

    public void setClientIdIssuedAt(String clientIdIssuedAt) {
        this.clientIdIssuedAt = clientIdIssuedAt;
    }

    public Long getLifecycleVersion() {
        return lifecycleVersion;
    }

    public void setLifecycleVersion(Long lifecycleVersion) {
        this.lifecycleVersion = lifecycleVersion;
    }

    public Long getTargetApplicationId() {
        return targetApplicationId;
    }

    public void setTargetApplicationId(Long targetApplicationId) {
        this.targetApplicationId = targetApplicationId;
    }
}
