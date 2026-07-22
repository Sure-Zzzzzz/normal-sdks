package io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller;

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
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.security.SmartRedisLimiterManagementOperatorProvider;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.service.SmartRedisLimiterPolicyManagementService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 限流策略管理 API
 *
 * @author surezzzzzz
 */
@RestController
@SmartRedisLimiterManagementComponent
@RequestMapping(SmartRedisLimiterManagementConstant.PLACEHOLDER_API_BASE_PATH
        + SmartRedisLimiterManagementConstant.PATH_POLICY_ADMIN)
@ConditionalOnProperty(
        prefix = SmartRedisLimiterManagementConstant.CONFIG_PREFIX + ".ui",
        name = SmartRedisLimiterManagementConstant.CONFIG_FIELD_ENABLE,
        havingValue = "true")
public class SmartRedisLimiterPolicyAdminController {

    private final SmartRedisLimiterPolicyManagementService managementService;
    private final SmartRedisLimiterManagementOperatorProvider operatorProvider;

    /**
     * 构造管理 API
     */
    public SmartRedisLimiterPolicyAdminController(
            SmartRedisLimiterPolicyManagementService managementService,
            SmartRedisLimiterManagementOperatorProvider operatorProvider) {
        this.managementService = managementService;
        this.operatorProvider = operatorProvider;
    }

    /**
     * 创建精确策略
     */
    @PostMapping
    public ResponseEntity<SmartRedisLimiterPolicyMutationResponse> create(
            @RequestBody SmartRedisLimiterPolicyCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(managementService.create(request, operatorProvider.getOperator()));
    }

    /**
     * 获取策略详情
     */
    @GetMapping("/{id}")
    public SmartRedisLimiterPolicyResponse get(@PathVariable long id) {
        return managementService.findById(id);
    }

    /**
     * 分页查询策略
     */
    @GetMapping
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
    @PutMapping("/{id}")
    public SmartRedisLimiterPolicyMutationResponse update(
            @PathVariable long id,
            @RequestBody SmartRedisLimiterPolicyUpdateRequest request) {
        return managementService.update(id, request, operatorProvider.getOperator());
    }

    /**
     * 局部更新策略启停状态
     */
    @PatchMapping("/{id}")
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
    @DeleteMapping("/{id}")
    public SmartRedisLimiterPolicyMutationResponse delete(
            @PathVariable long id,
            @RequestParam long expectedRowVersion) {
        return managementService.delete(
                id, expectedRowVersion, operatorProvider.getOperator());
    }
}
