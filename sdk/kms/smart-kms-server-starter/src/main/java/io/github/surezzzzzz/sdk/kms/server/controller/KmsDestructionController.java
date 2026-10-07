package io.github.surezzzzzz.sdk.kms.server.controller;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.annotation.CurrentDataAccessPlan;
import io.github.surezzzzzz.sdk.kms.core.model.KmsDestructionJob;
import io.github.surezzzzzz.sdk.kms.core.model.KmsDestructionWorkerHealth;
import io.github.surezzzzzz.sdk.kms.core.service.DestructionWorkerHealthService;
import io.github.surezzzzzz.sdk.kms.server.configuration.SmartKmsServerProperties;
import io.github.surezzzzzz.sdk.kms.server.constant.SmartKmsServerConstant;
import io.github.surezzzzzz.sdk.kms.server.model.KmsOwnerAccessScope;
import io.github.surezzzzzz.sdk.kms.server.repository.KmsDestructionJobPage;
import io.github.surezzzzzz.sdk.kms.server.repository.KmsDestructionJobQueryRepository;
import io.github.surezzzzzz.sdk.kms.server.service.KmsDestructionWorkerLifecycle;
import io.github.surezzzzzz.sdk.kms.server.service.KmsPrincipalDisplayNameResolver;
import io.github.surezzzzzz.sdk.kms.server.service.KmsPrincipalResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * KMS 销毁任务与当前实例健康 REST 控制器。
 *
 * <p>销毁任务查询以已评估 DataPlan 限制；响应不包含领取令牌、密钥材料或底层异常细节。</p>
 *
 * @author surezzzzzz
 */
@RestController
public class KmsDestructionController extends KmsHttpControllerSupport {

    private final KmsDestructionJobQueryRepository jobQueryRepository;
    private final DestructionWorkerHealthService workerHealthService;
    private final KmsDestructionWorkerLifecycle workerLifecycle;
    private final KmsPrincipalDisplayNameResolver displayNameResolver;

    /**
     * 创建销毁任务管理控制器。
     *
     * @param principalResolver   可信认证主体解析器
     * @param properties          KMS 配置
     * @param jobQueryRepository  销毁任务分页查询仓储
     * @param workerHealthService worker 健康查询
     * @param workerLifecycle     worker 生命周期
     * @param displayNameResolver 可选的主体显示名解析端口
     */
    public KmsDestructionController(KmsPrincipalResolver principalResolver, SmartKmsServerProperties properties,
                                    KmsDestructionJobQueryRepository jobQueryRepository,
                                    DestructionWorkerHealthService workerHealthService,
                                    KmsDestructionWorkerLifecycle workerLifecycle,
                                    KmsPrincipalDisplayNameResolver displayNameResolver) {
        super(principalResolver, properties);
        this.jobQueryRepository = jobQueryRepository;
        this.workerHealthService = workerHealthService;
        this.workerLifecycle = workerLifecycle;
        this.displayNameResolver = displayNameResolver;
    }

