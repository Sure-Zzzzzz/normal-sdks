package io.github.surezzzzzz.sdk.auth.iam.server.dto.internal.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * AKSK 读取既有 owner binding 的当前 IAM 授权投影请求。
 *
 * <p>targetApplicationId 必须来自 AKSK 持久 binding，浏览器不得直接构造此请求。</p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = false)
public class OwnerAuthorizationResolveRequest {

    @NotBlank
    @Size(max = 64)
    private String ownerSourceId;

    @NotBlank
    @Size(max = 128)
    private String ownerSubjectId;

    @NotNull
    private Long targetApplicationId;
}
