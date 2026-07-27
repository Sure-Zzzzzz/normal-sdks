package io.github.surezzzzzz.sdk.kms.server.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsAlgorithm;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsKeyPurpose;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsKeyState;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsOperation;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsPersistenceException;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsValidationException;
import io.github.surezzzzzz.sdk.kms.core.model.KmsKey;
import io.github.surezzzzzz.sdk.kms.core.model.KmsKeyPolicy;
import io.github.surezzzzzz.sdk.kms.core.model.KmsPublicKey;
import io.github.surezzzzzz.sdk.kms.core.service.KeyManagementService;
import io.github.surezzzzzz.sdk.kms.core.service.KeyPolicyManagementService;
import io.github.surezzzzzz.sdk.kms.core.service.PublicKeyService;
import io.github.surezzzzzz.sdk.kms.server.configuration.SmartKmsServerProperties;
import io.github.surezzzzzz.sdk.kms.server.constant.SmartKmsServerConstant;
import io.github.surezzzzzz.sdk.kms.server.repository.KmsKeyMetadata;
import io.github.surezzzzzz.sdk.kms.server.repository.KmsKeyPage;
import io.github.surezzzzzz.sdk.kms.server.repository.KmsKeyQueryRepository;
import io.github.surezzzzzz.sdk.kms.server.service.*;
import io.github.surezzzzzz.sdk.kms.server.support.KmsHttpJson;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * KMS 逻辑密钥及其从属资源 REST 控制器。
 *
 * @author surezzzzzz
 */
@RestController
@RequestMapping(SmartKmsServerConstant.API_BASE_PATH + "/keys")
public class KmsKeyController extends KmsHttpControllerSupport {

    private final KeyManagementService keyManagementService;
    private final KmsKeyQueryRepository keyQueryRepository;
    private final KmsManagementReadAuthorizer managementReadAuthorizer;
    private final KeyPolicyManagementService keyPolicyManagementService;
    private final PublicKeyService publicKeyService;
    private final KmsManagementIdempotencyService idempotencyService;

    /**
     * 创建逻辑密钥 REST 控制器。
     */
    public KmsKeyController(KmsPrincipalResolver principalResolver, SmartKmsServerProperties properties,
                            KeyManagementService keyManagementService,
                            KmsKeyQueryRepository keyQueryRepository,
                            KmsManagementReadAuthorizer managementReadAuthorizer,
                            KeyPolicyManagementService keyPolicyManagementService,
                            PublicKeyService publicKeyService,
                            KmsManagementIdempotencyService idempotencyService) {
        super(principalResolver, properties);
        this.keyManagementService = keyManagementService;
        this.keyQueryRepository = keyQueryRepository;
        this.managementReadAuthorizer = managementReadAuthorizer;
        this.keyPolicyManagementService = keyPolicyManagementService;
        this.publicKeyService = publicKeyService;
        this.idempotencyService = idempotencyService;
    }

    /**
     * 构造可被管理幂等链安全持久化的成功响应。
     */
    private static KmsManagementIdempotencyResult managementResponse(int status, Map<String, Object> response,
                                                                     String resourceRef) {
        return new KmsManagementIdempotencyResult(status, KmsHttpJson.write(response), resourceRef, null, false);
    }

    /**
     * 校验列表筛选使用的是公开稳定枚举编码。
     */
    private static void validateFilterCodes(String purpose, String algorithm, String state) {
        if ((purpose != null && KmsKeyPurpose.fromCode(purpose) == null)
                || (algorithm != null && KmsAlgorithm.fromCode(algorithm) == null)
                || (state != null && KmsKeyState.fromCode(state) == null)) {
            throw new KmsValidationException();
        }
    }

    private static KmsKeyPurpose keyPurpose(String value) {
        KmsKeyPurpose purpose = KmsKeyPurpose.fromCode(value);
        if (purpose == null) {
            throw new KmsValidationException();
        }
        return purpose;
    }

