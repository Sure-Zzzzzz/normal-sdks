package io.github.surezzzzzz.sdk.kms.server.controller;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsKeyState;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsPersistenceException;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsValidationException;
import io.github.surezzzzzz.sdk.kms.core.service.KeyManagementService;
import io.github.surezzzzzz.sdk.kms.core.support.KmsValidationHelper;
import io.github.surezzzzzz.sdk.kms.server.configuration.SmartKmsServerProperties;
import io.github.surezzzzzz.sdk.kms.server.constant.SmartKmsServerConstant;
import io.github.surezzzzzz.sdk.kms.server.repository.KmsKeyMetadata;
import io.github.surezzzzzz.sdk.kms.server.repository.KmsKeyQueryRepository;
import io.github.surezzzzzz.sdk.kms.server.service.KmsManagementIdempotencyResult;
import io.github.surezzzzzz.sdk.kms.server.service.KmsManagementIdempotencyService;
import io.github.surezzzzzz.sdk.kms.server.service.KmsPrincipalResolver;
import io.github.surezzzzzz.sdk.kms.server.service.KmsRequestContext;
import io.github.surezzzzzz.sdk.kms.server.support.KmsHttpJson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;

/**
 * 固定认证主体本人归属的生命周期写接口；治理 DATA 入口保留在旧控制器。
 *
 * @author surezzzzzz
 */
@Slf4j
@RestController
@RequestMapping(SmartKmsServerConstant.API_BASE_PATH + "/me/keys")
public class KmsMyKeyManagementController extends KmsHttpControllerSupport {

    private final KeyManagementService keyManagementService;
    private final KmsKeyQueryRepository keyQueryRepository;
    private final KmsManagementIdempotencyService idempotencyService;

    /**
     * 创建本人生命周期控制器。
     *
     * @param principalResolver    认证主体解析器
     * @param properties           模块配置
     * @param keyManagementService 生命周期领域服务
     * @param keyQueryRepository   安全元数据查询
     * @param idempotencyService   事务内幂等执行器
     */
    public KmsMyKeyManagementController(KmsPrincipalResolver principalResolver, SmartKmsServerProperties properties,
                                        KeyManagementService keyManagementService, KmsKeyQueryRepository keyQueryRepository,
                                        KmsManagementIdempotencyService idempotencyService) {
        super(principalResolver, properties);
        this.keyManagementService = keyManagementService;
        this.keyQueryRepository = keyQueryRepository;
        this.idempotencyService = idempotencyService;
    }

    /**
     * 修改本人密钥启停状态。
     */
    @PatchMapping(value = "/{keyRef}/state", consumes = JSON, produces = JSON_UTF8)
    @RequireApiPermission(SmartKmsServerConstant.API_PERMISSION_KEY_MANAGE)
    public ResponseEntity<KmsMyKeyResponse> changeState(@PathVariable String keyRef,
                                                        @RequestBody KmsMyKeyStateRequest body, @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                        HttpServletRequest request) {
        KmsRequestContext context = requireApiPermission(context(request), SmartKmsServerConstant.API_PERMISSION_KEY_MANAGE);
        KmsKeyState state = KmsKeyState.fromCode(body.getState());
        if (state != KmsKeyState.ACTIVE && state != KmsKeyState.DISABLED) {
            throw new KmsValidationException();
        }
        String endpoint = "PATCH:" + SmartKmsServerConstant.API_BASE_PATH + "/me/keys/" + keyRef + "/state";
        return response(execute(context, endpoint, keyRef, body, body.getExpectedRowVersion(), idempotencyKey, () -> {
            keyManagementService.changeState(context.getPrincipal(), keyRef, state, body.getExpectedRowVersion(),
                    idempotencyKey, context.getRequestId());
            return metadataResponse(context, keyRef);
        }));
    }

    /**
     * 轮换本人活动密钥。
     */
    @PostMapping(value = "/{keyRef}/versions", consumes = JSON, produces = JSON_UTF8)
    @RequireApiPermission(SmartKmsServerConstant.API_PERMISSION_KEY_MANAGE)
    public ResponseEntity<KmsMyKeyResponse> rotate(@PathVariable String keyRef,
                                                   @RequestBody KmsMyKeyVersionRequest body, @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                   HttpServletRequest request) {
        KmsRequestContext context = requireApiPermission(context(request), SmartKmsServerConstant.API_PERMISSION_KEY_MANAGE);
        String endpoint = "POST:" + SmartKmsServerConstant.API_BASE_PATH + "/me/keys/" + keyRef + "/versions";
        return response(execute(context, endpoint, keyRef, body, body.getExpectedRowVersion(), idempotencyKey, () -> {
            keyManagementService.rotate(context.getPrincipal(), keyRef, body.getExpectedRowVersion(), idempotencyKey,
                    context.getRequestId());
            return metadataResponse(context, keyRef);
        }));
    }

