package io.github.surezzzzzz.sdk.auth.iam.server.dto.resource.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.Map;

/**
 * IAM 资源令牌验证响应。
 *
 * @author surezzzzzz
 */
@Value
@Builder
public class ResourceTokenVerificationResponse {

    String sub;

    @JsonProperty("iam_authorization")
    Map<String, Object> iamAuthorization;
}
