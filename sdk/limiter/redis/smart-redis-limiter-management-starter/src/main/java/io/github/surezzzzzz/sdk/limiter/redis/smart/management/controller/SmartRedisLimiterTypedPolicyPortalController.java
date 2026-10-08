package io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.annotation.CurrentDataAccessPlan;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterDataDimension;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterRuleSelector;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.annotation.SmartRedisLimiterManagementComponent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.SmartRedisLimiterManagementConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.request.SmartRedisLimiterPolicyStateRequest;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.request.SmartRedisLimiterTypedRuleCreateRequest;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.request.SmartRedisLimiterTypedRuleUpdateRequest;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.response.*;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.directory.SmartRedisLimiterDirectoryObject;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.directory.SmartRedisLimiterDirectoryProvider;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.directory.SmartRedisLimiterServiceDeclaration;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.exception.SmartRedisLimiterManagementValidationException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.exception.SmartRedisLimiterTypedServiceUnavailableException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.view.SmartRedisLimiterPolicyDataScope;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.view.SmartRedisLimiterTypedRuleQuery;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.security.SmartRedisLimiterPolicyAuthorization;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.service.SmartRedisLimiterTypedPolicyManagementService;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.support.SmartRedisLimiterEtagHelper;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.typed.SmartRedisLimiterTypedPolicyKey;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.typed.SmartRedisLimiterTypedPolicySnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * v2 类型化策略 Portal 入口：目录、CRUD 与快照共用领域服务；
 * 机器请求不附加 PAGE 门槛，协议错配返回 409。
 */
@RestController
@SmartRedisLimiterManagementComponent
@RequiredArgsConstructor
@RequestMapping(SmartRedisLimiterManagementConstant.PLACEHOLDER_API_BASE_PATH)
@ConditionalOnProperty(prefix = SmartRedisLimiterManagementConstant.CONFIG_PREFIX,
        name = SmartRedisLimiterManagementConstant.CONFIG_FIELD_MODE,
        havingValue = SmartRedisLimiterManagementConstant.MODE_PORTAL)
public class SmartRedisLimiterTypedPolicyPortalController {

    private final ObjectProvider<SmartRedisLimiterTypedPolicyManagementService> managementServiceProvider;
    private final SmartRedisLimiterDirectoryProvider directoryProvider;
    private final SmartRedisLimiterPolicyAuthorization authorization;

    /**
     * 类型化快照：先鉴权再判断条件缓存，304 不绕过 DATA。
     */
    @GetMapping("/v2/policy/snapshot")
    @RequireApiPermission(SmartRedisLimiterManagementConstant.API_SNAPSHOT_READ)
    @DataPermissionOperation(resource = SmartRedisLimiterManagementConstant.DATA_POLICY_RESOURCE,
            action = SmartRedisLimiterManagementConstant.DATA_READ)
    public ResponseEntity<SmartRedisLimiterTypedPolicySnapshot> snapshot(
            @RequestParam String serviceCode,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch,
            @CurrentDataAccessPlan DataAccessPlan plan) {
        SmartRedisLimiterPolicyDataScope scope = SmartRedisLimiterPolicyDataScope.from(plan);
        scope.requireService(serviceCode);
        SmartRedisLimiterTypedPolicyManagementService.TypedSnapshotView view =
                managementService().snapshot(serviceCode);
        HttpHeaders headers = new HttpHeaders();
        headers.setETag(view.getEtag());
        headers.setCacheControl(SmartRedisLimiterManagementConstant.CACHE_CONTROL_NO_CACHE);
        return SmartRedisLimiterEtagHelper.matches(ifNoneMatch, view.getEtag())
                ? new ResponseEntity<>(headers, HttpStatus.NOT_MODIFIED)
                : new ResponseEntity<>((SmartRedisLimiterTypedPolicySnapshot) view.getSnapshot(),
                headers, HttpStatus.OK);
    }