    private static KmsAlgorithm algorithm(String value) {
        KmsAlgorithm algorithm = KmsAlgorithm.fromCode(value);
        if (algorithm == null) {
            throw new KmsValidationException();
        }
        return algorithm;
    }

    private static KmsKeyState keyState(String value) {
        KmsKeyState state = KmsKeyState.fromCode(value);
        if (state == null) {
            throw new KmsValidationException();
        }
        return state;
    }

    private static KmsOperation operation(String value) {
        KmsOperation operation = KmsOperation.fromCode(value);
        if (operation == null) {
            throw new KmsValidationException();
        }
        return operation;
    }

    /**
     * 创建逻辑密钥资源。
     */
    @PostMapping(consumes = JSON, produces = JSON_UTF8)
    public ResponseEntity<String> create(@RequestBody String body,
                                         @RequestHeader("Idempotency-Key") String idempotencyKey,
                                         HttpServletRequest request) {
        ObjectNode input = object(body, "keyAlias", "purpose", "algorithm");
        KmsRequestContext context = context(request);
        KmsKey key = KmsKey.builder().tenantId(context.getPrincipal().getTenantId())
                .keyRef("pending").keyAlias(text(input, "keyAlias", true))
                .purpose(keyPurpose(text(input, "purpose", true))).algorithm(algorithm(text(input, "algorithm", true)))
                .state(KmsKeyState.ACTIVE).activeVersion(1).rowVersion(0L).build();
        String endpoint = "POST:" + SmartKmsServerConstant.API_BASE_PATH + "/keys";
        KmsManagementIdempotencyResult result = idempotencyService.execute(context.getPrincipal(), endpoint,
                idempotencyKey, context.getRequestId(), canonicalRequest(endpoint, input), () -> {
                    KmsKey created = keyManagementService.create(context.getPrincipal(), key, idempotencyKey,
                            context.getRequestId());
                    KmsKeyMetadata metadata = keyQueryRepository.findMetadata(context.getPrincipal().getTenantId(),
                            created.getKeyRef()).orElseThrow(KmsPersistenceException::new);
                    String location = SmartKmsServerConstant.API_BASE_PATH + "/keys/" + created.getKeyRef();
                    return new KmsManagementIdempotencyResult(201, KmsHttpJson.write(key(metadata)),
                            created.getKeyRef(), location, false);
                });
        return idempotent(result);
    }

    /**
     * 查询当前 tenant 的单个逻辑密钥资源。
     */
    @GetMapping(value = "/{keyRef}", produces = JSON_UTF8)
    public ResponseEntity<String> get(@PathVariable String keyRef, HttpServletRequest request) {
        KmsRequestContext context = context(request);
        keyManagementService.find(context.getPrincipal(), keyRef, context.getRequestId());
        KmsKeyMetadata metadata = keyQueryRepository.findMetadata(context.getPrincipal().getTenantId(), keyRef)
                .orElseThrow(KmsPersistenceException::new);
        return json(200, key(metadata));
    }

    /**
     * 查询当前 tenant 的逻辑密钥集合。
     */
    @GetMapping(produces = JSON_UTF8)
    public ResponseEntity<String> list(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(required = false) Integer size,
                                       @RequestParam(required = false) String alias,
                                       @RequestParam(required = false) String purpose,
                                       @RequestParam(required = false) String algorithm,
                                       @RequestParam(required = false) String state,
                                       HttpServletRequest request) {
        KmsRequestContext context = context(request);
        int defaultSize = pageDefaultSize();
        int maxSize = pageMaxSize(defaultSize);
        int resolvedSize = size == null ? defaultSize : size.intValue();
        if (page < 1 || resolvedSize < 1 || resolvedSize > maxSize) {
            throw new KmsValidationException();
        }
        managementReadAuthorizer.authorize(context.getPrincipal(), context.getRequestId());
        validateFilterCodes(purpose, algorithm, state);
        long requestedOffset = ((long) page - 1L) * (long) resolvedSize;
        KmsKeyPage keys = keyQueryRepository.findPage(context.getPrincipal().getTenantId(), alias, purpose, algorithm,
                state, requestedOffset, resolvedSize);
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (KmsKeyMetadata key : keys.getItems()) {
            items.add(key(key));
        }
        Map<String, Object> response = map();
        response.put("items", items);
        response.put("page", page);
        response.put("size", resolvedSize);
        response.put("total", keys.getTotal());
        return json(200, response);
    }

