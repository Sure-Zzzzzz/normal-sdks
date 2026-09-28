package io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.model;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.claim.ApplicationAuthorizationContextClaimMapper;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.constant.SimpleOwnerAuthorizationCollaborationConstant;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.support.OwnerAuthorizationModelHelper;
import lombok.AccessLevel;
import lombok.Getter;

import java.util.Map;

/**
 * 所属人授权投影读取结果。
 *
 * <p>provider 对所属人当前最终授权投影的中性读取结果。</p>
 *
 * @author surezzzzzz
 */
@Getter
public final class OwnerAuthorizationReadResult {

    /**
     * 投影是否有效。
     */
    private final boolean active;
    /**
     * 所属人安全纪元。
     */
    private final Long ownerSecurityEpoch;
    /**
     * 应用授权纪元。
     */
    private final Long applicationAuthorizationEpoch;
    /**
     * 所属人继承访问纪元。
     */
    private final Long ownerInheritedAccessEpoch;
    /**
     * 投影访问纪元。
     */
    private final Long projectionAccessEpoch;
    /**
     * 变更消费恢复位点。
     */
    private final Long resumeAfterSequence;
    /**
     * 所属人登录名快照（管理端归属展示用，不得作为稳定授权键）。
     * 必填：投影有效时缺失本字段，消费方按失败关闭整体拒绝。
     */
    private final String ownerUsername;
    /**
     * 所属人在目标应用下的授权上下文（claim 形态）。
     * 键契约由 simple-application-authorization-core 的 ClaimMapper 单一定义，两侧共享。
     */
    private final Map<String, Object> authorization;
    /**
     * 已验证的强类型授权上下文，不作为协作 wire 字段序列化。
     */
    @Getter(AccessLevel.NONE)
    private final ApplicationAuthorizationContext authorizationContext;

    /**
     * 创建读取结果。
     *
     * @param active                        投影是否有效
     * @param ownerSecurityEpoch            所属人安全纪元
     * @param applicationAuthorizationEpoch 应用授权纪元
     * @param ownerInheritedAccessEpoch     所属人继承访问纪元
     * @param projectionAccessEpoch         投影访问纪元
     * @param resumeAfterSequence           变更消费恢复位点
     * @param ownerUsername                 所属人登录名（必填，见字段契约）
     * @param authorization                 授权上下文（claim 形态，见字段契约）
     */
    public OwnerAuthorizationReadResult(boolean active, Long ownerSecurityEpoch,
                                        Long applicationAuthorizationEpoch,
                                        Long ownerInheritedAccessEpoch,
                                        Long projectionAccessEpoch,
                                        Long resumeAfterSequence,
                                        String ownerUsername,
                                        Map<String, Object> authorization) {
        this.active = active;
        if (!active) {
            this.ownerSecurityEpoch = null;
            this.applicationAuthorizationEpoch = null;
            this.ownerInheritedAccessEpoch = null;
            this.projectionAccessEpoch = null;
            this.resumeAfterSequence = resumeAfterSequence == null ? null
                    : OwnerAuthorizationModelHelper.requireNonNegative(resumeAfterSequence,
                    SimpleOwnerAuthorizationCollaborationConstant.FIELD_RESUME_AFTER_SEQUENCE);
            this.ownerUsername = null;
            this.authorization = null;
            this.authorizationContext = null;
            return;
        }
        this.ownerSecurityEpoch = OwnerAuthorizationModelHelper.requirePositive(ownerSecurityEpoch,
                SimpleOwnerAuthorizationCollaborationConstant.FIELD_OWNER_SECURITY_EPOCH);
        this.applicationAuthorizationEpoch = OwnerAuthorizationModelHelper.requirePositive(applicationAuthorizationEpoch,
                SimpleOwnerAuthorizationCollaborationConstant.FIELD_APPLICATION_AUTHORIZATION_EPOCH);
        this.ownerInheritedAccessEpoch = OwnerAuthorizationModelHelper.requirePositive(ownerInheritedAccessEpoch,
                SimpleOwnerAuthorizationCollaborationConstant.FIELD_OWNER_INHERITED_ACCESS_EPOCH);
        this.projectionAccessEpoch = OwnerAuthorizationModelHelper.requirePositive(projectionAccessEpoch,
                SimpleOwnerAuthorizationCollaborationConstant.FIELD_PROJECTION_ACCESS_EPOCH);
        this.resumeAfterSequence = OwnerAuthorizationModelHelper.requireNonNegative(resumeAfterSequence,
                SimpleOwnerAuthorizationCollaborationConstant.FIELD_RESUME_AFTER_SEQUENCE);
        this.ownerUsername = OwnerAuthorizationModelHelper.requireText(ownerUsername,
                SimpleOwnerAuthorizationCollaborationConstant.FIELD_OWNER_USERNAME);
        if (authorization == null) {
            throw OwnerAuthorizationModelHelper.invalidModel(String.format(
                    SimpleOwnerAuthorizationCollaborationConstant.DETAIL_FIELD_CANNOT_BE_NULL,
                    SimpleOwnerAuthorizationCollaborationConstant.FIELD_AUTHORIZATION));
        }
        Map<String, Object> frozenAuthorization = OwnerAuthorizationModelHelper.freezePayload(authorization);
        try {
            this.authorizationContext = ApplicationAuthorizationContextClaimMapper.fromClaim(frozenAuthorization);
            this.authorization = OwnerAuthorizationModelHelper.freezePayload(
                    ApplicationAuthorizationContextClaimMapper.toClaim(authorizationContext));
        } catch (RuntimeException exception) {
            throw OwnerAuthorizationModelHelper.invalidModel(
                    SimpleOwnerAuthorizationCollaborationConstant.DETAIL_AUTHORIZATION_CLAIM_INVALID, exception);
        }
    }

    /**
     * 创建无效（失败关闭）读取结果。
     *
     * @return 无效读取结果
     */

    public static OwnerAuthorizationReadResult inactive() {
        return new OwnerAuthorizationReadResult(false, null, null, null, null, null, null, null);
    }

    /**
     * 创建无效（失败关闭）但携带变更消费恢复位点的读取结果。
     *
     * <p>失效投影不借纪元等字段外泄旧值，只告知调用方从哪条变更日志恢复。</p>
     *
     * @param resumeAfterSequence 变更消费恢复位点
     * @return 无效读取结果
     */
    public static OwnerAuthorizationReadResult inactive(Long resumeAfterSequence) {
        return new OwnerAuthorizationReadResult(false, null, null, null, null, resumeAfterSequence, null, null);
    }

    /**
     * 返回已验证的强类型授权上下文。
     *
     * @return 有效投影的授权上下文；无效投影返回 null
     */
    public ApplicationAuthorizationContext toApplicationAuthorizationContext() {
        return authorizationContext;
    }

}
