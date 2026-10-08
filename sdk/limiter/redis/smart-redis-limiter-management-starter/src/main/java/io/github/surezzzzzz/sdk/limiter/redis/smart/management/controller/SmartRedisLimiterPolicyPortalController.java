package io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.annotation.CurrentDataAccessPlan;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.annotation.SmartRedisLimiterManagementComponent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.SmartRedisLimiterManagementConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.request.SmartRedisLimiterPolicyCreateRequest;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.request.SmartRedisLimiterPolicyStateRequest;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.request.SmartRedisLimiterPolicyUpdateRequest;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.response.SmartRedisLimiterPolicyCapabilitiesResponse;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.response.SmartRedisLimiterPolicyMutationResponse;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.response.SmartRedisLimiterPolicyPageResponse;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.response.SmartRedisLimiterPolicyResponse;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.exception.SmartRedisLimiterManagementValidationException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.view.SmartRedisLimiterPolicyDataScope;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.view.SmartRedisLimiterPolicyQuery;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.view.SmartRedisLimiterPolicySnapshotView;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.security.SmartRedisLimiterPolicyAuthorization;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.service.SmartRedisLimiterPolicyManagementService;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.service.SmartRedisLimiterPolicySnapshotService;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.support.SmartRedisLimiterEtagHelper;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicySnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Portal 业务入口，共用领域服务；机器请求不附加 PAGE 门槛。
 */
@RestController
@SmartRedisLimiterManagementComponent
@RequiredArgsConstructor
@RequestMapping(SmartRedisLimiterManagementConstant.PLACEHOLDER_API_BASE_PATH)
@ConditionalOnProperty(prefix = SmartRedisLimiterManagementConstant.CONFIG_PREFIX,
        name = SmartRedisLimiterManagementConstant.CONFIG_FIELD_MODE,
        havingValue = SmartRedisLimiterManagementConstant.MODE_PORTAL)
public class SmartRedisLimiterPolicyPortalController {
    private final SmartRedisLimiterPolicyManagementService managementService;
    private final SmartRedisLimiterPolicySnapshotService snapshotService;
    private final SmartRedisLimiterPolicyAuthorization authorization;

    /**
     * 先鉴权再判断条件缓存，304 不绕过 DATA。
     */
    @GetMapping("/v1/policy/snapshot")
    @RequireApiPermission(SmartRedisLimiterManagementConstant.API_SNAPSHOT_READ)
    @DataPermissionOperation(resource = SmartRedisLimiterManagementConstant.DATA_POLICY_RESOURCE,
            action = SmartRedisLimiterManagementConstant.DATA_READ)
    public ResponseEntity<SmartRedisLimiterPolicySnapshot> snapshot(
            @RequestParam String serviceCode,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch,
            @CurrentDataAccessPlan DataAccessPlan plan) {
        SmartRedisLimiterPolicySnapshotView view = snapshotService.getSnapshot(serviceCode,
                SmartRedisLimiterPolicyDataScope.from(plan));
        HttpHeaders headers = new HttpHeaders();
        headers.setETag(view.getEtag());
        headers.setCacheControl(SmartRedisLimiterManagementConstant.CACHE_CONTROL_NO_CACHE);
        return SmartRedisLimiterEtagHelper.matches(ifNoneMatch, view.getEtag())
                ? new ResponseEntity<>(headers, HttpStatus.NOT_MODIFIED)
                : new ResponseEntity<>(view.getSnapshot(), headers, HttpStatus.OK);
    }

