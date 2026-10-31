package io.github.surezzzzzz.sdk.license.server.service;

import io.github.surezzzzzz.sdk.license.server.annotation.SimpleLicenseServerComponent;
import io.github.surezzzzzz.sdk.license.server.entity.SigningOperationEntity;
import io.github.surezzzzzz.sdk.license.server.repository.SigningOperationRepository;
import io.github.surezzzzzz.sdk.license.server.constant.SigningOperationConstant;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * OperationRepository 的 JPA 生产实现
 * （测试用内存实现经 @ConditionalOnMissingBean 让位）。
 *
 * @author surezzzzzz
 */
@lombok.extern.slf4j.Slf4j
@SimpleLicenseServerComponent
public class JpaOperationRepository implements OperationRepository {

    private final SigningOperationRepository repository;

    public JpaOperationRepository(SigningOperationRepository repository) {
        this.repository = repository;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void save(SigningOperationEntity entity) {
        SigningOperationEntity saved = repository.saveAndFlush(entity);
        entity.setRowVersion(saved.getRowVersion());
    }

    @Override
    public SigningOperationEntity find(String operationId) {
        return repository.findById(operationId).map(JpaOperationRepository::snapshot).orElse(null);
    }

    @Override
    public SigningOperationEntity findByIdempotencyDigest(String idempotencyDigest) {
        SigningOperationEntity entity = repository.findByIdempotencyDigest(idempotencyDigest);
        return entity == null ? null : snapshot(entity);
    }

    @Override
    public void update(SigningOperationEntity entity) {
        update(entity, entity.getExecutionGeneration());
    }

    @Override
    public void update(SigningOperationEntity entity, long expectedExecutionGeneration) {
        long expectedVersion = entity.getRowVersion();
        int updated = repository.compareAndSet(entity.getOperationId(), expectedVersion,
                expectedExecutionGeneration, entity.getState(), entity.getExecutionGeneration(),
                entity.getCommittedSignature(), entity.getReasonCategory(), entity.getLastKmsCallRef(),
                entity.getLastKmsCallAt());
        if (updated != 1) {
            log.debug("签发任务CAS未命中：operationId={}，expectedRowVersion={}，expectedGeneration={}",
                    entity.getOperationId(), expectedVersion, expectedExecutionGeneration);
            throw new OptimisticLockingFailureException(SigningOperationConstant.VERSION_CONFLICT);
        }
        entity.setRowVersion(expectedVersion + 1);
        log.debug("签发任务CAS已提交：operationId={}，rowVersion={}，generation={}，state={}",
                entity.getOperationId(), entity.getRowVersion(), entity.getExecutionGeneration(), entity.getState());
    }

    /** 返回独立冻结快照，防宿主启用OSIV时业务内存修改被JPA提前自动刷新而绕过CAS。 */
    private static SigningOperationEntity snapshot(SigningOperationEntity source) {
        SigningOperationEntity target = new SigningOperationEntity(source.getOperationId(), source.getCredentialId(),
                source.getGrantId(), source.getSigningProfileId(), source.getFrozenHeader(), source.getFrozenPayload(),
                source.getIdempotencyDigest());
        target.setRowVersion(source.getRowVersion());
        target.setState(source.getState());
        target.setExecutionGeneration(source.getExecutionGeneration());
        target.setCommittedSignature(source.getCommittedSignature());
        target.setReasonCategory(source.getReasonCategory());
        target.setLastKmsCallRef(source.getLastKmsCallRef());
        target.setLastKmsCallAt(source.getLastKmsCallAt());
        return target;
    }
}
