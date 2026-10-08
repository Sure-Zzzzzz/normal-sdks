package io.github.surezzzzzz.sdk.auth.aksk.openapi.client.model;

/**
 * 创建 Client 响应（clientSecret 仅此一次返回，调用方必须立即落受保护配置）
 *
 * @author surezzzzzz
 */
public class CreateClientResponse {

    /**
     * Client 标识（AK）
     */
    private String clientId;

    /**
     * Client 密钥（SK，仅此一次）
     */
    private String clientSecret;

    /**
     * Client 类型
     */
    private String type;

    /**
     * Client 名称
     */
    private String name;


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
}
