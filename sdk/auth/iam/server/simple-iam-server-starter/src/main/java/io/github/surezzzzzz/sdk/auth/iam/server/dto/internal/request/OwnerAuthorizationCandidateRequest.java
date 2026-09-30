package io.github.surezzzzzz.sdk.auth.iam.server.dto.internal.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 协作消费方创建 owner binding 前查询可选目标应用的请求。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = false)
public class OwnerAuthorizationCandidateRequest {

    @NotBlank
    @Size(max = 64)
    private String ownerSourceId;

    @NotBlank
    @Size(max = 128)
    private String ownerSubjectId;
}
