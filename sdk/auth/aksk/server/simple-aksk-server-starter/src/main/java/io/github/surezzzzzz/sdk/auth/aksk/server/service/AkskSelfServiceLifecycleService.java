package io.github.surezzzzzz.sdk.auth.aksk.server.service;

import io.github.surezzzzzz.sdk.auth.aksk.server.annotation.SimpleAkskServerComponent;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ClientInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ResetSecretResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.entity.AkskClientOwnerBindingEntity;
import io.github.surezzzzzz.sdk.auth.aksk.server.entity.AkskOwnerAuthorizationMode;
import io.github.surezzzzzz.sdk.auth.aksk.server.event.AkskClientLifecycleEvent;
import io.github.surezzzzzz.sdk.auth.aksk.server.event.AkskClientLifecycleEventType;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.AkskClientOwnerBindingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * OWNER_INHERITED AKU 的 owner 自助生命周期命令。
 */
@SimpleAkskServerComponent
@RequiredArgsConstructor
public class AkskSelfServiceLifecycleService {

    private final AkskClientOwnerBindingRepository bindingRepository;
    private final ClientManagementService clientManagementService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public AkskSelfServiceLifecycleResult rename(AkskSelfServicePrincipal owner, String clientId,
                                                 Long lifecycleVersion, String clientName) {
        AkskClientOwnerBindingEntity binding = ownedActiveBinding(owner, clientId);
        if (binding == null) {
            return AkskSelfServiceLifecycleResult.state(AkskSelfServiceLifecycleResult.State.NOT_FOUND);
        }
        if (!matches(binding, lifecycleVersion)) {
            return AkskSelfServiceLifecycleResult.state(AkskSelfServiceLifecycleResult.State.PRECONDITION_FAILED);
        }
        clientManagementService.updateClientName(clientId, clientName);
        ClientInfoResponse client = touchAndRead(binding);
        publish(binding, AkskClientLifecycleEventType.RENAMED);
        return AkskSelfServiceLifecycleResult.success(client);
    }

    @Transactional
    public AkskSelfServiceLifecycleResult rotateSecret(AkskSelfServicePrincipal owner, String clientId,
                                                       Long lifecycleVersion) {
        AkskClientOwnerBindingEntity binding = ownedActiveBinding(owner, clientId);
        if (binding == null) {
            return AkskSelfServiceLifecycleResult.state(AkskSelfServiceLifecycleResult.State.NOT_FOUND);
        }
        if (!matches(binding, lifecycleVersion)) {
            return AkskSelfServiceLifecycleResult.state(AkskSelfServiceLifecycleResult.State.PRECONDITION_FAILED);
        }
        // 换密钥必须同时撤销旧 Token，沿用既有 token 生命周期与审计链。
        ResetSecretResponse reset = clientManagementService.resetSecret(clientId, true);
        ClientInfoResponse client = touchAndRead(binding);
        client.setClientSecret(reset.getClientSecret());
        publish(binding, AkskClientLifecycleEventType.SECRET_ROTATED);
        return AkskSelfServiceLifecycleResult.success(client);
    }

    @Transactional
    public AkskSelfServiceLifecycleResult terminate(AkskSelfServicePrincipal owner, String clientId,
                                                    Long lifecycleVersion) {
        AkskClientOwnerBindingEntity binding = ownedActiveBinding(owner, clientId);
        if (binding == null) {
            return AkskSelfServiceLifecycleResult.state(AkskSelfServiceLifecycleResult.State.NOT_FOUND);
        }
        if (!matches(binding, lifecycleVersion)) {
            return AkskSelfServiceLifecycleResult.state(AkskSelfServiceLifecycleResult.State.PRECONDITION_FAILED);
        }
        // 先写 tombstone 并触发乐观锁，再复用删除路径撤销所有旧 Token、驱逐 client 缓存并删除 client。
        binding.setOwnerState(Integer.valueOf(0));
        binding.setUpdatedAt(Instant.now());
        bindingRepository.saveAndFlush(binding);
        clientManagementService.deleteClient(clientId);
        publish(binding, AkskClientLifecycleEventType.TERMINATED);
        return AkskSelfServiceLifecycleResult.success(null);
    }

    private AkskClientOwnerBindingEntity ownedActiveBinding(AkskSelfServicePrincipal owner, String clientId) {
        if (owner == null || clientId == null) {
            return null;
        }
        AkskClientOwnerBindingEntity binding = bindingRepository.findByClientId(clientId).orElse(null);
        if (binding == null || !AkskOwnerAuthorizationMode.OWNER_INHERITED.equals(binding.getAuthorizationMode())
                || !Integer.valueOf(1).equals(binding.getOwnerState())
                || !owner.getOwnerSourceId().equals(binding.getOwnerSourceId())
                || !owner.getOwnerSubjectId().equals(binding.getOwnerSubjectId())) {
            return null;
        }
        return binding;
    }

    private boolean matches(AkskClientOwnerBindingEntity binding, Long lifecycleVersion) {
        return lifecycleVersion != null && lifecycleVersion.equals(binding.getLifecycleVersion());
    }

    private ClientInfoResponse touchAndRead(AkskClientOwnerBindingEntity binding) {
        binding.setUpdatedAt(Instant.now());
        AkskClientOwnerBindingEntity updated = bindingRepository.saveAndFlush(binding);
        ClientInfoResponse client = clientManagementService.getClientById(updated.getClientId());
        client.setClientSecret(null);
        client.setLifecycleVersion(updated.getLifecycleVersion());
        return client;
    }

    private void publish(AkskClientOwnerBindingEntity binding, AkskClientLifecycleEventType eventType) {
        eventPublisher.publishEvent(new AkskClientLifecycleEvent(this, eventType, binding.getClientId(),
                binding.getOwnerSourceId(), binding.getOwnerSubjectId(), binding.getTargetApplicationId(),
                binding.getLifecycleVersion()));
    }
}