    /**
     * 门户页面能力（no-store），协议模式从授权目录取得。
     */
    @GetMapping("/v2/policy/capabilities")
    @RequireApiPermission(SmartRedisLimiterManagementConstant.API_POLICY_READ)
    public ResponseEntity<SmartRedisLimiterPolicyCapabilitiesResponse> capabilities(
            @RequestParam(required = false) String serviceCode) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, SmartRedisLimiterManagementConstant.CACHE_CONTROL_NO_STORE)
                .body(authorization.capabilities(serviceCode));
    }

    /**
     * 授权目录内 TYPED_V2 服务列表（最小 ID/名称，DATA 过滤）。
     */
    @GetMapping("/v2/policy/services")
    @RequireApiPermission(SmartRedisLimiterManagementConstant.API_POLICY_READ)
    @DataPermissionOperation(resource = SmartRedisLimiterManagementConstant.DATA_POLICY_RESOURCE,
            action = SmartRedisLimiterManagementConstant.DATA_READ)
    public ResponseEntity<List<SmartRedisLimiterServiceSummaryResponse>> services(
            @CurrentDataAccessPlan DataAccessPlan plan) {
        SmartRedisLimiterPolicyDataScope scope = SmartRedisLimiterPolicyDataScope.from(plan);
        List<SmartRedisLimiterServiceSummaryResponse> summaries = new ArrayList<>();
        for (SmartRedisLimiterServiceDeclaration declaration : directoryProvider.listServices()) {
            if (SmartRedisLimiterManagementConstant.TYPED_CONTROL_MODE.equals(declaration.getControlMode())
                    && scope.allows(declaration.getServiceCode())) {
                summaries.add(new SmartRedisLimiterServiceSummaryResponse(declaration.getServiceCode(),
                        declaration.getDisplayName() == null || declaration.getDisplayName().isEmpty()
                                ? declaration.getServiceCode() : declaration.getDisplayName()));
            }
        }
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, SmartRedisLimiterManagementConstant.CACHE_CONTROL_NO_STORE)
                .body(summaries);
    }

    /**
     * 服务资源与维度声明（DATA 过滤；未声明服务 404）。
     */
    @GetMapping("/v2/policy/services/{serviceCode}/declarations")
    @RequireApiPermission(SmartRedisLimiterManagementConstant.API_POLICY_READ)
    @DataPermissionOperation(resource = SmartRedisLimiterManagementConstant.DATA_POLICY_RESOURCE,
            action = SmartRedisLimiterManagementConstant.DATA_READ)
    public ResponseEntity<SmartRedisLimiterServiceDeclaration> declarations(
            @PathVariable String serviceCode,
            @CurrentDataAccessPlan DataAccessPlan plan) {
        SmartRedisLimiterPolicyDataScope scope = SmartRedisLimiterPolicyDataScope.from(plan);
        scope.requireService(serviceCode);
        SmartRedisLimiterServiceDeclaration declaration = directoryProvider.findService(serviceCode);
        if (declaration == null
                || !SmartRedisLimiterManagementConstant.TYPED_CONTROL_MODE.equals(declaration.getControlMode())) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, SmartRedisLimiterManagementConstant.CACHE_CONTROL_NO_STORE)
                .body(declaration);
    }

    /**
     * 对象目录检索（客户/主体等稳定对象，最小 ID/名称）。
     */
    @GetMapping("/v2/policy/services/{serviceCode}/objects")
    @RequireApiPermission(SmartRedisLimiterManagementConstant.API_POLICY_READ)
    @DataPermissionOperation(resource = SmartRedisLimiterManagementConstant.DATA_POLICY_RESOURCE,
            action = SmartRedisLimiterManagementConstant.DATA_READ)
    public ResponseEntity<List<SmartRedisLimiterDirectoryObject>> objects(
            @PathVariable String serviceCode,
            @RequestParam String dimension,
            @RequestParam(required = false) String customType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "20") Integer limit,
            @CurrentDataAccessPlan DataAccessPlan plan) {
        SmartRedisLimiterPolicyDataScope scope = SmartRedisLimiterPolicyDataScope.from(plan);
        scope.requireService(serviceCode);
        int boundedLimit = limit == null || limit < 1
                ? SmartRedisLimiterManagementConstant.DEFAULT_OBJECT_QUERY_LIMIT
                : Math.min(limit, SmartRedisLimiterManagementConstant.MAX_OBJECT_QUERY_LIMIT);
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, SmartRedisLimiterManagementConstant.CACHE_CONTROL_NO_STORE)
                .body(directoryProvider.listObjects(serviceCode, dimension, customType, keyword, boundedLimit));
    }

    /**
     * 创建类型化规则（201 + 新规则 URI）。
     */
    @PostMapping("/v2/policy/rule")
    @RequireApiPermission(SmartRedisLimiterManagementConstant.API_POLICY_WRITE)
    @DataPermissionOperation(resource = SmartRedisLimiterManagementConstant.DATA_POLICY_RESOURCE,
            action = SmartRedisLimiterManagementConstant.DATA_WRITE)
    public ResponseEntity<SmartRedisLimiterTypedMutationResponse> create(
            @RequestBody SmartRedisLimiterTypedRuleCreateRequest request,
            @CurrentDataAccessPlan DataAccessPlan plan) {
        SmartRedisLimiterTypedMutationResponse result = managementService().create(
                toKey(request), request.getEnabled(), request.getLimits(),
                authorization.operator(), SmartRedisLimiterPolicyDataScope.from(plan));
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(result.getRule().getId()).toUri()).body(result);
    }

    /**
     * 类型化规则分页查询，count 由完整 DATA 谓词计算。
     */
    @GetMapping("/v2/policy/rules")
    @RequireApiPermission(SmartRedisLimiterManagementConstant.API_POLICY_READ)
    @DataPermissionOperation(resource = SmartRedisLimiterManagementConstant.DATA_POLICY_RESOURCE,
            action = SmartRedisLimiterManagementConstant.DATA_READ)
    public SmartRedisLimiterTypedPageResponse query(
            @RequestParam(required = false) String serviceCode,
            @RequestParam(required = false) String resourceCode,
            @RequestParam(required = false) String dimension,
            @RequestParam(required = false) String selector,
            @RequestParam(required = false) String namespace,
            @RequestParam(required = false) String customType,
            @RequestParam(required = false) String objectId,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @CurrentDataAccessPlan DataAccessPlan plan) {
        return managementService().query(SmartRedisLimiterTypedRuleQuery.builder()
                        .serviceCode(serviceCode).resourceCode(resourceCode).dimension(dimension)
                        .selector(selector).namespace(namespace).customType(customType)
                        .objectId(objectId).enabled(enabled).page(page).size(size).build(),
                SmartRedisLimiterPolicyDataScope.from(plan));
    }

    /**
     * 详情按持久化服务范围读取，不泄露范围外记录的存在。
     */
    @GetMapping("/v2/policy/rule/{id}")
    @RequireApiPermission(SmartRedisLimiterManagementConstant.API_POLICY_READ)
    @DataPermissionOperation(resource = SmartRedisLimiterManagementConstant.DATA_POLICY_RESOURCE,
            action = SmartRedisLimiterManagementConstant.DATA_READ)
    public SmartRedisLimiterTypedRuleResponse get(@PathVariable long id,
                                                  @CurrentDataAccessPlan DataAccessPlan plan) {
        return managementService().findById(id, SmartRedisLimiterPolicyDataScope.from(plan));
    }

    /**
     * 整体替换窗口（身份不变，CAS 行版本）。
     */
    @PutMapping("/v2/policy/rule/{id}")
    @RequireApiPermission(SmartRedisLimiterManagementConstant.API_POLICY_WRITE)
    @DataPermissionOperation(resource = SmartRedisLimiterManagementConstant.DATA_POLICY_RESOURCE,
            action = SmartRedisLimiterManagementConstant.DATA_WRITE)
    public SmartRedisLimiterTypedMutationResponse update(@PathVariable long id,
                                                         @RequestBody SmartRedisLimiterTypedRuleUpdateRequest request,
                                                         @CurrentDataAccessPlan DataAccessPlan plan) {
        if (request.getExpectedRowVersion() == null || request.getExpectedRowVersion() < 0) {
            throw new SmartRedisLimiterManagementValidationException(ErrorMessage.REQUEST_INVALID);
        }
        return managementService().update(id, request.getExpectedRowVersion(), request.getLimits(),
                authorization.operator(), SmartRedisLimiterPolicyDataScope.from(plan));
    }

    /**
     * 按行版本局部更新启用状态。
     */
    @PatchMapping("/v2/policy/rule/{id}")
    @RequireApiPermission(SmartRedisLimiterManagementConstant.API_POLICY_WRITE)
    @DataPermissionOperation(resource = SmartRedisLimiterManagementConstant.DATA_POLICY_RESOURCE,
            action = SmartRedisLimiterManagementConstant.DATA_WRITE)
    public SmartRedisLimiterTypedMutationResponse state(
            @PathVariable long id,
            @RequestBody SmartRedisLimiterPolicyStateRequest request,
            @CurrentDataAccessPlan DataAccessPlan plan) {
        if (request.getEnabled() == null || request.getExpectedRowVersion() == null
                || request.getExpectedRowVersion() < 0) {
            throw new SmartRedisLimiterManagementValidationException(ErrorMessage.REQUEST_INVALID);
        }
        return managementService().state(id, request.getExpectedRowVersion(), request.getEnabled(),
                authorization.operator(), SmartRedisLimiterPolicyDataScope.from(plan));
    }

    /**
     * 删除后返回变更结果及服务 revision。
     */
    @DeleteMapping("/v2/policy/rule/{id}")
    @RequireApiPermission(SmartRedisLimiterManagementConstant.API_POLICY_WRITE)
    @DataPermissionOperation(resource = SmartRedisLimiterManagementConstant.DATA_POLICY_RESOURCE,
            action = SmartRedisLimiterManagementConstant.DATA_WRITE)
    public SmartRedisLimiterTypedMutationResponse delete(@PathVariable long id,
                                                         @RequestParam long expectedRowVersion,
                                                         @CurrentDataAccessPlan DataAccessPlan plan) {
        if (expectedRowVersion < 0) {
            throw new SmartRedisLimiterManagementValidationException(ErrorMessage.REQUEST_INVALID);
        }
        return managementService().delete(id, expectedRowVersion, authorization.operator(),
                SmartRedisLimiterPolicyDataScope.from(plan));
    }

    /**
     * 构造类型化规则键：命名空间以宿主目录声明为准，不信任客户端传值。
     */
    private SmartRedisLimiterTypedPolicyKey toKey(SmartRedisLimiterTypedRuleCreateRequest request) {
        SmartRedisLimiterDataDimension dimension;
        SmartRedisLimiterRuleSelector selector;
        try {
            dimension = SmartRedisLimiterDataDimension.valueOf(request.getDimension());
            selector = SmartRedisLimiterRuleSelector.valueOf(request.getSelector());
        } catch (IllegalArgumentException ex) {
            throw new SmartRedisLimiterManagementValidationException(ErrorMessage.REQUEST_INVALID);
        }
        SmartRedisLimiterServiceDeclaration declaration = directoryProvider
                .findService(request.getServiceCode());
        String namespace = declaration == null ? request.getNamespace()
                : declaration.getNamespaces().getOrDefault(dimension.name(), request.getNamespace());
        return new SmartRedisLimiterTypedPolicyKey(request.getServiceCode(), request.getResourceCode(),
                dimension, selector, namespace, request.getCustomType(),
                request.getObjectId());
    }

    private SmartRedisLimiterTypedPolicyManagementService managementService() {
        SmartRedisLimiterTypedPolicyManagementService service = managementServiceProvider.getIfAvailable();
        if (service == null) {
            throw new SmartRedisLimiterTypedServiceUnavailableException();
        }
        return service;
    }


}
