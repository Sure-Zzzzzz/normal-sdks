package io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller;

import io.github.surezzzzzz.sdk.auth.aksk.resource.core.annotation.RequireExpression;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.annotation.SmartRedisLimiterManagementComponent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.SmartRedisLimiterManagementConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.request.SmartRedisLimiterPolicyCreateRequest;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.request.SmartRedisLimiterPolicyStateRequest;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.request.SmartRedisLimiterPolicyUpdateRequest;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.response.SmartRedisLimiterPolicyMutationResponse;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.response.SmartRedisLimiterPolicyPageResponse;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.response.SmartRedisLimiterPolicyResponse;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.exception.SmartRedisLimiterManagementValidationException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.view.SmartRedisLimiterPolicyQuery;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.view.SmartRedisLimiterPolicySnapshotView;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.security.SmartRedisLimiterManagementOperatorProvider;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.service.SmartRedisLimiterPolicyManagementService;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.service.SmartRedisLimiterPolicySnapshotService;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.support.SmartRedisLimiterEtagHelper;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicySnapshot;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 对外限流策略 API
 *
 * <p>聚合对外 {@code /api/v1/policy/**} 接口：策略快照（供 limiter 引擎拉取）与策略 CRUD（供第三方与未来 OpenAPI 调用）。
 * resource-server 默认接管并按 scope 鉴权；显式关闭时由 management 固定 token 兜底。
 *
 * @author surezzzzzz
 */
@RestController
@SmartRedisLimiterManagementComponent
@RequestMapping(SmartRedisLimiterManagementConstant.PLACEHOLDER_API_BASE_PATH)
@ConditionalOnProperty(
        prefix = SmartRedisLimiterManagementConstant.CONFIG_PREFIX + ".api",
        name = SmartRedisLimiterManagementConstant.CONFIG_FIELD_ENABLE,
        havingValue = "true")
public class SmartRedisLimiterPolicyController {

    private final SmartRedisLimiterPolicyManagementService managementService;
    private final SmartRedisLimiterPolicySnapshotService snapshotService;
    private final SmartRedisLimiterManagementOperatorProvider operatorProvider;

    /**
     * 构造对外限流策略 API
     */
    public SmartRedisLimiterPolicyController(
            SmartRedisLimiterPolicyManagementService managementService,
            SmartRedisLimiterPolicySnapshotService snapshotService,
            SmartRedisLimiterManagementOperatorProvider operatorProvider) {
        this.managementService = managementService;
        this.snapshotService = snapshotService;
        this.operatorProvider = operatorProvider;
    }

    /**
     * 获取服务完整已启用策略快照
     *
     * @param serviceCode 服务编码
     * @param ifNoneMatch 条件请求 ETag
     * @return 200 完整快照或 304
     */
    @GetMapping(SmartRedisLimiterManagementConstant.PATH_POLICY_SNAPSHOT)
    @RequireExpression(SmartRedisLimiterManagementConstant.POLICY_READ_EXPRESSION)
    public ResponseEntity<SmartRedisLimiterPolicySnapshot> getSnapshot(
            @RequestParam(SmartRedisLimiterManagementConstant.QUERY_PARAM_SERVICE_CODE)
            String serviceCode,
            @RequestHeader(value = SmartRedisLimiterManagementConstant.HEADER_IF_NONE_MATCH,
                    required = false)
            String ifNoneMatch) {
        SmartRedisLimiterPolicySnapshotView view = snapshotService.getSnapshot(serviceCode);
        HttpHeaders headers = new HttpHeaders();
        headers.set(SmartRedisLimiterManagementConstant.HEADER_ETAG, view.getEtag());
        headers.set(SmartRedisLimiterManagementConstant.HEADER_CACHE_CONTROL,
                SmartRedisLimiterManagementConstant.CACHE_CONTROL_NO_CACHE);
        if (SmartRedisLimiterEtagHelper.matches(ifNoneMatch, view.getEtag())) {
            return new ResponseEntity<>(headers, HttpStatus.NOT_MODIFIED);
        }
        return new ResponseEntity<>(view.getSnapshot(), headers, HttpStatus.OK);
    }

    /**
     * 创建精确策略
     */
    @PostMapping("/v1/policy")
    @RequireExpression(SmartRedisLimiterManagementConstant.POLICY_WRITE_EXPRESSION)
    public ResponseEntity<SmartRedisLimiterPolicyMutationResponse> create(
            @RequestBody SmartRedisLimiterPolicyCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(managementService.create(request, operatorProvider.getOperator()));
    }

    /**
     * 获取策略详情
     */
    @GetMapping("/v1/policy/{id}")
    @RequireExpression(SmartRedisLimiterManagementConstant.POLICY_WRITE_EXPRESSION)
    public SmartRedisLimiterPolicyResponse get(@PathVariable long id) {
        return managementService.findById(id);
    }

    /**
     * 分页查询策略
     */
    @GetMapping("/v1/policy")
    @RequireExpression(SmartRedisLimiterManagementConstant.POLICY_WRITE_EXPRESSION)
    public SmartRedisLimiterPolicyPageResponse query(
            @RequestParam(required = false) String serviceCode,
            @RequestParam(required = false) String resourceCode,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return managementService.query(SmartRedisLimiterPolicyQuery.builder()
                .serviceCode(serviceCode)
                .resourceCode(resourceCode)
                .subject(subject)
                .enabled(enabled)
                .page(page)
                .size(size)
                .build());
    }

    /**
     * 整体替换 limits
     */
    @PutMapping("/v1/policy/{id}")
    @RequireExpression(SmartRedisLimiterManagementConstant.POLICY_WRITE_EXPRESSION)
    public SmartRedisLimiterPolicyMutationResponse update(
            @PathVariable long id,
            @RequestBody SmartRedisLimiterPolicyUpdateRequest request) {
        return managementService.update(id, request, operatorProvider.getOperator());
    }

    /**
     * 局部更新策略启停状态
     */
    @PatchMapping("/v1/policy/{id}")
    @RequireExpression(SmartRedisLimiterManagementConstant.POLICY_WRITE_EXPRESSION)
    public SmartRedisLimiterPolicyMutationResponse updateState(
            @PathVariable long id,
            @RequestBody SmartRedisLimiterPolicyStateRequest request) {
        if (request == null || request.getEnabled() == null) {
            throw new SmartRedisLimiterManagementValidationException(ErrorMessage.POLICY_STATE_REQUIRED);
        }
        return request.getEnabled()
                ? managementService.enable(id, request.getExpectedRowVersion(), operatorProvider.getOperator())
                : managementService.disable(id, request.getExpectedRowVersion(), operatorProvider.getOperator());
    }

    /**
     * 删除策略
     */
    @DeleteMapping("/v1/policy/{id}")
    @RequireExpression(SmartRedisLimiterManagementConstant.POLICY_WRITE_EXPRESSION)
    public SmartRedisLimiterPolicyMutationResponse delete(
            @PathVariable long id,
            @RequestParam long expectedRowVersion) {
        return managementService.delete(
                id, expectedRowVersion, operatorProvider.getOperator());
    }
}
