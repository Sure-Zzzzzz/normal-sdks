package io.github.surezzzzzz.sdk.auth.aksk.openapi.client.model;

import java.util.List;

/**
 * 创建 Client 请求（type=platform 用户级时填 ownerUserId/ownerUsername；scopes 可选）
 *
 * @author surezzzzzz
 */
public class CreateClientRequest {

    /**
     * Client 类型：platform（平台级 AKP）/ user（用户级 AKU）
     */
    private String type;

    /**
     * Client 名称
     */
    private String name;

    /**
     * 用户级归属主体标识（平台级省略）
     */
    private String ownerUserId;

    /**
     * 用户级归属展示名（平台级省略）
     */
    private String ownerUsername;

    /**
     * OAuth scope 列表（可选，只能缩小）
     */
    private List<String> scopes;


    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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

    public List<String> getScopes() {
        return scopes;
    }

    public void setScopes(List<String> scopes) {
        this.scopes = scopes;
    }
}
