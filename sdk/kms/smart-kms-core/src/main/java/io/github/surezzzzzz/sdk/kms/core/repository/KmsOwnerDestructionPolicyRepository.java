package io.github.surezzzzzz.sdk.kms.core.repository;

import io.github.surezzzzzz.sdk.kms.core.model.KmsOwnerDestructionPolicy;

import java.util.Optional;

/**
 * owner 级销毁窗口政策仓储。
 *
 * @author surezzzzzz
 */
public interface KmsOwnerDestructionPolicyRepository {

    /**
     * 查询 owner 的销毁窗口政策。
     *
     * @param ownerPrincipalId owner 主体标识
     * @return 已存政策；无行时为空（能力态默认 = 不限制）
     */
    Optional<KmsOwnerDestructionPolicy> find(String ownerPrincipalId);

    /**
     * 写入（upsert）owner 的销毁窗口政策。
     *
     * @param policy 政策快照
     * @return 写入后的最新行（含新版本号）
     */
    KmsOwnerDestructionPolicy save(KmsOwnerDestructionPolicy policy);
}
