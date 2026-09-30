package io.github.surezzzzzz.sdk.auth.aksk.server.controller;

import io.github.surezzzzzz.sdk.auth.aksk.server.annotation.SimpleAkskServerComponent;
import io.github.surezzzzzz.sdk.auth.aksk.server.constant.SimpleAkskServerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.request.CreateMyAkskClientRequest;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.request.UpdateMyAkskClientRequest;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ClientInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.entity.AkskClientOwnerBindingEntity;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.AkskClientOwnerBindingRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.*;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.model.OwnerAuthorizationCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;

/**
 * 当前身份源人员的 AKU 自助入口。
 */
@SimpleAkskServerComponent
@RestController
@RequestMapping("/api/me/aksk-clients")
@RequiredArgsConstructor
public class AkskSelfServiceClientController {

    /**
     * 候选目录降级标记（3.2.1 起仅在降级时出现）：本地投影同步租约已失效时置 true，
     * 提示空候选可能是"读不到"而非"没授权"；不改变 200 + 空数组的既有契约响应形态。
     */
    public static final String HEADER_PROJECTION_DEGRADED = "X-Aksk-Projection-Degraded";

    private final AkskSelfServicePrincipalResolver principalResolver;
    private final AkskOwnerInheritedBindingService bindingService;
    private final AkskClientOwnerBindingRepository bindingRepository;
    private final ClientManagementService clientManagementService;
    private final AkskSelfServiceLifecycleService lifecycleService;
    private final AkskOwnerAuthorizationProjectionService ownerAuthorizationProjectionService;

    @GetMapping("/candidate-applications")
    @RequireApiPermission(SimpleAkskServerConstant.SELF_PERMISSION_CREDENTIAL_READ)
    public ResponseEntity<List<OwnerAuthorizationCandidate>> candidates() {
        AkskSelfServicePrincipal owner = principalResolver.resolve();
        if (owner == null) {
            return ResponseEntity.status(403).build();
        }
        ResponseEntity.BodyBuilder response = ResponseEntity.ok().cacheControl(privateNoStore());
        if (ownerAuthorizationProjectionService.isSynchronizationLeaseExpired()) {
            response = response.header(HEADER_PROJECTION_DEGRADED, "true");
        }
        return response.body(bindingService.listCandidates(owner));
    }

    @GetMapping
    @RequireApiPermission(SimpleAkskServerConstant.SELF_PERMISSION_CREDENTIAL_READ)
    public ResponseEntity<List<ClientInfoResponse>> list() {
        AkskSelfServicePrincipal owner = principalResolver.resolve();
        if (owner == null) {
            return ResponseEntity.status(403).build();
        }
        List<ClientInfoResponse> items = new ArrayList<ClientInfoResponse>();
        for (AkskClientOwnerBindingEntity binding : bindingRepository
                .findByOwnerSourceIdAndOwnerSubjectIdOrderByBoundAtDesc(owner.getOwnerSourceId(), owner.getOwnerSubjectId())) {
            if (!Integer.valueOf(1).equals(binding.getOwnerState())) {
                continue;
            }
            ClientInfoResponse client = clientManagementService.getClientById(binding.getClientId());
            client.setClientSecret(null);
            client.setTargetApplicationId(binding.getTargetApplicationId());
            client.setLifecycleVersion(binding.getLifecycleVersion());
            items.add(client);
        }
        return ResponseEntity.ok().cacheControl(privateNoStore()).body(items);
    }

    @PostMapping
    @RequireApiPermission(SimpleAkskServerConstant.SELF_PERMISSION_CREDENTIAL_CREATE)
    public ResponseEntity<ClientInfoResponse> create(@RequestHeader(value = "Idempotency-Key", required = false)
                                                     String idempotencyKey,
                                                     @Valid @RequestBody CreateMyAkskClientRequest request) {
        AkskSelfServicePrincipal owner = principalResolver.resolve();
        if (owner == null) {
            return ResponseEntity.status(403).build();
        }
        if (!StringUtils.hasText(idempotencyKey) || idempotencyKey.length() > 128) {
            return ResponseEntity.badRequest().build();
        }
        AkskSelfServiceLifecycleResult created = bindingService.create(owner, request.getTargetApplicationId(),
                request.getClientName(), idempotencyKey);
        // 不存在、跨 owner 或刚被身份源收紧的目标都用同一 404，避免枚举应用授权状态。
        return lifecycleResponse(created, false);
    }

