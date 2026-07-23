package io.github.surezzzzzz.sdk.kms.core.support;

import io.github.surezzzzzz.sdk.kms.core.constant.SmartKmsCoreConstant;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsIdempotencyConflictException;
import io.github.surezzzzzz.sdk.kms.core.model.KmsIdempotencyRecord;

/**
 * KMS 幂等判定工具。
 *
 * <p>幂等记录按 tenant、主体、endpoint 和幂等键共同隔离。只有这些维度相同且请求摘要一致时
 * 才能重放；同一隔离维度内摘要变化必须显式冲突，不能悄悄复用旧结果。</p>
 *
 * @author surezzzzzz
 */
public final class KmsIdempotencyHelper {

    private KmsIdempotencyHelper() {
        throw new UnsupportedOperationException(SmartKmsCoreConstant.MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE);
    }

    /**
     * 判断现有幂等记录是否可重放。
     *
     * @param record         已查询到的幂等记录，可为 {@code null}
     * @param tenantId       当前租户标识
     * @param principalId    当前主体标识
     * @param endpoint       管理操作端点标识
     * @param idempotencyKey 调用方提交的幂等键
     * @param requestHash    规范化请求摘要，不得是原始请求体
     * @return 存在同一隔离维度且摘要一致的记录时返回 {@code true}
     * @throws KmsIdempotencyConflictException 隔离维度相同但请求摘要不同
     */
    public static boolean isReplayable(KmsIdempotencyRecord record, String tenantId,
                                       String principalId, String endpoint,
                                       String idempotencyKey, String requestHash) {
        if (record == null) {
            return false;
        }
        if (!record.getTenantId().equals(tenantId)
                || !record.getPrincipalId().equals(principalId)
                || !record.getEndpoint().equals(endpoint)
                || !record.getIdempotencyKey().equals(idempotencyKey)) {
            return false;
        }
        if (!record.getRequestHash().equals(requestHash)) {
            throw new KmsIdempotencyConflictException();
        }
        return true;
    }
}
