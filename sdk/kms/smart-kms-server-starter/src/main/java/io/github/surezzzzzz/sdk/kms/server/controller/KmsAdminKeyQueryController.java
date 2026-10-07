package io.github.surezzzzzz.sdk.kms.server.controller;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.annotation.CurrentDataAccessPlan;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsAlgorithm;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsKeyPurpose;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsKeyState;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsNotFoundException;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsValidationException;
import io.github.surezzzzzz.sdk.kms.core.model.KmsKey;
import io.github.surezzzzzz.sdk.kms.server.configuration.SmartKmsServerProperties;
import io.github.surezzzzzz.sdk.kms.server.constant.SmartKmsServerConstant;
import io.github.surezzzzzz.sdk.kms.server.model.KmsOwnerAccessScope;
import io.github.surezzzzzz.sdk.kms.server.repository.KmsKeyMetadata;
import io.github.surezzzzzz.sdk.kms.server.repository.KmsKeyPage;
import io.github.surezzzzzz.sdk.kms.server.repository.KmsKeyQueryRepository;
import io.github.surezzzzzz.sdk.kms.server.service.KmsPrincipalDisplayNameResolver;
import io.github.surezzzzzz.sdk.kms.server.service.KmsPrincipalResolver;
import io.github.surezzzzzz.sdk.kms.server.support.KmsHttpJson;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * KMS 管理工作区密钥查询控制器。
 *
 * <p>所有列表、详情和总数均由同一份已验证 DataPlan 翻译得到，不能由请求参数扩大归属范围。</p>
 *
 * @author surezzzzzz
 */
@RestController
@RequestMapping(SmartKmsServerConstant.API_BASE_PATH + "/admin/keys")
public class KmsAdminKeyQueryController extends KmsHttpControllerSupport {

    private final KmsKeyQueryRepository keyQueryRepository;
    private final KmsPrincipalDisplayNameResolver displayNameResolver;

    /**
     * 创建管理员密钥查询控制器。
     *
     * @param principalResolver   可信认证主体解析器
     * @param properties          KMS 配置
     * @param keyQueryRepository  受 DataPlan 约束的密钥查询仓储
     * @param displayNameResolver 可选的主体显示名解析端口
     */
    public KmsAdminKeyQueryController(KmsPrincipalResolver principalResolver, SmartKmsServerProperties properties,
                                      KmsKeyQueryRepository keyQueryRepository,
                                      KmsPrincipalDisplayNameResolver displayNameResolver) {
        super(principalResolver, properties);
        this.keyQueryRepository = keyQueryRepository;
        this.displayNameResolver = displayNameResolver;
    }

    /**
     * 在当前 DataPlan 范围内分页查询密钥；归属筛选只能在范围内收窄。
     */
    @GetMapping(produces = JSON_UTF8)
    @RequireApiPermission(SmartKmsServerConstant.API_PERMISSION_KEY_READ)
    @DataPermissionOperation(resource = SmartKmsServerConstant.DATA_RESOURCE_KEY,
            action = SmartKmsServerConstant.DATA_ACTION_KEY_READ)
    public ResponseEntity<String> list(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(required = false) Integer size,
                                       @RequestParam(required = false) String alias,
                                       @RequestParam(required = false) String purpose,
                                       @RequestParam(required = false) String algorithm,
                                       @RequestParam(required = false) String state,
                                       @RequestParam(required = false) String ownerPrincipalId,
                                       @CurrentDataAccessPlan DataAccessPlan plan,
                                       HttpServletRequest request) {
        requireApiPermission(context(request), SmartKmsServerConstant.API_PERMISSION_KEY_READ);
        int resolvedSize = size == null ? pageDefaultSize() : size.intValue();
        String filterOwner = ownerPrincipalId == null || ownerPrincipalId.trim().isEmpty() ? null
                : ownerPrincipalId.trim();
        if (page < 1 || resolvedSize < 1 || resolvedSize > pageMaxSize(pageDefaultSize())
                || (purpose != null && KmsKeyPurpose.fromCode(purpose) == null)
                || (algorithm != null && KmsAlgorithm.fromCode(algorithm) == null)
                || (state != null && KmsKeyState.fromCode(state) == null)) {
            throw new KmsValidationException();
        }
        KmsKeyPage result = keyQueryRepository.findPage(KmsOwnerAccessScope.from(plan), alias, purpose, algorithm,
                state, filterOwner, ((long) page - 1L) * (long) resolvedSize, resolvedSize);
        Map<String, String> displayNames = displayNames(result.getItems());
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (KmsKeyMetadata metadata : result.getItems()) {
            items.add(key(metadata, displayNames.get(metadata.getKey().getOwnerPrincipalId())));
        }
        Map<String, Object> response = map();
        response.put("items", items);
        response.put("page", Integer.valueOf(page));
        response.put("size", Integer.valueOf(resolvedSize));
        response.put("total", Long.valueOf(result.getTotal()));
        return json(200, response);
    }

    /**
     * 查询当前 DataPlan 范围内单个密钥；无权与不存在均返回 404。
     */
    @GetMapping(value = "/{keyRef}", produces = JSON_UTF8)
    @RequireApiPermission(SmartKmsServerConstant.API_PERMISSION_KEY_READ)
    @DataPermissionOperation(resource = SmartKmsServerConstant.DATA_RESOURCE_KEY,
            action = SmartKmsServerConstant.DATA_ACTION_KEY_READ)
    public ResponseEntity<String> get(@PathVariable String keyRef, @CurrentDataAccessPlan DataAccessPlan plan,
                                      HttpServletRequest request) {
        requireApiPermission(context(request), SmartKmsServerConstant.API_PERMISSION_KEY_READ);
        KmsKeyMetadata metadata = keyQueryRepository.findMetadata(KmsOwnerAccessScope.from(plan), keyRef)
                .orElseThrow(KmsNotFoundException::new);
        String owner = metadata.getKey().getOwnerPrincipalId();
        return json(200, key(metadata, firstResolved(owner)));
    }

    /**
     * 批量解析当前页归属主体的显示名。
     */
    private Map<String, String> displayNames(List<KmsKeyMetadata> items) {
        List<String> owners = new ArrayList<String>();
        for (KmsKeyMetadata metadata : items) {
            owners.add(metadata.getKey().getOwnerPrincipalId());
        }
        return displayNameResolver.resolveDisplayNames(owners);
    }

    /**
     * 解析单个归属主体的显示名。
     */
    private String firstResolved(String ownerPrincipalId) {
        String displayName = displayNameResolver.resolveDisplayName(ownerPrincipalId);
        return displayName == null || displayName.isEmpty() ? null : displayName;
    }

    private Map<String, Object> key(KmsKeyMetadata metadata, String ownerDisplayName) {
        KmsKey key = metadata.getKey();
        Map<String, Object> response = map();
        response.put("ownerPrincipalId", key.getOwnerPrincipalId());
        response.put("ownerDisplayName", ownerDisplayName);
        response.put("keyRef", key.getKeyRef());
        response.put("keyAlias", key.getKeyAlias());
        response.put("purpose", key.getPurpose().getCode());
        response.put("algorithm", key.getAlgorithm().getCode());
        response.put("state", key.getState().getCode());
        response.put("activeVersion", key.getActiveVersion());
        response.put("rowVersion", Long.valueOf(key.getRowVersion()));
        response.put("createdAt", KmsHttpJson.utcMillis(metadata.getCreatedAt()));
        response.put("updatedAt", KmsHttpJson.utcMillis(metadata.getUpdatedAt()));
        return response;
    }
}
