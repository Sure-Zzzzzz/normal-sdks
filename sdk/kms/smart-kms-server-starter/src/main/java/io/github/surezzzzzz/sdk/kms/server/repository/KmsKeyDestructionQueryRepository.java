package io.github.surezzzzzz.sdk.kms.server.repository;

import io.github.surezzzzzz.sdk.kms.server.model.KmsKeyDestructionDetails;

import java.util.Optional;

/**
 * 密钥级无材料销毁详情查询端口，不参与领取或取消写链。
 *
 * @author surezzzzzz
 */
public interface KmsKeyDestructionQueryRepository {

    /**
     * 查询明确归属内单个密钥的同一时点销毁明细。
     *
     * @param ownerPrincipalId 认证归属或已通过 DATA 校验的目标归属
     * @param keyRef           逻辑密钥标识
     * @return 存在时返回快照，无密钥时为空；无任务不是无密钥
     */
    Optional<KmsKeyDestructionDetails> findDetails(String ownerPrincipalId, String keyRef);
}
