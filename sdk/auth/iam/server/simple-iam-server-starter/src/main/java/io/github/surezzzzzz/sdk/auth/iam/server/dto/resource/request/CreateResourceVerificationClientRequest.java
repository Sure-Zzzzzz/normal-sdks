package io.github.surezzzzzz.sdk.auth.iam.server.dto.resource.request;

import lombok.Data;

/**
 * 创建资源验证客户端请求
 *
 * <p>secret 由服务端生成并在创建响应中一次性返回，请求不携带任何密钥。
 *
 * @author surezzzzzz
 */
@Data
public class CreateResourceVerificationClientRequest {

    /**
     * 客户端ID（不能为空、不超过 100 字符、不能包含冒号——Basic 凭据按冒号分隔）
     */
    private String clientId;
}
