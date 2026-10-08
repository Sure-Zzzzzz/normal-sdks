package io.github.surezzzzzz.sdk.limiter.redis.smart.management.security;

import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataPermissionRequest;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceSubjectType;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.SmartRedisLimiterManagementConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.response.SmartRedisLimiterPolicyCapabilitiesResponse;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.exception.SmartRedisLimiterManagementAccessDeniedException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.view.SmartRedisLimiterPolicyDataScope;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.SmartRedisLimiterPolicyValidationHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;

/**
 * 只从公共资源层的已验证上下文读取三权，不认识任何身份提供方。
 */
@Slf4j
public class SmartRedisLimiterPolicyAuthorization {
    /**
     * 取得已认证且仍有效的授权上下文，不接受普通管理员 Session。
     */
    public VerifiedResourceContext context() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof VerifiedResourceContext)) {
            throw new SmartRedisLimiterManagementAccessDeniedException();
        }
        VerifiedResourceContext context = (VerifiedResourceContext) authentication.getPrincipal();
        if (!context.getApplicationAuthorization().isAdmitted()
                || !context.getApplicationAuthorization().getExpiresAt().isAfter(Instant.now())) {
            throw new SmartRedisLimiterManagementAccessDeniedException();
        }
        return context;
    }

    /**
     * 返回门户展示能力，人员类型不代表业务 API 必须具备 PAGE。
     *
     * <p>能力接口不落业务数据，不参与 @DataPermissionOperation 注解链；
     * API 权限与写范围在此显式评估，语义与注解链一致。
     */
    public SmartRedisLimiterPolicyCapabilitiesResponse capabilities(String serviceCode) {
        VerifiedResourceContext context = context();
        if (!context.getApplicationAuthorization().getApiPermissions().contains(
                SmartRedisLimiterManagementConstant.API_POLICY_READ)) {
            throw new SmartRedisLimiterManagementAccessDeniedException();
        }
        if (context.getPrincipal().getSubjectType() != ResourceSubjectType.HUMAN) {
            throw new SmartRedisLimiterManagementAccessDeniedException();
        }
        String normalized = serviceCode == null ? null
                : SmartRedisLimiterPolicyValidationHelper.normalizeServiceCode(serviceCode);
        boolean page = context.getApplicationAuthorization().getPagePermissions().contains(
                SmartRedisLimiterManagementConstant.PAGE_POLICY);
        boolean write = false;
        if (page && context.getApplicationAuthorization().getApiPermissions().contains(
                SmartRedisLimiterManagementConstant.API_POLICY_WRITE)) {
            DataAccessPlan plan = context.getApplicationAuthorization().getDataGrantDocument() == null
                    ? DataAccessPlan.deny()
                    : DataAccessPlan.evaluate(context.getApplicationAuthorization().getDataGrantDocument(),
                    new DataPermissionRequest(SmartRedisLimiterManagementConstant.DATA_POLICY_RESOURCE,
                            SmartRedisLimiterManagementConstant.DATA_WRITE));
            SmartRedisLimiterPolicyDataScope scope = SmartRedisLimiterPolicyDataScope.from(plan);
            write = normalized == null ? scope.allowsAny() : scope.allows(normalized);
        }
        return new SmartRedisLimiterPolicyCapabilitiesResponse(page, write);
    }

    /**
     * 操作人采用已验证稳定主体标识，绝不回退配置管理员。
     */
    public String operator() {
        VerifiedResourceContext context = context();
        return context.getPrincipal().getSourceId().getValue() + ":" + context.getPrincipal().getSubjectId();
    }
}
