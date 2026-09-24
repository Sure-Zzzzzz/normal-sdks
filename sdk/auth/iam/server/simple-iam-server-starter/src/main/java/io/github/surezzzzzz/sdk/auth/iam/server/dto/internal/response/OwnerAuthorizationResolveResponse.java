package io.github.surezzzzzz.sdk.auth.iam.server.dto.internal.response;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * owner 当前 IAM 授权投影读取结果。
 *
 * <p>active=false 时不附带授权快照或纪元，只给出 resumeAfterSequence 作为
 * AKSK 通过变更日志恢复本地投影的起点。</p>
 */
@Data
@Builder
public class OwnerAuthorizationResolveResponse {

    private boolean active;
    private Long ownerSecurityEpoch;
    private Long applicationAuthorizationEpoch;
    private Long ownerInheritedAccessEpoch;
    private Long projectionAccessEpoch;
    private Long resumeAfterSequence;
    /**
     * 当前 owner 的展示用户名；不参与绑定或授权判定。
     */
    private String ownerUsername;
    private Map<String, Object> iamAuthorization;

    public static OwnerAuthorizationResolveResponse inactive() {
        return OwnerAuthorizationResolveResponse.builder().active(false).build();
    }

    public static OwnerAuthorizationResolveResponse inactive(Long resumeAfterSequence) {
        return OwnerAuthorizationResolveResponse.builder()
                .active(false)
                .resumeAfterSequence(resumeAfterSequence)
                .build();
    }
}
