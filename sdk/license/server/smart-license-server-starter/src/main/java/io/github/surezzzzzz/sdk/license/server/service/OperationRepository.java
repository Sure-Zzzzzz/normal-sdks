package io.github.surezzzzzz.sdk.license.server.service;

import io.github.surezzzzzz.sdk.license.server.entity.SigningOperationEntity;

/**
 * 签发任务仓储端口（顶层接口：JPA 仓储代理对嵌套接口的注入在此前场景不可见）。
 *
 * @author surezzzzzz
 */
public interface OperationRepository {

    void save(SigningOperationEntity entity);

    SigningOperationEntity find(String operationId);

    /** 受理唯一竞争失败后回读已提交首任务；无该能力的旧适配器只会透传冲突，不得创建替代任务。 */
    default SigningOperationEntity findByIdempotencyDigest(String idempotencyDigest) {
        return null;
    }

    void update(SigningOperationEntity entity);

    /** 受控恢复更改代数时，用更改前代数认领；生产持久化必须同时核对行版本与该代数。 */
    default void update(SigningOperationEntity entity, long expectedExecutionGeneration) {
        // 旧适配器不能默认为已具备跨代数CAS能力；缺实现时拒绝恢复而不是静默覆盖。
        throw new UnsupportedOperationException(
                io.github.surezzzzzz.sdk.license.server.constant.SigningOperationConstant.CAS_UNSUPPORTED);
    }
}
