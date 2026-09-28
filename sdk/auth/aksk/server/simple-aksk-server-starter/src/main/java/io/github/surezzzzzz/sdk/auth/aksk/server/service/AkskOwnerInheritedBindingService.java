package io.github.surezzzzzz.sdk.auth.aksk.server.service;

import io.github.surezzzzzz.sdk.auth.aksk.server.annotation.SimpleAkskServerComponent;
import io.github.surezzzzzz.sdk.auth.aksk.server.constant.SimpleAkskServerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ClientInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.entity.AkskClientLifecycleCommandEntity;
import io.github.surezzzzzz.sdk.auth.aksk.server.entity.AkskClientOwnerBindingEntity;
import io.github.surezzzzzz.sdk.auth.aksk.server.entity.AkskOwnerAuthorizationMode;
import io.github.surezzzzzz.sdk.auth.aksk.server.event.AkskClientLifecycleEvent;
import io.github.surezzzzzz.sdk.auth.aksk.server.event.AkskClientLifecycleEventType;
import io.github.surezzzzzz.sdk.auth.aksk.server.exception.SimpleAkskServerException;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.AkskClientLifecycleCommandRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.AkskClientOwnerBindingRepository;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.model.OwnerAuthorizationCandidate;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.model.OwnerAuthorizationKey;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.model.OwnerAuthorizationReadResult;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.spi.OwnerAuthorizationProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * OWNER_INHERITED AKU 的创建与候选校验服务。
 */
@SimpleAkskServerComponent
@RequiredArgsConstructor
public class AkskOwnerInheritedBindingService {

    private static final String CREATE_COMMAND = "CREATE";
    /**
     * 绑定来源：浏览器自助创建。
     */
    private static final String BINDING_ORIGIN_SELF_SERVICE = "SELF_SERVICE";

    private final OwnerAuthorizationProvider ownerAuthorizationProvider;
    private final ClientManagementService clientManagementService;
    private final AkskClientOwnerBindingRepository bindingRepository;
    private final AkskClientLifecycleCommandRepository commandRepository;
    private final AkskOwnerAuthorizationProjectionService projectionService;
    private final ApplicationEventPublisher eventPublisher;

    @PersistenceContext
    private EntityManager entityManager;

    public List<OwnerAuthorizationCandidate> listCandidates(AkskSelfServicePrincipal owner) {
        return ownerAuthorizationProvider.listCandidates(new OwnerAuthorizationKey(
                owner.getOwnerSourceId(), owner.getOwnerSubjectId()));
    }

    /**
     * 创建用户本人的 inherited AKU。目标应用必须在同一次服务端候选读取中出现，
     * 因此不会信任浏览器提交的 applicationId。
     */
    @Transactional
    public AkskSelfServiceLifecycleResult create(AkskSelfServicePrincipal owner, Long targetApplicationId,
                                                 String clientName, String idempotencyKey) {
        if (owner == null || targetApplicationId == null || targetApplicationId.longValue() <= 0L
                || !StringUtils.hasText(idempotencyKey)) {
            return AkskSelfServiceLifecycleResult.state(AkskSelfServiceLifecycleResult.State.NOT_FOUND);
        }
        String fingerprint = fingerprint(targetApplicationId, clientName);
        Instant boundAt = Instant.now();
        int reserved = reserve(UUID.randomUUID().toString(), owner, idempotencyKey, fingerprint, boundAt);
        if (reserved == 0) {
            return replay(owner, idempotencyKey, fingerprint);
        }
        boolean permitted = false;
        OwnerAuthorizationKey ownerKey = new OwnerAuthorizationKey(owner.getOwnerSourceId(), owner.getOwnerSubjectId());
        for (OwnerAuthorizationCandidate candidate : ownerAuthorizationProvider.listCandidates(ownerKey)) {
            if (targetApplicationId.equals(candidate.getApplicationId())) {
                permitted = true;
                break;
            }
        }
        if (!permitted) {
            return AkskSelfServiceLifecycleResult.state(AkskSelfServiceLifecycleResult.State.NOT_FOUND);
        }
        OwnerAuthorizationReadResult resolved = ownerAuthorizationProvider.resolve(ownerKey, targetApplicationId);
        if (!resolved.isActive()) {
            return AkskSelfServiceLifecycleResult.state(AkskSelfServiceLifecycleResult.State.NOT_FOUND);
        }
        ClientInfoResponse created = clientManagementService.createUserClient(owner.getOwnerSubjectId(),
                resolved.getOwnerUsername(),
                clientName, Collections.singletonList(SimpleAkskServerConstant.OWNER_INHERITED_TECHNICAL_SCOPE));
        AkskClientOwnerBindingEntity binding = new AkskClientOwnerBindingEntity();
        binding.setClientId(created.getClientId());
        binding.setOwnerSourceId(owner.getOwnerSourceId());
        binding.setOwnerSubjectId(owner.getOwnerSubjectId());
        binding.setTargetApplicationId(targetApplicationId);
        binding.setAuthorizationMode(AkskOwnerAuthorizationMode.OWNER_INHERITED);
        binding.setOwnerState(Integer.valueOf(1));
        binding.setLifecycleVersion(1L);
        binding.setBindingOrigin(BINDING_ORIGIN_SELF_SERVICE);
        binding.setBoundAt(boundAt);
        binding.setUpdatedAt(boundAt);
        bindingRepository.saveAndFlush(binding);
        projectionService.bootstrap(binding.getOwnerSourceId(), binding.getOwnerSubjectId(),
                binding.getTargetApplicationId(), resolved);
        AkskClientLifecycleCommandEntity command = commandRepository
                .findByOwnerSourceIdAndOwnerSubjectIdAndCommandTypeAndIdempotencyKey(
                        owner.getOwnerSourceId(), owner.getOwnerSubjectId(), CREATE_COMMAND, idempotencyKey)
                .orElse(null);
        if (command == null) {
            throw new SimpleAkskServerException("AKU自助命令账本读取失败");
        }
        command.setClientId(created.getClientId());
        command.setUpdatedAt(Instant.now());
        commandRepository.save(command);
        created.setLifecycleVersion(binding.getLifecycleVersion());
        eventPublisher.publishEvent(new AkskClientLifecycleEvent(this, AkskClientLifecycleEventType.CREATED,
                binding.getClientId(), binding.getOwnerSourceId(), binding.getOwnerSubjectId(),
                binding.getTargetApplicationId(), binding.getLifecycleVersion()));
        return AkskSelfServiceLifecycleResult.success(created);
    }

