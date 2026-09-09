package io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.response;

import lombok.Builder;
import lombok.Getter;

/**
 * 创建 / 重置可信应用客户端密钥响应
 *
 * <p>明文 secret 仅在此响应中返回一次，列表 / 详情不会回显。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class TrustedApplicationClientSecretResponse {

    /**
     * 客户端主键ID
     */
    private String id;

    /**
     * 客户端ID
     */
    private String clientId;

    /**
     * 明文 secret（仅本次返回）
     */
    private String clientSecret;
}
