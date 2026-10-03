package io.github.surezzzzzz.sdk.kms.server.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.annotation.CurrentDataAccessPlan;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsOperation;
import io.github.surezzzzzz.sdk.kms.core.constant.SmartKmsCoreConstant;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsValidationException;
import io.github.surezzzzzz.sdk.kms.core.model.KmsOwnerDestructionPolicy;
import io.github.surezzzzzz.sdk.kms.core.repository.KmsClock;
import io.github.surezzzzzz.sdk.kms.core.repository.KmsOwnerDestructionPolicyRepository;
import io.github.surezzzzzz.sdk.kms.core.support.KmsValidationHelper;
import io.github.surezzzzzz.sdk.kms.server.configuration.SmartKmsServerProperties;
import io.github.surezzzzzz.sdk.kms.server.constant.SmartKmsServerConstant;
import io.github.surezzzzzz.sdk.kms.server.model.KmsOwnerAccessScope;
import io.github.surezzzzzz.sdk.kms.server.service.KmsAuditPublisher;
import io.github.surezzzzzz.sdk.kms.server.service.KmsPrincipalResolver;
import io.github.surezzzzzz.sdk.kms.server.service.KmsRequestContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * owner 级销毁窗口政策控制器。
 *
 * <p>窗口政策属于 owner 的选择：自助通道（/me）读写当前认证主体自身政策；治理通道只读
 * DataPlan 范围内的 owner 政策。设置写是幂等 upsert + 审计，不消耗幂等键。</p>
 *
 * @author surezzzzzz
 */
@RestController
@RequestMapping(SmartKmsServerConstant.API_BASE_PATH)
public class KmsOwnerDestructionPolicyController extends KmsHttpControllerSupport {

    /**
     * 单侧提前量上限（秒）：3650 天，防溢出与荒谬值。
     */
    private static final long MAX_AHEAD_SECONDS = 315_360_000L;

    private final KmsOwnerDestructionPolicyRepository policyRepository;
    private final KmsAuditPublisher auditPublisher;
    private final KmsClock clock;

    /**
     * 创建销毁窗口政策控制器。
     */
    public KmsOwnerDestructionPolicyController(KmsPrincipalResolver principalResolver,
                                               SmartKmsServerProperties properties,
                                               KmsOwnerDestructionPolicyRepository policyRepository,
                                               KmsAuditPublisher auditPublisher, KmsClock clock) {
        super(principalResolver, properties);
        this.policyRepository = policyRepository;
        this.auditPublisher = auditPublisher;
        this.clock = clock;
    }

    /**
     * 读取当前 owner 的销毁窗口政策。
     */
    @GetMapping(value = "/me/destruction-policy", produces = JSON_UTF8)
    @RequireApiPermission(SmartKmsServerConstant.API_PERMISSION_KEY_DESTROY)
    public ResponseEntity<String> me(HttpServletRequest request) {
        KmsRequestContext context = requireApiPermission(context(request),
                SmartKmsServerConstant.API_PERMISSION_KEY_DESTROY);
        return json(200, policy(context.getPrincipal().getOwnerPrincipalId()));
    }

    /**
     * 写当前 owner 的销毁窗口政策（幂等 upsert；两侧均空 = 显式不限制）。
     */
    @PutMapping(value = "/me/destruction-policy", consumes = JSON, produces = JSON_UTF8)
    @RequireApiPermission(SmartKmsServerConstant.API_PERMISSION_KEY_DESTROY)
    public ResponseEntity<String> update(@RequestBody String body, HttpServletRequest request) {
        KmsRequestContext context = requireApiPermission(context(request),
                SmartKmsServerConstant.API_PERMISSION_KEY_DESTROY);
        ObjectNode payload = object(body, "minScheduleAheadSeconds", "maxScheduleAheadSeconds");
        Long minSeconds = optionalSeconds(payload, "minScheduleAheadSeconds");
        Long maxSeconds = optionalSeconds(payload, "maxScheduleAheadSeconds");
        if ((minSeconds != null && maxSeconds != null && minSeconds > maxSeconds)) {
            throw new KmsValidationException();
        }
        KmsOwnerDestructionPolicy saved = policyRepository.save(KmsOwnerDestructionPolicy.builder()
                .ownerPrincipalId(context.getPrincipal().getOwnerPrincipalId())
                .minScheduleAheadSeconds(minSeconds).maxScheduleAheadSeconds(maxSeconds)
                .updatedAt(clock.now()).rowVersion(currentRowVersion(context)).build());
        auditPublisher.allowed(context.getPrincipal(), null, null,
                KmsOperation.SET_OWNER_DESTRUCTION_POLICY, context.getRequestId(),
                SmartKmsCoreConstant.AUDIT_RESOURCE_TYPE_KEY, null, null, null, null);
        return json(200, policy(saved.getOwnerPrincipalId()));
    }

    /**
     * 治理面只读：读取 DataPlan 范围内指定 owner 的政策。
     */
    @GetMapping(value = "/admin/owners/{ownerPrincipalId}/destruction-policy", produces = JSON_UTF8)
    @RequireApiPermission(SmartKmsServerConstant.API_PERMISSION_KEY_DESTROY)
    @DataPermissionOperation(resource = SmartKmsServerConstant.DATA_RESOURCE_KEY,
            action = SmartKmsServerConstant.DATA_ACTION_KEY_DESTROY)
    public ResponseEntity<String> adminGet(@PathVariable String ownerPrincipalId,
                                           @CurrentDataAccessPlan DataAccessPlan plan,
                                           HttpServletRequest request) {
        requireApiPermission(context(request), SmartKmsServerConstant.API_PERMISSION_KEY_DESTROY);
        KmsOwnerAccessScope scope = KmsOwnerAccessScope.from(plan);
        scope.requireAllowed(ownerPrincipalId);
        return json(200, policy(KmsValidationHelper.requireOwnerPrincipalId(ownerPrincipalId)));
    }

    private long currentRowVersion(KmsRequestContext context) {
        return policyRepository.find(context.getPrincipal().getOwnerPrincipalId())
                .map(KmsOwnerDestructionPolicy::getRowVersion).orElse(0L);
    }

    private Map<String, Object> policy(String ownerPrincipalId) {
        KmsOwnerDestructionPolicy policy = policyRepository.find(ownerPrincipalId).orElse(null);
        Map<String, Object> response = map();
        response.put("ownerPrincipalId", ownerPrincipalId);
        response.put("exists", Boolean.valueOf(policy != null));
        response.put("minScheduleAheadSeconds",
                policy == null ? null : policy.getMinScheduleAheadSeconds());
        response.put("maxScheduleAheadSeconds",
                policy == null ? null : policy.getMaxScheduleAheadSeconds());
        response.put("rowVersion", Long.valueOf(policy == null ? 0L : policy.getRowVersion()));
        return response;
    }

    private Long optionalSeconds(ObjectNode payload, String field) {
        com.fasterxml.jackson.databind.JsonNode value = payload.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isIntegralNumber() || value.asLong() < 0L || value.asLong() > MAX_AHEAD_SECONDS) {
            throw new KmsValidationException();
        }
        return Long.valueOf(value.asLong());
    }
}