    /**
     * 在本人政策允许的窗口安排销毁。
     */
    @PutMapping(value = "/{keyRef}/destruction", consumes = JSON, produces = JSON_UTF8)
    @RequireApiPermission(SmartKmsServerConstant.API_PERMISSION_KEY_DESTROY)
    public ResponseEntity<KmsMyKeyResponse> scheduleDestruction(@PathVariable String keyRef,
                                                                @RequestBody KmsMyKeyDestructionRequest body, @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                                HttpServletRequest request) {
        KmsRequestContext context = requireApiPermission(context(request), SmartKmsServerConstant.API_PERMISSION_KEY_DESTROY);
        String endpoint = "PUT:" + SmartKmsServerConstant.API_BASE_PATH + "/me/keys/" + keyRef + "/destruction";
        return response(execute(context, endpoint, keyRef, body, body.getExpectedRowVersion(), idempotencyKey, () -> {
            keyManagementService.scheduleDestruction(context.getPrincipal(), keyRef, Instant.parse(body.getDueAt()),
                    body.getExpectedRowVersion(), idempotencyKey, context.getRequestId());
            return metadataResponse(context, keyRef);
        }));
    }

    /**
     * 取消后台从未领取的本人销毁任务。
     */
    @DeleteMapping(value = "/{keyRef}/destruction", consumes = JSON)
    @RequireApiPermission(SmartKmsServerConstant.API_PERMISSION_KEY_DESTROY)
    public ResponseEntity<Void> cancelDestruction(@PathVariable String keyRef,
                                                  @RequestBody KmsMyKeyVersionRequest body, @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                  HttpServletRequest request) {
        KmsRequestContext context = requireApiPermission(context(request), SmartKmsServerConstant.API_PERMISSION_KEY_DESTROY);
        String endpoint = "DELETE:" + SmartKmsServerConstant.API_BASE_PATH + "/me/keys/" + keyRef + "/destruction";
        KmsManagementIdempotencyResult result = execute(context, endpoint, keyRef, body, body.getExpectedRowVersion(),
                idempotencyKey, () -> {
                    keyManagementService.cancelDestruction(context.getPrincipal(), keyRef, body.getExpectedRowVersion(),
                            idempotencyKey, context.getRequestId());
                    return new KmsManagementIdempotencyResult(204, null, keyRef, null, false);
                });
        return ResponseEntity.status(result.getStatus()).build();
    }

    private KmsManagementIdempotencyResult metadataResponse(KmsRequestContext context, String keyRef) {
        KmsKeyMetadata metadata = keyQueryRepository.findMetadata(context.getPrincipal().getOwnerPrincipalId(), keyRef)
                .orElseThrow(KmsPersistenceException::new);
        return new KmsManagementIdempotencyResult(200, KmsHttpJson.write(KmsMyKeyResponse.fromMetadata(metadata)),
                keyRef, null, false);
    }

    private ResponseEntity<KmsMyKeyResponse> response(KmsManagementIdempotencyResult result) {
        return ResponseEntity.status(result.getStatus()).contentType(MediaType.parseMediaType(JSON_UTF8))
                .body(KmsMyKeyResponse.fromSnapshot(result.getResponseBody()));
    }

    private KmsManagementIdempotencyResult execute(KmsRequestContext context, String endpoint, String keyRef,
                                                   Object command, long expectedRowVersion, String idempotencyKey,
                                                   KmsManagementIdempotencyService.KmsManagementWriteAction action) {
        KmsValidationHelper.requireKeyRef(keyRef);
        if (expectedRowVersion < 0) {
            throw new KmsValidationException();
        }
        String canonical = canonicalRequest(endpoint, KmsHttpJson.parseSnapshotObject(KmsHttpJson.write(command)));
        log.debug("KMS 本人写入: requestId={}, actor={}, owner={}, keyRef={}, endpoint={}, expectedRowVersion={}",
                context.getRequestId(), context.getPrincipal().getPrincipalId(), context.getPrincipal().getOwnerPrincipalId(),
                keyRef, endpoint, expectedRowVersion);
        try {
            KmsManagementIdempotencyResult result = idempotencyService.execute(context.getPrincipal(), endpoint,
                    idempotencyKey, context.getRequestId(), canonical, action);
            log.debug("KMS 本人写入完成: requestId={}, keyRef={}, status={}, replayed={}",
                    context.getRequestId(), keyRef, result.getStatus(), result.isReplayed());
            return result;
        } catch (RuntimeException exception) {
            log.debug("KMS 本人写入拒绝或失败: requestId={}, keyRef={}, category={}",
                    context.getRequestId(), keyRef, exception.getClass().getSimpleName());
            throw exception;
        }
    }
}
