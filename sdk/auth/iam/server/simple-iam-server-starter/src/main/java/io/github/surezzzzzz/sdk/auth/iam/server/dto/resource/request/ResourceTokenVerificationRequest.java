package io.github.surezzzzzz.sdk.auth.iam.server.dto.resource.request;

import lombok.Data;

/**
 * IAM 资源令牌验证请求。
 *
 * @author surezzzzzz
 */
@Data
public class ResourceTokenVerificationRequest {

    private String token;
}
