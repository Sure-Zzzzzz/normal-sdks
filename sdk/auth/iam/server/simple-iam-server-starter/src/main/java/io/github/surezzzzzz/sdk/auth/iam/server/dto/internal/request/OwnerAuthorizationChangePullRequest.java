package io.github.surezzzzzz.sdk.auth.iam.server.dto.internal.request;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * AKSK 从 IAM 拉取授权变更日志的请求。
 */
@Data
public class OwnerAuthorizationChangePullRequest {

    /**
     * 已成功持久化的最后一条 sourceSequence；首次拉取传 0。
     */
    @NotNull
    @Min(0L)
    private Long afterSequence;

    /**
     * 单次最大条数；服务端仍会执行固定上限保护。
     */
    @NotNull
    @Min(1L)
    @Max(200L)
    private Integer pageSize;
}
