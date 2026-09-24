package io.github.surezzzzzz.sdk.auth.iam.server.dto.internal.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * owner 可创建 AKU 的目标应用目录。
 */
@Data
@Builder
public class OwnerAuthorizationCandidateResponse {

    private List<OwnerAuthorizationCandidateApplicationResponse> items;
}
