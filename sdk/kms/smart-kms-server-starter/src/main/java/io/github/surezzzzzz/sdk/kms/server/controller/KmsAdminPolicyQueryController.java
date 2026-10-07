package io.github.surezzzzzz.sdk.kms.server.controller;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.annotation.CurrentDataAccessPlan;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsOperation;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsValidationException;
import io.github.surezzzzzz.sdk.kms.server.configuration.SmartKmsServerProperties;
import io.github.surezzzzzz.sdk.kms.server.constant.SmartKmsServerConstant;
import io.github.surezzzzzz.sdk.kms.server.model.KmsOwnerAccessScope;
import io.github.surezzzzzz.sdk.kms.server.repository.KmsAdminPolicyPage;
import io.github.surezzzzzz.sdk.kms.server.repository.KmsAdminPolicyQueryRepository;
import io.github.surezzzzzz.sdk.kms.server.service.KmsPrincipalDisplayNameResolver;
import io.github.surezzzzzz.sdk.kms.server.service.KmsPrincipalResolver;
import io.github.surezzzzzz.sdk.kms.server.support.KmsHttpJson;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 治理视角跨钥策略分页查询控制器。
 *
 * <p>列表由同一份已验证 DataPlan 翻译得到，不能由请求参数扩大归属范围；
 * 策略写命令仍在既有按钥入口执行。</p>
 *
 * @author surezzzzzz
 */
@RestController
@RequestMapping(SmartKmsServerConstant.API_BASE_PATH + "/admin/policies")
public class KmsAdminPolicyQueryController extends KmsHttpControllerSupport {

    private final KmsAdminPolicyQueryRepository policyQueryRepository;
    private final KmsPrincipalDisplayNameResolver displayNameResolver;

    /**
     * 创建跨钥策略查询控制器。
     *
     * @param principalResolver     可信认证主体解析器
     * @param properties            KMS 配置
     * @param policyQueryRepository 跨钥策略分页查询仓储
     * @param displayNameResolver   可选的主体显示名解析端口
     */
    public KmsAdminPolicyQueryController(KmsPrincipalResolver principalResolver, SmartKmsServerProperties properties,
                                         KmsAdminPolicyQueryRepository policyQueryRepository,
                                         KmsPrincipalDisplayNameResolver displayNameResolver) {
        super(principalResolver, properties);
        this.policyQueryRepository = policyQueryRepository;
        this.displayNameResolver = displayNameResolver;
    }

    /**
     * 在当前 DataPlan 范围内分页查询策略，附带归属与被授权主体的可选显示名。
     */
    @GetMapping(produces = JSON_UTF8)
    @RequireApiPermission(SmartKmsServerConstant.API_PERMISSION_KEY_POLICY)
    @DataPermissionOperation(resource = SmartKmsServerConstant.DATA_RESOURCE_KEY,
            action = SmartKmsServerConstant.DATA_ACTION_KEY_POLICY)
    public ResponseEntity<String> list(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(required = false) Integer size,
                                       @RequestParam(required = false) String keyAlias,
                                       @RequestParam(required = false) String principalId,
                                       @RequestParam(required = false) String operation,
                                       @CurrentDataAccessPlan DataAccessPlan plan,
                                       HttpServletRequest request) {
        requireApiPermission(context(request), SmartKmsServerConstant.API_PERMISSION_KEY_POLICY);
        int resolvedSize = size == null ? pageDefaultSize() : size.intValue();
        String filterPrincipal = principalId == null || principalId.trim().isEmpty() ? null : principalId.trim();
        if (page < 1 || resolvedSize < 1 || resolvedSize > pageMaxSize(pageDefaultSize())
                || (operation != null && KmsOperation.fromCode(operation) == null)) {
            throw new KmsValidationException();
        }
        KmsAdminPolicyPage result = policyQueryRepository.findPage(KmsOwnerAccessScope.from(plan), keyAlias,
                filterPrincipal, operation, ((long) page - 1L) * (long) resolvedSize, resolvedSize);
        List<String> principals = new ArrayList<String>();
        for (KmsAdminPolicyPage.KmsAdminPolicyEntry entry : result.getItems()) {
            principals.add(entry.getPolicy().getOwnerPrincipalId());
            principals.add(entry.getPolicy().getPrincipalId());
        }
        Map<String, String> displayNames = displayNameResolver.resolveDisplayNames(principals);
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (KmsAdminPolicyPage.KmsAdminPolicyEntry entry : result.getItems()) {
            items.add(policy(entry, displayNames));
        }
        Map<String, Object> response = map();
        response.put("items", items);
        response.put("page", Integer.valueOf(page));
        response.put("size", Integer.valueOf(resolvedSize));
        response.put("total", Long.valueOf(result.getTotal()));
        return json(200, response);
    }

    /**
     * 将策略条目转换为附带密钥别名与显示名的安全响应。
     */
    private Map<String, Object> policy(KmsAdminPolicyPage.KmsAdminPolicyEntry entry,
                                       Map<String, String> displayNames) {
        io.github.surezzzzzz.sdk.kms.core.model.KmsKeyPolicy source = entry.getPolicy();
        Map<String, Object> response = map();
        response.put("policyId", source.getPolicyId());
        response.put("keyRef", source.getKeyRef());
        response.put("keyAlias", entry.getKeyAlias());
        response.put("ownerPrincipalId", source.getOwnerPrincipalId());
        response.put("ownerDisplayName", resolved(displayNames, source.getOwnerPrincipalId()));
        response.put("principalId", source.getPrincipalId());
        response.put("principalDisplayName", resolved(displayNames, source.getPrincipalId()));
        response.put("keyVersion", source.getKeyVersion());
        response.put("operation", source.getOperation().getCode());
        response.put("expiresAt", KmsHttpJson.utcMillis(source.getExpiresAt()));
        response.put("rowVersion", Long.valueOf(source.getRowVersion()));
        response.put("createdAt", KmsHttpJson.utcMillis(entry.getCreatedAt()));
        return response;
    }

    private String resolved(Map<String, String> displayNames, String principalId) {
        String displayName = displayNames.get(principalId);
        return displayName == null || displayName.isEmpty() ? null : displayName;
    }
}
