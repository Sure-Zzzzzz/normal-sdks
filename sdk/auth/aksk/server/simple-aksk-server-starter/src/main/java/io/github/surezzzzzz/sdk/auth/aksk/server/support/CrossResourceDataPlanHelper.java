package io.github.surezzzzzz.sdk.auth.aksk.server.support;

import io.github.surezzzzzz.sdk.auth.aksk.server.annotation.SimpleAkskServerComponent;
import io.github.surezzzzzz.sdk.auth.aksk.server.constant.SimpleAkskServerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.server.exception.ManagementAccessDeniedException;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationDecision;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.support.DefaultApplicationAuthorizationEvaluator;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.support.DataPermissionFacade;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 跨资源数据访问计划 helper。
 * <p>
 * 资源层 {@code @RequireApiPermission} 只评估方法注解声明的单一 API 权限；端点内连带操作
 * 其他资源（如删 Client 时撤销其名下 Token）必须先补该资源的 API 权限层评估，再经
 * facade 取数据计划——双层语义对齐 3.0.x 的 ManagementApiAuthorizationHelper.requiredPlan。
 *
 * @author surezzzzzz
 */
@SimpleAkskServerComponent
@RequiredArgsConstructor
public class CrossResourceDataPlanHelper {

    private final DefaultApplicationAuthorizationEvaluator evaluator = new DefaultApplicationAuthorizationEvaluator();

    private final DataPermissionFacade dataPermissionFacade;

    /**
     * 要求当前调用方对指定资源动作同时具备 API 权限与数据计划。
     *
     * @param resource   数据资源
     * @param action     数据动作
     * @param permission 跨资源操作对应的精确 API 权限
     * @return 数据访问计划
     */
    public DataAccessPlan require(String resource, String action, String permission) {
        ApplicationAuthorizationContext authorization = currentAuthorization();
        if (authorization == null || evaluator.evaluateApi(authorization,
                SimpleAkskServerConstant.MANAGEMENT_APPLICATION_CODE, permission)
                != ApplicationAuthorizationDecision.ALLOW) {
            throw new ManagementAccessDeniedException();
        }
        return dataPermissionFacade.require(resource, action);
    }

    private ApplicationAuthorizationContext currentAuthorization() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof VerifiedResourceContext)) {
            return null;
        }
        return ((VerifiedResourceContext) authentication.getPrincipal()).getApplicationAuthorization();
    }
}
