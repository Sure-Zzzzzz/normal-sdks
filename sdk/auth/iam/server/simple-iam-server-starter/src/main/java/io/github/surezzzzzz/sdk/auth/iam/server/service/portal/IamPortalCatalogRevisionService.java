package io.github.surezzzzzz.sdk.auth.iam.server.service.portal;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.portal.IamPortalCatalogRevisionEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.portal.IamPortalCatalogRevisionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;

/**
 * Portal 可信应用路由投影 revision 管理。
 *
 * <p>目录可见集合或路由配置提交后调用 {@link #bump()}；读取端只把 revision 当作快照版本，
 * 不把它与单应用 configVersion 或用户 Portal 版本混用。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class IamPortalCatalogRevisionService {

    private static final Object BUMP_RESOURCE_KEY = new Object();

    private final IamPortalCatalogRevisionRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 读取当前目录版本。
     *
     * @return 当前目录 revision
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public long current() {
        IamPortalCatalogRevisionEntity entity = repository.findById(IamPortalCatalogRevisionEntity.SINGLETON_ID)
                .orElseThrow(this::missingRevision);
        return entity.getRevision();
    }

    /**
     * 在同一业务事务内推进目录版本。
     *
     * @return 新 revision
     */
    @Transactional
    public long bump() {
        return bump("PORTAL_CATALOG_CHANGED", null);
    }

    /**
     * 推进目录版本并发布内部变更事件。事件在事务提交后由监听器消费。
     */
    @Transactional
    public long bump(String changeType, String applicationCode) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.hasResource(BUMP_RESOURCE_KEY)) {
            return ((BumpState) TransactionSynchronizationManager.getResource(BUMP_RESOURCE_KEY)).revision;
        }
        IamPortalCatalogRevisionEntity entity = repository
                .findByIdForUpdate(IamPortalCatalogRevisionEntity.SINGLETON_ID);
        if (entity == null) {
            throw missingRevision();
        }
        entity.setRevision(entity.getRevision() + 1L);
        entity.setUpdatedAt(Instant.now());
        IamPortalCatalogRevisionEntity saved = repository.saveAndFlush(entity);
        // 先登记事务内状态，再发布事件；若同步监听器触发嵌套 bump，也必须复用本事务 revision。
        registerBumpState(saved.getRevision());
        eventPublisher.publishEvent(new IamPortalCatalogRevisionBumpedEvent(
                saved.getRevision() - 1L, saved.getRevision(), changeType,
                IamPortalCatalogRevisionBumpedEvent.digest(applicationCode), correlationId()));
        log.info("Portal 可信应用路由投影 revision 已推进：revision={}, changeType={}",
                saved.getRevision(), changeType);
        return saved.getRevision();
    }

    private void registerBumpState(long revision) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.bindResource(BUMP_RESOURCE_KEY, new BumpState(revision));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (TransactionSynchronizationManager.hasResource(BUMP_RESOURCE_KEY)) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(BUMP_RESOURCE_KEY);
                }
            }
        });
    }

    private String correlationId() {
        String value = MDC.get("correlationId");
        if (value == null || value.trim().isEmpty()) {
            value = MDC.get("traceId");
        }
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private SimpleIamServerException missingRevision() {
        return new SimpleIamServerException("Portal 可信应用路由投影 revision 缺失");
    }

    private static final class BumpState {
        private final long revision;

        private BumpState(long revision) {
            this.revision = revision;
        }
    }
}
