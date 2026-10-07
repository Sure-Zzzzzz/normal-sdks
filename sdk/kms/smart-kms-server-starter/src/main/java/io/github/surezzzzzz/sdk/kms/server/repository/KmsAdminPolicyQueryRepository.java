package io.github.surezzzzzz.sdk.kms.server.repository;

import io.github.surezzzzzz.sdk.kms.server.model.KmsOwnerAccessScope;

/**
 * 治理视角跨钥策略分页查询端口。
 *
 * <p>只读端口，聚合策略与所属逻辑密钥元数据，不参与策略写链。</p>
 *
 * @author surezzzzzz
 */
public interface KmsAdminPolicyQueryRepository {

    /**
     * 按已验证 DataPlan 归属范围分页查询策略。
     *
     * @param scope       已验证的数据访问范围
     * @param keyAlias    可选密钥别名片段
     * @param principalId 可选被授权主体精确匹配
     * @param operation   可选操作编码精确匹配
     * @param offset      从零开始的分页偏移
     * @param size        当前页最大记录数
     * @return 附带密钥别名的策略分页
     */
    KmsAdminPolicyPage findPage(KmsOwnerAccessScope scope, String keyAlias, String principalId, String operation,
                                long offset, int size);
}
