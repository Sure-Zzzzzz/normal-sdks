package io.github.surezzzzzz.sdk.auth.aksk.server.support;

import io.github.surezzzzzz.sdk.auth.aksk.server.constant.ManagementApiAuthorizationConstant;
import io.github.surezzzzzz.sdk.auth.aksk.server.constant.SimpleAkskServerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.server.exception.ManagementAccessDeniedException;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationDecision;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.support.DefaultApplicationAuthorizationEvaluator;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataAccessOutcome;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataPermissionRequest;

import javax.servlet.http.HttpServletRequest;

/**
 * 管理 REST 授权计划辅助。
 *
 * @author surezzzzzz
 */
public final class ManagementApiAuthorizationHelper {

    private static final DefaultApplicationAuthorizationEvaluator EVALUATOR =
            new DefaultApplicationAuthorizationEvaluator();

    private ManagementApiAuthorizationHelper() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }

    /**
     * 获取当前接口已验证的数据访问计划。
     *
     * @param request HTTP 请求
     * @return 数据访问计划
     */
    public static DataAccessPlan currentPlan(HttpServletRequest request) {
        Object plan = request.getAttribute(ManagementApiAuthorizationConstant.REQUEST_ATTRIBUTE_DATA_ACCESS_PLAN);
        if (!(plan instanceof DataAccessPlan)) {
            throw new IllegalStateException("管理 REST 授权计划不存在");
        }
        return (DataAccessPlan) plan;
    }

    /**
     * 基于当前已验证授权快照生成同一请求的另一资源动作计划。
     *
     * @param request HTTP 请求
     * @param resource 数据资源
     * @param action 数据动作
     * @return 数据访问计划
     */
    public static DataAccessPlan requiredPlan(HttpServletRequest request, String resource, String action,
                                              String permission) {
        Object authorization = request.getAttribute(
                ManagementApiAuthorizationConstant.REQUEST_ATTRIBUTE_APPLICATION_AUTHORIZATION);
        if (!(authorization instanceof ApplicationAuthorizationContext)) {
            throw new ManagementAccessDeniedException();
        }
        ApplicationAuthorizationContext context = (ApplicationAuthorizationContext) authorization;
        if (EVALUATOR.evaluateApi(context, SimpleAkskServerConstant.MANAGEMENT_APPLICATION_CODE, permission)
                != ApplicationAuthorizationDecision.ALLOW || context.getDataGrantDocument() == null) {
            throw new ManagementAccessDeniedException();
        }
        DataAccessPlan plan = DataAccessPlan.evaluate(context.getDataGrantDocument(),
                new DataPermissionRequest(resource, action));
        if (plan.getOutcome() == DataAccessOutcome.DENY) {
            throw new ManagementAccessDeniedException();
        }
        return plan;
    }
}