    /**
     * 修改逻辑密钥状态。
     */
    @PatchMapping(value = "/{keyRef}/state", consumes = JSON, produces = JSON_UTF8)
    public ResponseEntity<String> changeState(@PathVariable String keyRef, @RequestBody String body,
                                              @RequestHeader("Idempotency-Key") String idempotencyKey,
                                              HttpServletRequest request) {
        ObjectNode input = object(body, "state", "expectedRowVersion");
        KmsRequestContext context = context(request);
        String endpoint = "PATCH:" + SmartKmsServerConstant.API_BASE_PATH + "/keys/" + keyRef + "/state";
        KmsManagementIdempotencyResult result = idempotencyService.execute(context.getPrincipal(), endpoint,
                idempotencyKey, context.getRequestId(), canonicalRequest(endpoint, input), () -> {
                    KmsKey key = keyManagementService.changeState(context.getPrincipal(), keyRef,
                            keyState(text(input, "state", true)),
                            longValue(input, "expectedRowVersion", true).longValue(), idempotencyKey,
                            context.getRequestId());
                    return managementResponse(200, key(context, key), key.getKeyRef());
                });
        return idempotent(result);
    }

    /**
     * 创建下一个活动密钥版本。
     */
    @PostMapping(value = "/{keyRef}/versions", consumes = JSON, produces = JSON_UTF8)
    public ResponseEntity<String> rotate(@PathVariable String keyRef, @RequestBody String body,
                                         @RequestHeader("Idempotency-Key") String idempotencyKey,
                                         HttpServletRequest request) {
        ObjectNode input = object(body, "expectedRowVersion");
        KmsRequestContext context = context(request);
        String endpoint = "POST:" + SmartKmsServerConstant.API_BASE_PATH + "/keys/" + keyRef + "/versions";
        KmsManagementIdempotencyResult result = idempotencyService.execute(context.getPrincipal(), endpoint,
                idempotencyKey, context.getRequestId(), canonicalRequest(endpoint, input), () -> {
                    KmsKey key = keyManagementService.rotate(context.getPrincipal(), keyRef,
                            longValue(input, "expectedRowVersion", true).longValue(), idempotencyKey,
                            context.getRequestId());
                    return managementResponse(200, key(context, key), key.getKeyRef());
                });
        return idempotent(result);
    }

    /**
     * 安排整个逻辑密钥销毁。
     */
    @PutMapping(value = "/{keyRef}/destruction", consumes = JSON, produces = JSON_UTF8)
    public ResponseEntity<String> scheduleDestruction(@PathVariable String keyRef, @RequestBody String body,
                                                      @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                      HttpServletRequest request) {
        ObjectNode input = object(body, "destroyAfter", "expectedRowVersion");
        KmsRequestContext context = context(request);
        Instant destroyAfter = instant(input, "destroyAfter", true);
        String endpoint = "PUT:" + SmartKmsServerConstant.API_BASE_PATH + "/keys/" + keyRef + "/destruction";
        KmsManagementIdempotencyResult result = idempotencyService.execute(context.getPrincipal(), endpoint,
                idempotencyKey, context.getRequestId(), canonicalRequest(endpoint, input), () -> {
                    KmsKey key = keyManagementService.scheduleDestruction(context.getPrincipal(), keyRef, destroyAfter,
                            longValue(input, "expectedRowVersion", true).longValue(), idempotencyKey,
                            context.getRequestId());
                    return managementResponse(200, key(context, key), key.getKeyRef());
                });
        return idempotent(result);
    }

