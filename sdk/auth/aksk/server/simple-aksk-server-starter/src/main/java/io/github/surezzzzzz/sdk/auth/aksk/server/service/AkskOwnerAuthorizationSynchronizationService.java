package io.github.surezzzzzz.sdk.auth.aksk.server.service;

import io.github.surezzzzzz.sdk.auth.aksk.server.annotation.SimpleAkskServerComponent;
import io.github.surezzzzzz.sdk.auth.aksk.server.configuration.SimpleAkskServerProperties;
import io.github.surezzzzzz.sdk.auth.aksk.server.entity.AkskClientOwnerBindingEntity;
import io.github.surezzzzzz.sdk.auth.aksk.server.entity.AkskOwnerAuthorizationMode;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.AkskClientOwnerBindingRepository;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.model.OwnerAuthorizationChange;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.model.OwnerAuthorizationChangePage;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.model.OwnerAuthorizationKey;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.model.OwnerAuthorizationReadResult;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.spi.OwnerAuthorizationProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 固定内部 SERVICE 主动拉取身份源 owner 授权日志的 worker。
 */
@Slf4j
@SimpleAkskServerComponent
@RequiredArgsConstructor
public class AkskOwnerAuthorizationSynchronizationService {

    private final SimpleAkskServerProperties properties;
    private final OwnerAuthorizationProvider provider;
    private final AkskOwnerAuthorizationProjectionService projectionService;
    private final AkskClientOwnerBindingRepository bindingRepository;

    /**
     * 领取 DB 租约后拉取一页；身份源不可用时不续 synchronization lease。
     */
    @Scheduled(fixedDelayString = "${io.github.surezzzzzz.sdk.auth.aksk.server.owner-authorization.pull-interval-millis:1000}")
    public void synchronize() {
        if (!Boolean.TRUE.equals(properties.getOwnerAuthorization().getEnabled())) {
            return;
        }
        String workerLease = projectionService.claimWorkerLease();
        if (workerLease == null) {
            return;
        }
        try {
            Long cursor = projectionService.currentCursor();
            OwnerAuthorizationChangePage result = provider.pullChanges(cursor,
                    properties.getOwnerAuthorization().getPullPageSize());
            if (!result.isAvailable()) {
                return;
            }
            if (result.isResyncRequired()) {
                projectionService.invalidateSynchronizationLease();
                if (resyncAllBindings()) {
                    projectionService.completeResync(result.getHighWatermark());
                }
                return;
            }
            for (OwnerAuthorizationChange change : result.getChanges()) {
                if (!projectionService.apply(change)) {
                    projectionService.invalidateSynchronizationLease();
                    return;
                }
            }
            projectionService.renewSynchronizationLease();
        } finally {
            projectionService.releaseWorkerLease(workerLease);
        }
    }

    private boolean resyncAllBindings() {
        for (AkskClientOwnerBindingEntity binding : bindingRepository.findAll()) {
            if (binding.getAuthorizationMode() != AkskOwnerAuthorizationMode.OWNER_INHERITED
                    || binding.getOwnerState() == null || binding.getOwnerState().intValue() != 1) {
                continue;
            }
            OwnerAuthorizationReadResult result = provider.resolve(new OwnerAuthorizationKey(
                    binding.getOwnerSourceId(), binding.getOwnerSubjectId()), binding.getTargetApplicationId());
            if (!result.isActive()) {
                log.warn("身份源授权全量修复未收敛，保持 inherited 租约失效：clientId={}", binding.getClientId());
                return false;
            }
            projectionService.bootstrap(binding.getOwnerSourceId(), binding.getOwnerSubjectId(),
                    binding.getTargetApplicationId(), result);
        }
        return true;
    }
}
