package io.github.surezzzzzz.sdk.auth.aksk.server.controller.response;

import lombok.Data;

/**
 * 创建Client响应
 *
 * @author surezzzzzz
 */
@Data
public class CreateClientResponse {
    /**
     * Client ID (Access Key)
     */
    private String clientId;

    /**
     * Client Secret (Secret Key)
     */
    private String clientSecret;

    /**
     * Client类型
     */
    private String type;

    /**
     * Client名称
     */
    private String name;
}