    /**
     * 取消未被领取的逻辑密钥销毁任务。
     */
    @DeleteMapping(value = "/{keyRef}/destruction", consumes = JSON)
    public ResponseEntity<String> cancelDestruction(@PathVariable String keyRef, @RequestBody String body,
                                                    @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                    HttpServletRequest request) {
        ObjectNode input = object(body, "expectedRowVersion");
        KmsRequestContext context = context(request);
        String endpoint = "DELETE:" + SmartKmsServerConstant.API_BASE_PATH + "/keys/" + keyRef + "/destruction";
        KmsManagementIdempotencyResult result = idempotencyService.execute(context.getPrincipal(), endpoint,
                idempotencyKey, context.getRequestId(), canonicalRequest(endpoint, input), () -> {
                    keyManagementService.cancelDestruction(context.getPrincipal(), keyRef,
                            longValue(input, "expectedRowVersion", true).longValue(), idempotencyKey,
                            context.getRequestId());
                    return new KmsManagementIdempotencyResult(204, null, keyRef, null, false);
                });
        return idempotent(result);
    }

    /**
     * 查询可分发的单个 ES256 公钥资源。
     */
    @GetMapping(value = "/{keyRef}/public-key", produces = JSON_UTF8)
    public ResponseEntity<String> publicKey(@PathVariable String keyRef,
                                            @RequestParam(required = false) Integer version,
                                            HttpServletRequest request) {
        KmsRequestContext context = context(request);
        return jsonWithHeader(200, publicKey(publicKeyService.read(context.getPrincipal(), keyRef, version,
                context.getRequestId())), HttpHeaders.CACHE_CONTROL, "no-store");
    }

    /**
     * 查询当前逻辑密钥的全部可分发 ES256 公钥资源。
     */
    @GetMapping(value = "/{keyRef}/public-keys", produces = JSON_UTF8)
    public ResponseEntity<String> publicKeys(@PathVariable String keyRef, HttpServletRequest request) {
        KmsRequestContext context = context(request);
        List<Map<String, Object>> keys = new ArrayList<Map<String, Object>>();
        for (KmsPublicKey publicKey : publicKeyService.list(context.getPrincipal(), keyRef, context.getRequestId())) {
            keys.add(publicKey(publicKey));
        }
        return jsonArrayWithHeader(200, keys, HttpHeaders.CACHE_CONTROL, "no-store");
    }

    /**
     * 创建精确 allow-only 策略资源。
     */
    @PostMapping(value = "/{keyRef}/policies", consumes = JSON, produces = JSON_UTF8)
    public ResponseEntity<String> createPolicy(@PathVariable String keyRef, @RequestBody String body,
                                               @RequestHeader("Idempotency-Key") String idempotencyKey,
                                               HttpServletRequest request) {
        ObjectNode input = object(body, "principalId", "keyVersion", "operation", "expiresAt");
        KmsRequestContext context = context(request);
        KmsKeyPolicy policy = KmsKeyPolicy.builder().policyId("pending").tenantId(context.getPrincipal().getTenantId())
                .keyRef(keyRef).principalId(text(input, "principalId", true)).keyVersion(integer(input, "keyVersion", false))
                .operation(operation(text(input, "operation", true))).expiresAt(instant(input, "expiresAt", false))
                .rowVersion(0L).build();
        String endpoint = "POST:" + SmartKmsServerConstant.API_BASE_PATH + "/keys/" + keyRef + "/policies";
        KmsManagementIdempotencyResult result = idempotencyService.execute(context.getPrincipal(), endpoint,
                idempotencyKey, context.getRequestId(), canonicalRequest(endpoint, input), () -> {
                    KmsKeyPolicy created = keyPolicyManagementService.create(context.getPrincipal(), policy,
                            idempotencyKey, context.getRequestId());
                    String location = SmartKmsServerConstant.API_BASE_PATH + "/keys/" + keyRef + "/policies/"
                            + created.getPolicyId();
                    return new KmsManagementIdempotencyResult(201, KmsHttpJson.write(policy(created)),
                            keyRef + "/" + created.getPolicyId(), location, false);
                });
        return idempotent(result);
    }

