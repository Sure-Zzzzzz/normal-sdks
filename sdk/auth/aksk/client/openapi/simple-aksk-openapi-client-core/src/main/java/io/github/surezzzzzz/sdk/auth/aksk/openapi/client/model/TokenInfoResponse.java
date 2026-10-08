package io.github.surezzzzzz.sdk.auth.aksk.openapi.client.model;

import java.util.List;

/**
 * Token 详情响应
 *
 * @author surezzzzzz
 */
public class TokenInfoResponse {

    /**
     * Token 标识
     */
    private String id;

    /**
     * 注册 Client 标识
     */
    private String registeredClientId;

    /**
     * Client 标识
     */
    private String clientId;

    /**
     * Client 名称
     */
    private String clientName;

    /**
     * Client 类型码
     */
    private Integer clientType;

    /**
     * 签发时间（wire 原文）
     */
    private String issuedAt;

    /**
     * 过期时间（wire 原文）
     */
    private String expiresAt;

    /**
     * scope 列表
     */
    private List<String> scopes;

    /**
     * 状态（wire 原文枚举名）
     */
    private String status;

    /**
     * 数据来源（wire 原文枚举名：mysql/redis/both）
     */
    private String dataSource;

    /**
     * 归属主体标识
     */
    private String ownerUserId;

    /**
     * 归属展示名
     */
    private String ownerUsername;


    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRegisteredClientId() {
        return registeredClientId;
    }

    public void setRegisteredClientId(String registeredClientId) {
        this.registeredClientId = registeredClientId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
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

    public String getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(String issuedAt) {
        this.issuedAt = issuedAt;
    }

    public String getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(String expiresAt) {
        this.expiresAt = expiresAt;
    }

    public List<String> getScopes() {
        return scopes;
    }

    public void setScopes(List<String> scopes) {
        this.scopes = scopes;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDataSource() {
        return dataSource;
    }

    public void setDataSource(String dataSource) {
        this.dataSource = dataSource;
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