    /**
     * 相同幂等键只返回已创建的 client 元数据，绝不再次返回首次 secret。
     */
    private AkskSelfServiceLifecycleResult replay(AkskSelfServicePrincipal owner, String idempotencyKey,
                                                  String fingerprint) {
        AkskClientLifecycleCommandEntity command = commandRepository
                .findByOwnerSourceIdAndOwnerSubjectIdAndCommandTypeAndIdempotencyKey(
                        owner.getOwnerSourceId(), owner.getOwnerSubjectId(), CREATE_COMMAND, idempotencyKey)
                .orElse(null);
        if (command == null || !fingerprint.equals(command.getRequestFingerprint())) {
            return AkskSelfServiceLifecycleResult.state(AkskSelfServiceLifecycleResult.State.CONFLICT);
        }
        if (!StringUtils.hasText(command.getClientId())) {
            return AkskSelfServiceLifecycleResult.state(AkskSelfServiceLifecycleResult.State.CONFLICT);
        }
        AkskClientOwnerBindingEntity binding = bindingRepository.findByClientId(command.getClientId()).orElse(null);
        if (binding == null || !Integer.valueOf(1).equals(binding.getOwnerState())) {
            return AkskSelfServiceLifecycleResult.state(AkskSelfServiceLifecycleResult.State.NOT_FOUND);
        }
        ClientInfoResponse client = clientManagementService.getClientById(command.getClientId());
        client.setClientSecret(null);
        client.setLifecycleVersion(binding.getLifecycleVersion());
        return AkskSelfServiceLifecycleResult.replayed(client);
    }

    private String fingerprint(Long targetApplicationId, String clientName) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest((targetApplicationId + "\n"
                    + clientName.trim()).getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder(64);
            for (byte item : digest) {
                value.append(String.format("%02x", item & 0xff));
            }
            return value.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new SimpleAkskServerException("AKU自助命令摘要初始化失败", exception);
        }
    }

    /**
     * MySQL 的 INSERT IGNORE 由 EntityManager 直接执行，避开旧版 Spring Data JPA 对 native INSERT
     * 推导 count query 的缺陷；唯一键仍是多实例并发下的最终幂等边界。
     */
    private int reserve(String id, AkskSelfServicePrincipal owner, String idempotencyKey,
                        String fingerprint, Instant now) {
        return entityManager.createNativeQuery("INSERT IGNORE INTO aksk_client_lifecycle_command "
                        + "(id, owner_source_id, owner_subject_id, command_type, idempotency_key, "
                        + "request_fingerprint, created_at, updated_at) VALUES "
                        + "(:id, :ownerSourceId, :ownerSubjectId, :commandType, :idempotencyKey, "
                        + ":requestFingerprint, :createdAt, :updatedAt)")
                .setParameter("id", id)
                .setParameter("ownerSourceId", owner.getOwnerSourceId())
                .setParameter("ownerSubjectId", owner.getOwnerSubjectId())
                .setParameter("commandType", CREATE_COMMAND)
                .setParameter("idempotencyKey", idempotencyKey)
                .setParameter("requestFingerprint", fingerprint)
                .setParameter("createdAt", now)
                .setParameter("updatedAt", now)
                .executeUpdate();
    }
}
