package io.github.surezzzzzz.sdk.kms.core.service;

import io.github.surezzzzzz.sdk.kms.core.model.KmsKeyPolicy;
import io.github.surezzzzzz.sdk.kms.core.model.KmsPrincipal;

import java.util.List;

/**
 * 密钥策略管理服务。
 *
 * <p>策略只表达当前 tenant 下某主体、密钥、可选单版本和操作的精确 allow 授权；创建与撤销均为
 * 管理变更，必须在状态、幂等和审计提交后才可返回成功。</p>
 *
 * @author surezzzzzz
 */
public interface KeyPolicyManagementService {

    /**
     * 创建精确 allow-only 密钥策略。
     */
    KmsKeyPolicy create(KmsPrincipal principal, KmsKeyPolicy policy, String idempotencyKey,
                        String requestId);

    /**
     * 查询当前 tenant 和逻辑密钥下的全部策略。
     */
    List<KmsKeyPolicy> list(KmsPrincipal principal, String keyRef, String requestId);

    /**
     * 撤销指定策略。
     *
     * <p>实现必须与同一 key 的密码学操作共享线性化锁，确保撤销提交后不会再产生新获授权操作。</p>
     */
    void revoke(KmsPrincipal principal, String keyRef, String policyId, long expectedRowVersion,
                String idempotencyKey, String requestId);
}
