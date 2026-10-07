package io.github.surezzzzzz.sdk.kms.server.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.annotation.CurrentDataAccessPlan;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsAlgorithm;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsKeyPurpose;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsKeyState;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsOperation;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsNotFoundException;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsPersistenceException;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsValidationException;
import io.github.surezzzzzz.sdk.kms.core.model.KmsKey;
import io.github.surezzzzzz.sdk.kms.core.model.KmsKeyPolicy;
import io.github.surezzzzzz.sdk.kms.core.model.KmsPrincipal;
import io.github.surezzzzzz.sdk.kms.core.model.KmsPublicKey;
import io.github.surezzzzzz.sdk.kms.core.service.KeyManagementService;
import io.github.surezzzzzz.sdk.kms.core.service.KeyPolicyManagementService;
import io.github.surezzzzzz.sdk.kms.core.service.PublicKeyService;
import io.github.surezzzzzz.sdk.kms.server.configuration.SmartKmsServerProperties;
import io.github.surezzzzzz.sdk.kms.server.constant.SmartKmsServerConstant;
import io.github.surezzzzzz.sdk.kms.server.model.KmsOwnerAccessScope;
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
    private final KeyPolicyManagementService keyPolicyManagementService;
    private final PublicKeyService publicKeyService;
    private final KmsManagementIdempotencyService idempotencyService;
    private final KmsPrincipalDisplayNameResolver displayNameResolver;

    /**
     * 创建逻辑密钥 REST 控制器。
     *
     * @param principalResolver          可信认证主体解析器
     * @param properties                 KMS 配置
     * @param keyManagementService       密钥领域服务
     * @param keyQueryRepository         受 DataPlan 约束的密钥查询仓储
     * @param keyPolicyManagementService 策略领域服务
     * @param publicKeyService           公钥领域服务
     * @param idempotencyService         管理幂等服务
     * @param displayNameResolver        可选的主体显示名解析端口
     */
    public KmsKeyController(KmsPrincipalResolver principalResolver, SmartKmsServerProperties properties,
                            KeyManagementService keyManagementService,
                            KmsKeyQueryRepository keyQueryRepository,
                            KeyPolicyManagementService keyPolicyManagementService,
                            PublicKeyService publicKeyService,
                            KmsManagementIdempotencyService idempotencyService,
                            KmsPrincipalDisplayNameResolver displayNameResolver) {
        super(principalResolver, properties);
        this.keyManagementService = keyManagementService;
        this.keyQueryRepository = keyQueryRepository;
        this.keyPolicyManagementService = keyPolicyManagementService;
        this.publicKeyService = publicKeyService;
        this.idempotencyService = idempotencyService;
        this.displayNameResolver = displayNameResolver;
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
        if (state != KmsKeyState.ACTIVE && state != KmsKeyState.DISABLED) {
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
     * 从已评估 DataPlan 中定位目标密钥，并保留实际操作者身份构造领域调用主体。
     *
     * <p>目标归属只能由受控查询结果派生，不能接收 HTTP 传入的 ownerPrincipalId。这样管理员的
     * ALLOW_ALL 与受限管理员的 IN 约束经过同一条路径生效，审计主体仍保留实际调用者。</p>
     */
    private KmsRequestContext scopedContext(HttpServletRequest request, DataAccessPlan plan, String keyRef) {
        KmsRequestContext requestContext = context(request);
        KmsKeyMetadata metadata = keyQueryRepository.findMetadata(KmsOwnerAccessScope.from(plan), keyRef)
                .orElseThrow(KmsNotFoundException::new);
        KmsPrincipal principal = requestContext.getPrincipal();
        return new KmsRequestContext(new KmsPrincipal(principal.getPrincipalId(),
                metadata.getKey().getOwnerPrincipalId(), principal.getScopes()), requestContext.getRequestId());
    }

    /**
     * 创建逻辑密钥资源；豁免 DataPlan：归属由服务端固定为认证主体本人（不接收 owner 参数，
     * 无跨 owner 面），自建自用不涉及访问他人数据。管理面端点照旧全量 DATA 校验。
     */
    @PostMapping(consumes = JSON, produces = JSON_UTF8)
    @RequireApiPermission(SmartKmsServerConstant.API_PERMISSION_KEY_MANAGE)
    public ResponseEntity<String> create(@RequestBody String body,
                                         @RequestHeader("Idempotency-Key") String idempotencyKey,
                                         HttpServletRequest request) {
        ObjectNode input = object(body, "keyAlias", "purpose", "algorithm");
        KmsRequestContext context = requireApiPermission(context(request), SmartKmsServerConstant.API_PERMISSION_KEY_MANAGE);
        KmsKey key = KmsKey.builder().ownerPrincipalId(context.getPrincipal().getOwnerPrincipalId())
                .keyRef("pending").keyAlias(text(input, "keyAlias", true))
                .purpose(keyPurpose(text(input, "purpose", true))).algorithm(algorithm(text(input, "algorithm", true)))
                .state(KmsKeyState.ACTIVE).activeVersion(1).rowVersion(0L).build();
        String endpoint = "POST:" + SmartKmsServerConstant.API_BASE_PATH + "/keys";
        KmsManagementIdempotencyResult result = idempotencyService.execute(context.getPrincipal(), endpoint,
                idempotencyKey, context.getRequestId(), canonicalRequest(endpoint, input), () -> {
                    KmsKey created = keyManagementService.create(context.getPrincipal(), key, idempotencyKey,
                            context.getRequestId());
                    KmsKeyMetadata metadata = keyQueryRepository.findMetadata(context.getPrincipal().getOwnerPrincipalId(),
                            created.getKeyRef()).orElseThrow(KmsPersistenceException::new);
                    String location = SmartKmsServerConstant.API_BASE_PATH + "/keys/" + created.getKeyRef();
                    return new KmsManagementIdempotencyResult(201, KmsHttpJson.write(key(metadata)),
                            created.getKeyRef(), location, false);
                });
        return idempotent(result);
    }

    /**
     * 查询当前主体归属的单个逻辑密钥资源。
     */
    @GetMapping(value = "/{keyRef}", produces = JSON_UTF8)
    @RequireApiPermission(SmartKmsServerConstant.API_PERMISSION_KEY_READ)
    public ResponseEntity<String> get(@PathVariable String keyRef, HttpServletRequest request) {
        KmsRequestContext context = requireApiPermission(context(request), SmartKmsServerConstant.API_PERMISSION_KEY_READ);
        keyManagementService.find(context.getPrincipal(), keyRef, context.getRequestId());
        KmsKeyMetadata metadata = keyQueryRepository.findMetadata(context.getPrincipal().getOwnerPrincipalId(), keyRef)
                .orElseThrow(KmsPersistenceException::new);
        return json(200, key(metadata));
    }

    /**
     * 查询当前主体归属的逻辑密钥集合。
     */
    @GetMapping(produces = JSON_UTF8)
    @RequireApiPermission(SmartKmsServerConstant.API_PERMISSION_KEY_READ)
    public ResponseEntity<String> list(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(required = false) Integer size,
                                       @RequestParam(required = false) String alias,
                                       @RequestParam(required = false) String purpose,
                                       @RequestParam(required = false) String algorithm,
                                       @RequestParam(required = false) String state,
                                       HttpServletRequest request) {
        KmsRequestContext context = requireApiPermission(context(request), SmartKmsServerConstant.API_PERMISSION_KEY_READ);
        int defaultSize = pageDefaultSize();
        int maxSize = pageMaxSize(defaultSize);
        int resolvedSize = size == null ? defaultSize : size.intValue();
        if (page < 1 || resolvedSize < 1 || resolvedSize > maxSize) {
            throw new KmsValidationException();
        }
        validateFilterCodes(purpose, algorithm, state);
        long requestedOffset = ((long) page - 1L) * (long) resolvedSize;
        KmsKeyPage keys = keyQueryRepository.findPage(context.getPrincipal().getOwnerPrincipalId(), alias, purpose, algorithm,
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
    @RequireApiPermission(SmartKmsServerConstant.API_PERMISSION_KEY_MANAGE)
    @DataPermissionOperation(resource = SmartKmsServerConstant.DATA_RESOURCE_KEY,
            action = SmartKmsServerConstant.DATA_ACTION_KEY_MANAGE)
    public ResponseEntity<String> changeState(@PathVariable String keyRef, @RequestBody String body,
                                              @RequestHeader("Idempotency-Key") String idempotencyKey,
                                              @CurrentDataAccessPlan DataAccessPlan plan,
                                              HttpServletRequest request) {
        ObjectNode input = object(body, "state", "expectedRowVersion");
        KmsRequestContext context = requireApiPermission(scopedContext(request, plan, keyRef), SmartKmsServerConstant.API_PERMISSION_KEY_MANAGE);
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
    @RequireApiPermission(SmartKmsServerConstant.API_PERMISSION_KEY_MANAGE)
    @DataPermissionOperation(resource = SmartKmsServerConstant.DATA_RESOURCE_KEY,
            action = SmartKmsServerConstant.DATA_ACTION_KEY_MANAGE)
    public ResponseEntity<String> rotate(@PathVariable String keyRef, @RequestBody String body,
                                         @RequestHeader("Idempotency-Key") String idempotencyKey,
                                         @CurrentDataAccessPlan DataAccessPlan plan,
                                         HttpServletRequest request) {
        ObjectNode input = object(body, "expectedRowVersion");
        KmsRequestContext context = requireApiPermission(scopedContext(request, plan, keyRef), SmartKmsServerConstant.API_PERMISSION_KEY_MANAGE);
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
    @RequireApiPermission(SmartKmsServerConstant.API_PERMISSION_KEY_DESTROY)
    @DataPermissionOperation(resource = SmartKmsServerConstant.DATA_RESOURCE_KEY,
            action = SmartKmsServerConstant.DATA_ACTION_KEY_DESTROY)
    public ResponseEntity<String> scheduleDestruction(@PathVariable String keyRef, @RequestBody String body,
                                                      @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                      @CurrentDataAccessPlan DataAccessPlan plan,
                                                      HttpServletRequest request) {
        ObjectNode input = object(body, "dueAt", "expectedRowVersion");
        KmsRequestContext context = requireApiPermission(scopedContext(request, plan, keyRef), SmartKmsServerConstant.API_PERMISSION_KEY_DESTROY);
        Instant dueAt = instant(input, "dueAt", true);
        String endpoint = "PUT:" + SmartKmsServerConstant.API_BASE_PATH + "/keys/" + keyRef + "/destruction";
        KmsManagementIdempotencyResult result = idempotencyService.execute(context.getPrincipal(), endpoint,
                idempotencyKey, context.getRequestId(), canonicalRequest(endpoint, input), () -> {
                    KmsKey key = keyManagementService.scheduleDestruction(context.getPrincipal(), keyRef, dueAt,
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
    @RequireApiPermission(SmartKmsServerConstant.API_PERMISSION_KEY_DESTROY)
    @DataPermissionOperation(resource = SmartKmsServerConstant.DATA_RESOURCE_KEY,
            action = SmartKmsServerConstant.DATA_ACTION_KEY_DESTROY)
    public ResponseEntity<String> cancelDestruction(@PathVariable String keyRef, @RequestBody String body,
                                                    @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                    @CurrentDataAccessPlan DataAccessPlan plan,
                                                    HttpServletRequest request) {
        ObjectNode input = object(body, "expectedRowVersion");
        KmsRequestContext context = requireApiPermission(scopedContext(request, plan, keyRef), SmartKmsServerConstant.API_PERMISSION_KEY_DESTROY);
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
    @RequireApiPermission(SmartKmsServerConstant.SCOPE_READ_PUBLIC_KEY)
    public ResponseEntity<String> publicKey(@PathVariable String keyRef,
                                            @RequestParam(required = false) Integer version,
                                            HttpServletRequest request) {
        KmsRequestContext context = requireApiPermission(context(request), SmartKmsServerConstant.SCOPE_READ_PUBLIC_KEY);
        return jsonWithHeader(200, publicKey(publicKeyService.read(context.getPrincipal(), keyRef, version,
                context.getRequestId())), HttpHeaders.CACHE_CONTROL, "no-store");
    }

    /**
     * 查询当前逻辑密钥的全部可分发 ES256 公钥资源。
     */
    @GetMapping(value = "/{keyRef}/public-keys", produces = JSON_UTF8)
    @RequireApiPermission(SmartKmsServerConstant.SCOPE_READ_PUBLIC_KEY)
    public ResponseEntity<String> publicKeys(@PathVariable String keyRef, HttpServletRequest request) {
        KmsRequestContext context = requireApiPermission(context(request), SmartKmsServerConstant.SCOPE_READ_PUBLIC_KEY);
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
    @RequireApiPermission(SmartKmsServerConstant.API_PERMISSION_KEY_POLICY)
    @DataPermissionOperation(resource = SmartKmsServerConstant.DATA_RESOURCE_KEY,
            action = SmartKmsServerConstant.DATA_ACTION_KEY_POLICY)
    public ResponseEntity<String> createPolicy(@PathVariable String keyRef, @RequestBody String body,
                                               @RequestHeader("Idempotency-Key") String idempotencyKey,
                                               @CurrentDataAccessPlan DataAccessPlan plan,
                                               HttpServletRequest request) {
        ObjectNode input = object(body, "principalId", "keyVersion", "operation", "expiresAt");
        KmsRequestContext context = requireApiPermission(scopedContext(request, plan, keyRef), SmartKmsServerConstant.API_PERMISSION_KEY_POLICY);
        KmsKeyPolicy policy = KmsKeyPolicy.builder().policyId("pending").ownerPrincipalId(context.getPrincipal().getOwnerPrincipalId())
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
                    String displayName = displayNameResolver.resolveDisplayName(created.getPrincipalId());
                    return new KmsManagementIdempotencyResult(201,
                            KmsHttpJson.write(policy(created, displayName == null || displayName.isEmpty() ? null : displayName)),
                            keyRef + "/" + created.getPolicyId(), location, false);
                });
        return idempotent(result);
    }

    /**
     * 查询当前 DataPlan 范围内的逻辑密钥策略资源。
     */
    @GetMapping(value = "/{keyRef}/policies", produces = JSON_UTF8)
    @RequireApiPermission(SmartKmsServerConstant.API_PERMISSION_KEY_POLICY)
    @DataPermissionOperation(resource = SmartKmsServerConstant.DATA_RESOURCE_KEY,
            action = SmartKmsServerConstant.DATA_ACTION_KEY_POLICY)
    public ResponseEntity<String> policies(@PathVariable String keyRef, @CurrentDataAccessPlan DataAccessPlan plan,
                                           HttpServletRequest request) {
        KmsRequestContext context = requireApiPermission(scopedContext(request, plan, keyRef), SmartKmsServerConstant.API_PERMISSION_KEY_POLICY);
        List<KmsKeyPolicy> list = keyPolicyManagementService.list(context.getPrincipal(), keyRef,
                context.getRequestId());
        List<String> principals = new ArrayList<String>();
        for (KmsKeyPolicy policy : list) {
            principals.add(policy.getPrincipalId());
        }
        Map<String, String> displayNames = displayNameResolver.resolveDisplayNames(principals);
        List<Map<String, Object>> policies = new ArrayList<Map<String, Object>>();
        for (KmsKeyPolicy policy : list) {
            String displayName = displayNames.get(policy.getPrincipalId());
            policies.add(policy(policy, displayName == null || displayName.isEmpty() ? null : displayName));
        }
        Map<String, Object> response = map();
        response.put("items", policies);
        return json(200, response);
    }

    /**
     * 撤销精确策略资源。
     */
    @DeleteMapping(value = "/{keyRef}/policies/{policyId}", consumes = JSON)
    @RequireApiPermission(SmartKmsServerConstant.API_PERMISSION_KEY_POLICY)
    @DataPermissionOperation(resource = SmartKmsServerConstant.DATA_RESOURCE_KEY,
            action = SmartKmsServerConstant.DATA_ACTION_KEY_POLICY)
    public ResponseEntity<String> revokePolicy(@PathVariable String keyRef, @PathVariable String policyId,
                                               @RequestBody String body,
                                               @RequestHeader("Idempotency-Key") String idempotencyKey,
                                               @CurrentDataAccessPlan DataAccessPlan plan,
                                               HttpServletRequest request) {
        ObjectNode input = object(body, "expectedRowVersion");
        KmsRequestContext context = requireApiPermission(scopedContext(request, plan, keyRef), SmartKmsServerConstant.API_PERMISSION_KEY_POLICY);
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
        KmsKeyMetadata metadata = keyQueryRepository.findMetadata(context.getPrincipal().getOwnerPrincipalId(), source.getKeyRef())
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

    private Map<String, Object> policy(KmsKeyPolicy source, String principalDisplayName) {
        Map<String, Object> response = map();
        response.put("policyId", source.getPolicyId());
        response.put("keyRef", source.getKeyRef());
        response.put("principalId", source.getPrincipalId());
        response.put("principalDisplayName", principalDisplayName);
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