    /**
     * 返回门户页面能力，不缓存也不返回完整授权文档。
     */
    @GetMapping("/v1/policy/capabilities")
    @RequireApiPermission(SmartRedisLimiterManagementConstant.API_POLICY_READ)
    public ResponseEntity<SmartRedisLimiterPolicyCapabilitiesResponse> capabilities(
            @RequestParam(required = false) String serviceCode) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, SmartRedisLimiterManagementConstant.CACHE_CONTROL_NO_STORE)
                .body(authorization.capabilities(serviceCode));
    }

    /**
     * 创建目标服务内的精确策略。
     */
    @PostMapping("/v1/policy")
    @RequireApiPermission(SmartRedisLimiterManagementConstant.API_POLICY_WRITE)
    @DataPermissionOperation(resource = SmartRedisLimiterManagementConstant.DATA_POLICY_RESOURCE,
            action = SmartRedisLimiterManagementConstant.DATA_WRITE)
    public ResponseEntity<SmartRedisLimiterPolicyMutationResponse> create(
            @RequestBody SmartRedisLimiterPolicyCreateRequest request,
            @CurrentDataAccessPlan DataAccessPlan plan) {
        SmartRedisLimiterPolicyMutationResponse result = managementService.create(request, authorization.operator(),
                SmartRedisLimiterPolicyDataScope.from(plan));
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(result.getPolicy().getId()).toUri()).body(result);
    }

    /**
     * 按持久化服务范围读取详情，不泄露范围外记录的存在。
     */
    @GetMapping("/v1/policy/{id}")
    @RequireApiPermission(SmartRedisLimiterManagementConstant.API_POLICY_READ)
    @DataPermissionOperation(resource = SmartRedisLimiterManagementConstant.DATA_POLICY_RESOURCE,
            action = SmartRedisLimiterManagementConstant.DATA_READ)
    public SmartRedisLimiterPolicyResponse get(@PathVariable long id,
                                               @CurrentDataAccessPlan DataAccessPlan plan) {
        return managementService.findById(id, SmartRedisLimiterPolicyDataScope.from(plan));
    }

    /**
     * 列表与总数由同一完整 DATA 谓词计算。
     */
    @GetMapping("/v1/policy")
    @RequireApiPermission(SmartRedisLimiterManagementConstant.API_POLICY_READ)
    @DataPermissionOperation(resource = SmartRedisLimiterManagementConstant.DATA_POLICY_RESOURCE,
            action = SmartRedisLimiterManagementConstant.DATA_READ)
    public SmartRedisLimiterPolicyPageResponse query(
            @RequestParam(required = false) String serviceCode,
            @RequestParam(required = false) String resourceCode,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @CurrentDataAccessPlan DataAccessPlan plan) {
        return managementService.query(SmartRedisLimiterPolicyQuery.builder().serviceCode(serviceCode)
                        .resourceCode(resourceCode).subject(subject).enabled(enabled).page(page).size(size).build(),
                SmartRedisLimiterPolicyDataScope.from(plan));
    }

    /**
     * 整体替换窗口，保持策略三元组不变。
     */
    @PutMapping("/v1/policy/{id}")
    @RequireApiPermission(SmartRedisLimiterManagementConstant.API_POLICY_WRITE)
    @DataPermissionOperation(resource = SmartRedisLimiterManagementConstant.DATA_POLICY_RESOURCE,
            action = SmartRedisLimiterManagementConstant.DATA_WRITE)
    public SmartRedisLimiterPolicyMutationResponse update(@PathVariable long id,
                                                          @RequestBody SmartRedisLimiterPolicyUpdateRequest request,
                                                          @CurrentDataAccessPlan DataAccessPlan plan) {
        return managementService.update(id, request, authorization.operator(),
                SmartRedisLimiterPolicyDataScope.from(plan));
    }

    /**
     * 按行版本局部更新启用状态。
     */
    @PatchMapping("/v1/policy/{id}")
    @RequireApiPermission(SmartRedisLimiterManagementConstant.API_POLICY_WRITE)
    @DataPermissionOperation(resource = SmartRedisLimiterManagementConstant.DATA_POLICY_RESOURCE,
            action = SmartRedisLimiterManagementConstant.DATA_WRITE)
    public SmartRedisLimiterPolicyMutationResponse state(@PathVariable long id,
                                                         @RequestBody SmartRedisLimiterPolicyStateRequest request,
                                                         @CurrentDataAccessPlan DataAccessPlan plan) {
        if (request.getEnabled() == null || request.getExpectedRowVersion() == null
                || request.getExpectedRowVersion() < 0) {
            throw new SmartRedisLimiterManagementValidationException(ErrorMessage.REQUEST_INVALID);
        }
        return request.getEnabled()
                ? managementService.enable(id, request.getExpectedRowVersion(), authorization.operator(),
                SmartRedisLimiterPolicyDataScope.from(plan))
                : managementService.disable(id, request.getExpectedRowVersion(), authorization.operator(),
                SmartRedisLimiterPolicyDataScope.from(plan));
    }

    /**
     * 删除后返回变更结果及服务 revision。
     */
    @DeleteMapping("/v1/policy/{id}")
    @RequireApiPermission(SmartRedisLimiterManagementConstant.API_POLICY_WRITE)
    @DataPermissionOperation(resource = SmartRedisLimiterManagementConstant.DATA_POLICY_RESOURCE,
            action = SmartRedisLimiterManagementConstant.DATA_WRITE)
    public SmartRedisLimiterPolicyMutationResponse delete(@PathVariable long id,
                                                          @RequestParam long expectedRowVersion,
                                                          @CurrentDataAccessPlan DataAccessPlan plan) {
        if (expectedRowVersion < 0) {
            throw new SmartRedisLimiterManagementValidationException(ErrorMessage.REQUEST_INVALID);
        }
        return managementService.delete(id, expectedRowVersion, authorization.operator(),
                SmartRedisLimiterPolicyDataScope.from(plan));
    }

}