    /**
     * 查询当前 tenant 的逻辑密钥策略资源。
     */
    @GetMapping(value = "/{keyRef}/policies", produces = JSON_UTF8)
    public ResponseEntity<String> policies(@PathVariable String keyRef, HttpServletRequest request) {
        KmsRequestContext context = context(request);
        List<Map<String, Object>> policies = new ArrayList<Map<String, Object>>();
        for (KmsKeyPolicy policy : keyPolicyManagementService.list(context.getPrincipal(), keyRef,
                context.getRequestId())) {
            policies.add(policy(policy));
        }
        Map<String, Object> response = map();
        response.put("items", policies);
        return json(200, response);
    }

    /**
     * 撤销精确策略资源。
     */
    @DeleteMapping(value = "/{keyRef}/policies/{policyId}", consumes = JSON)
    public ResponseEntity<String> revokePolicy(@PathVariable String keyRef, @PathVariable String policyId,
                                               @RequestBody String body,
                                               @RequestHeader("Idempotency-Key") String idempotencyKey,
                                               HttpServletRequest request) {
        ObjectNode input = object(body, "expectedRowVersion");
        KmsRequestContext context = context(request);
        String endpoint = "DELETE:" + SmartKmsServerConstant.API_BASE_PATH + "/keys/" + keyRef + "/policies/"
                + policyId;
        KmsManagementIdempotencyResult result = idempotencyService.execute(context.getPrincipal(), endpoint,
                idempotencyKey, context.getRequestId(), canonicalRequest(endpoint, input), () -> {
                    keyPolicyManagementService.revoke(context.getPrincipal(), keyRef, policyId,
                            longValue(input, "expectedRowVersion", true).longValue(), idempotencyKey,
                            context.getRequestId());
                    return new KmsManagementIdempotencyResult(204, null, policyId, null, false);
                });
        return idempotent(result);
    }

    private Map<String, Object> key(KmsRequestContext context, KmsKey source) {
        KmsKeyMetadata metadata = keyQueryRepository.findMetadata(context.getPrincipal().getTenantId(), source.getKeyRef())
                .orElseThrow(KmsPersistenceException::new);
        return key(metadata);
    }

    private Map<String, Object> key(KmsKeyMetadata source) {
        KmsKey key = source.getKey();
        Map<String, Object> response = map();
        response.put("keyRef", key.getKeyRef());
        response.put("keyAlias", key.getKeyAlias());
        response.put("purpose", key.getPurpose().getCode());
        response.put("algorithm", key.getAlgorithm().getCode());
        response.put("state", key.getState().getCode());
        response.put("activeVersion", key.getActiveVersion());
        response.put("rowVersion", key.getRowVersion());
        response.put("createdAt", KmsHttpJson.utcMillis(source.getCreatedAt()));
        response.put("updatedAt", KmsHttpJson.utcMillis(source.getUpdatedAt()));
        return response;
    }

    private Map<String, Object> policy(KmsKeyPolicy source) {
        Map<String, Object> response = map();
        response.put("policyId", source.getPolicyId());
        response.put("keyRef", source.getKeyRef());
        response.put("principalId", source.getPrincipalId());
        response.put("keyVersion", source.getKeyVersion());
        response.put("operation", source.getOperation().getCode());
        response.put("expiresAt", KmsHttpJson.utcMillis(source.getExpiresAt()));
        response.put("rowVersion", source.getRowVersion());
        return response;
    }

    private Map<String, Object> publicKey(KmsPublicKey source) {
        Map<String, Object> response = map();
        response.put("keyRef", source.getKeyRef());
        response.put("version", source.getVersion());
        response.put("algorithm", source.getAlgorithm().getCode());
        response.put("state", source.getState().getCode());
        response.put("publicKey", base64url(source.getPublicMaterial()));
        return response;
    }
}
