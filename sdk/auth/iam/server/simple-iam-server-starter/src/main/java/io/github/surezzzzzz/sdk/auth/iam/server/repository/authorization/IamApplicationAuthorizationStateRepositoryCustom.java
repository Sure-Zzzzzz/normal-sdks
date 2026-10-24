package io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization;

/**
 * IAM 可信应用 AKU 授权状态的原生写入扩展。
 */
public interface IamApplicationAuthorizationStateRepositoryCustom {

    /**
     * 并发安全地创建初始状态；唯一键冲突时保留已有状态。
     *
     * @param applicationId 可信应用 ID
     * @return 受影响的行数
     */
    int insertIfAbsent(Long applicationId);
}