    /**
     * 按已评估 DataPlan 归属范围分页查询销毁任务，携带归属主体及其可选显示名；归属筛选只在范围内收窄。
     */
    @GetMapping(value = SmartKmsServerConstant.API_BASE_PATH + "/destruction-jobs", produces = JSON_UTF8)
    @RequireApiPermission(SmartKmsServerConstant.API_PERMISSION_KEY_DESTROY)
    @DataPermissionOperation(resource = SmartKmsServerConstant.DATA_RESOURCE_KEY,
            action = SmartKmsServerConstant.DATA_ACTION_KEY_DESTROY)
    public ResponseEntity<String> jobs(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(required = false) Integer size,
                                       @RequestParam(required = false) String ownerPrincipalId,
                                       @CurrentDataAccessPlan DataAccessPlan plan,
                                       HttpServletRequest request) {
        requireApiPermission(context(request), SmartKmsServerConstant.API_PERMISSION_KEY_DESTROY);
        int defaultSize = pageDefaultSize();
        int maxSize = pageMaxSize(defaultSize);
        int resolvedSize = size == null ? defaultSize : size.intValue();
        String filterOwner = ownerPrincipalId == null || ownerPrincipalId.trim().isEmpty() ? null
                : ownerPrincipalId.trim();
        if (page < 1 || resolvedSize < 1 || resolvedSize > maxSize) {
            throw new io.github.surezzzzzz.sdk.kms.core.exception.KmsValidationException();
        }
        KmsDestructionJobPage result = jobQueryRepository.findPage(KmsOwnerAccessScope.from(plan), filterOwner,
                ((long) page - 1L) * (long) resolvedSize, resolvedSize);
        List<String> owners = new ArrayList<String>();
        for (KmsDestructionJob job : result.getItems()) {
            owners.add(job.getOwnerPrincipalId());
        }
        Map<String, String> displayNames = displayNameResolver.resolveDisplayNames(owners);
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (KmsDestructionJob job : result.getItems()) {
            String displayName = displayNames.get(job.getOwnerPrincipalId());
            items.add(job(job, displayName == null || displayName.isEmpty() ? null : displayName));
        }
        Map<String, Object> response = map();
        response.put("items", items);
        response.put("page", Integer.valueOf(page));
        response.put("size", Integer.valueOf(resolvedSize));
        response.put("total", Long.valueOf(result.getTotal()));
        return json(200, response);
    }

    /**
     * 查询当前 KMS 进程销毁 worker 的健康事实。
     */
    @GetMapping(value = SmartKmsServerConstant.API_BASE_PATH + "/destruction-worker/health", produces = JSON_UTF8)
    @RequireApiPermission(SmartKmsServerConstant.API_PERMISSION_KEY_DESTROY)
    public ResponseEntity<String> workerHealth(HttpServletRequest request) {
        requireApiPermission(context(request), SmartKmsServerConstant.API_PERMISSION_KEY_DESTROY);
        Map<String, Object> response = map();
        String instanceId = workerLifecycle.getResolvedInstanceId();
        response.put("running", Boolean.valueOf(workerLifecycle.isRunning()));
        if (instanceId == null) {
            response.put("instanceId", null);
            response.put("claimable", Boolean.FALSE);
            response.put("lastSuccessfulScanAt", null);
            response.put("consecutiveFailureCount", Integer.valueOf(0));
            response.put("oldestOverdueDelayMillis", null);
            return json(200, response);
        }
        KmsDestructionWorkerHealth health = workerHealthService.health(instanceId);
        response.put("instanceId", instanceId);
        response.put("claimable", Boolean.valueOf(health.isClaimable()));
        response.put("lastSuccessfulScanAt", utcMillis(health.getLastSuccessfulScanAt()));
        response.put("consecutiveFailureCount", Integer.valueOf(health.getConsecutiveFailureCount()));
        Duration oldestOverdueDelay = health.getOldestOverdueDelay();
        response.put("oldestOverdueDelayMillis", oldestOverdueDelay == null ? null
                : Long.valueOf(oldestOverdueDelay.toMillis()));
        return json(200, response);
    }

    /**
     * 转换不含敏感领取令牌的销毁任务响应，附带归属主体及其可选显示名。
     */
    private Map<String, Object> job(KmsDestructionJob source, String ownerDisplayName) {
        Map<String, Object> response = map();
        response.put("ownerPrincipalId", source.getOwnerPrincipalId());
        response.put("ownerDisplayName", ownerDisplayName);
        response.put("keyRef", source.getKeyRef());
        response.put("keyVersion", Integer.valueOf(source.getKeyVersion()));
        response.put("state", source.getState().getCode());
        response.put("dueAt", utcMillis(source.getDueAt()));
        response.put("claimUntil", utcMillis(source.getClaimUntil()));
        response.put("attemptCount", Integer.valueOf(source.getAttemptCount()));
        response.put("completedAt", utcMillis(source.getCompletedAt()));
        return response;
    }

    /**
     * 将时间统一序列化为既有 KMS UTC 毫秒字符串。
     */
    private String utcMillis(java.time.Instant instant) {
        return io.github.surezzzzzz.sdk.kms.server.support.KmsHttpJson.utcMillis(instant);
    }
}
