package io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.response;

import lombok.Builder;
import lombok.Data;

/**
 * AKU 所属人授权继承开关的管理面响应。
 */
@Data
@Builder
public class OwnerInheritanceResponse {

    private Long applicationId;
    private Boolean enabled;
    private Long applicationAuthorizationEpoch;
}
