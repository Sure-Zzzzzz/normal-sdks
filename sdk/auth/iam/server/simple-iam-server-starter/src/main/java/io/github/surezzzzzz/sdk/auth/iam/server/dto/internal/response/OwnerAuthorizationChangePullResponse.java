package io.github.surezzzzzz.sdk.auth.iam.server.dto.internal.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * IAM 授权变更日志拉取结果。
 */
@Data
@Builder
public class OwnerAuthorizationChangePullResponse {

    /**
     * true 表示 afterSequence 已被保留策略清理，调用方必须执行全量修复。
     */
    private boolean resyncRequired;
    private Long oldestAvailableSequence;
    private Long highWaterSequence;
    private List<OwnerAuthorizationChangeResponse> changes;
}