    @PatchMapping("/{clientId}")
    @RequireApiPermission(SimpleAkskServerConstant.SELF_PERMISSION_CREDENTIAL_UPDATE)
    public ResponseEntity<ClientInfoResponse> rename(@PathVariable String clientId,
                                                     @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                                     @Valid @RequestBody UpdateMyAkskClientRequest request) {
        AkskSelfServicePrincipal owner = principalResolver.resolve();
        if (owner == null) {
            return ResponseEntity.status(403).build();
        }
        Long lifecycleVersion = lifecycleVersion(ifMatch);
        if (lifecycleVersion == null) {
            return ifMatch == null ? ResponseEntity.status(428).build() : ResponseEntity.badRequest().build();
        }
        return lifecycleResponse(lifecycleService.rename(owner, clientId, lifecycleVersion, request.getClientName()), false);
    }

    @PutMapping("/{clientId}/secret")
    @RequireApiPermission(SimpleAkskServerConstant.SELF_PERMISSION_CREDENTIAL_UPDATE)
    public ResponseEntity<ClientInfoResponse> rotateSecret(@PathVariable String clientId,
                                                           @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        AkskSelfServicePrincipal owner = principalResolver.resolve();
        if (owner == null) {
            return ResponseEntity.status(403).build();
        }
        Long lifecycleVersion = lifecycleVersion(ifMatch);
        if (lifecycleVersion == null) {
            return ifMatch == null ? ResponseEntity.status(428).build() : ResponseEntity.badRequest().build();
        }
        return lifecycleResponse(lifecycleService.rotateSecret(owner, clientId, lifecycleVersion), false);
    }

    @DeleteMapping("/{clientId}")
    @RequireApiPermission(SimpleAkskServerConstant.SELF_PERMISSION_CREDENTIAL_DELETE)
    public ResponseEntity<Void> terminate(@PathVariable String clientId,
                                          @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        AkskSelfServicePrincipal owner = principalResolver.resolve();
        if (owner == null) {
            return ResponseEntity.status(403).build();
        }
        Long lifecycleVersion = lifecycleVersion(ifMatch);
        if (lifecycleVersion == null) {
            return ifMatch == null ? ResponseEntity.status(428).build() : ResponseEntity.badRequest().build();
        }
        AkskSelfServiceLifecycleResult result = lifecycleService.terminate(owner, clientId, lifecycleVersion);
        if (result.getState() == AkskSelfServiceLifecycleResult.State.SUCCESS) {
            return ResponseEntity.noContent().cacheControl(privateNoStore()).build();
        }
        if (result.getState() == AkskSelfServiceLifecycleResult.State.NOT_FOUND) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.status(412).build();
    }

    private CacheControl privateNoStore() {
        return CacheControl.noStore().cachePrivate();
    }

    private Long lifecycleVersion(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.replace("\"", "");
        try {
            long version = Long.parseLong(normalized);
            return version > 0L ? Long.valueOf(version) : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private ResponseEntity<ClientInfoResponse> lifecycleResponse(AkskSelfServiceLifecycleResult result,
                                                                 boolean secretMayBePresent) {
        if (result.getState() == AkskSelfServiceLifecycleResult.State.SUCCESS
                || result.getState() == AkskSelfServiceLifecycleResult.State.REPLAYED) {
            ClientInfoResponse client = result.getClient();
            if (client != null) {
                AkskClientOwnerBindingEntity binding = bindingRepository.findByClientId(client.getClientId()).orElse(null);
                if (binding != null) {
                    client.setTargetApplicationId(binding.getTargetApplicationId());
                }
            }
            if (!secretMayBePresent && result.getState() == AkskSelfServiceLifecycleResult.State.REPLAYED) {
                client.setClientSecret(null);
            }
            return ResponseEntity.ok().cacheControl(privateNoStore()).body(client);
        }
        if (result.getState() == AkskSelfServiceLifecycleResult.State.NOT_FOUND) {
            return ResponseEntity.notFound().build();
        }
        if (result.getState() == AkskSelfServiceLifecycleResult.State.CONFLICT) {
            return ResponseEntity.status(409).build();
        }
        return ResponseEntity.status(412).build();
    }
}
