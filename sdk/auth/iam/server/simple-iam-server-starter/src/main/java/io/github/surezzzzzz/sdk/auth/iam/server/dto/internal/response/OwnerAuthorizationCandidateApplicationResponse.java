package io.github.surezzzzzz.sdk.auth.iam.server.dto.internal.response;

import lombok.Builder;
import lombok.Data;

/**
 * AKSK 创建 owner binding 时可选择的目标应用最小展示信息。
 */
@Data
@Builder
public class OwnerAuthorizationCandidateApplicationResponse {

    private Long applicationId;
    private String applicationName;
    private String applicationCodeSnapshot;
}
