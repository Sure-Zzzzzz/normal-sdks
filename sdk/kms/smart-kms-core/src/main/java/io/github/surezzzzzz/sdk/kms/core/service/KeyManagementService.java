package io.github.surezzzzzz.sdk.kms.core.service;

import io.github.surezzzzzz.sdk.kms.core.constant.KmsKeyState;
import io.github.surezzzzzz.sdk.kms.core.model.KmsKey;
import io.github.surezzzzzz.sdk.kms.core.model.KmsPrincipal;

import java.time.Instant;
import java.util.List;

/**
 * 密钥管理服务。
 *
 * <p>所有管理变更均从 {@link KmsPrincipal} 派生 tenant，必须携带幂等键并以行版本控制并发更新。
 * 状态迁移必须由服务端依据领域状态机校验；销毁排程和取消排程仍通过独立接口承载任务语义。</p>
 *
 * @author surezzzzzz
 */
public interface KeyManagementService {

    /**
     * 创建逻辑密钥及其首个活动版本。
     *
     * @param principal      已认证管理主体
     * @param key            待创建的逻辑密钥定义，不携带材料
     * @param idempotencyKey 管理变更幂等键
     * @param requestId      用于安全审计的请求标识
     * @return 已创建的逻辑密钥
     */
    KmsKey create(KmsPrincipal principal, KmsKey key, String idempotencyKey, String requestId);

    /**
     * 查询当前 tenant 下的逻辑密钥。
     *
     * @param principal 已认证管理主体
     * @param keyRef    逻辑密钥标识
     * @param requestId 用于安全审计的请求标识
     * @return 查询到的逻辑密钥
     */
    KmsKey find(KmsPrincipal principal, String keyRef, String requestId);

    /**
     * 查询当前 tenant 下的逻辑密钥集合。
     *
     * @param principal 已认证管理主体
     * @param requestId 用于安全审计的请求标识
     * @return 逻辑密钥集合
     */
    List<KmsKey> list(KmsPrincipal principal, String requestId);

    /**
     * 轮换逻辑密钥的活动版本。
     *
     * <p>实现必须在同一事务中创建新活动版本、退役旧活动版本并更新 activeVersion。</p>
     */
    KmsKey rotate(KmsPrincipal principal, String keyRef, long expectedRowVersion,
                  String idempotencyKey, String requestId);

    /**
     * 按领域状态机迁移逻辑密钥状态。
     *
     * <p>服务端必须拒绝非法迁移，并仅在销毁排程接口保障任务、逻辑密钥和各版本状态的一致更新。
     * 禁用后拒绝签名、加密和解密，但已发布的 ES256 公钥仍可读取并用于验签。</p>
     *
     * @param principal          已认证管理主体
     * @param keyRef             逻辑密钥标识
     * @param targetState        目标逻辑密钥状态
     * @param expectedRowVersion 预期行版本
     * @param idempotencyKey     管理变更幂等键
     * @param requestId          用于安全审计的请求标识
     * @return 已迁移状态的逻辑密钥
     */
    KmsKey changeState(KmsPrincipal principal, String keyRef, KmsKeyState targetState,
                       long expectedRowVersion, String idempotencyKey, String requestId);

    /**
     * 安排整个逻辑密钥销毁。
     *
     * <p>实现必须记录每个版本的排程前状态，并为全部非已销毁版本创建同一到期时间的销毁任务。</p>
     *
     * @param principal          已认证管理主体
     * @param keyRef             逻辑密钥标识
     * @param dueAt              销毁任务最早执行时间
     * @param expectedRowVersion 预期行版本
     * @param idempotencyKey     管理变更幂等键
     * @param requestId          用于安全审计的请求标识
     * @return 已安排销毁的逻辑密钥
     */
    KmsKey scheduleDestruction(KmsPrincipal principal, String keyRef, Instant dueAt,
                               long expectedRowVersion, String idempotencyKey, String requestId);

    /**
     * 取消尚未被 worker 成功领取的销毁任务。
     *
     * <p>只能恢复持久化的排程前状态，不能根据版本号或 activeVersion 猜测恢复状态。</p>
     */
    KmsKey cancelDestruction(KmsPrincipal principal, String keyRef, long expectedRowVersion,
                             String idempotencyKey, String requestId);
}
