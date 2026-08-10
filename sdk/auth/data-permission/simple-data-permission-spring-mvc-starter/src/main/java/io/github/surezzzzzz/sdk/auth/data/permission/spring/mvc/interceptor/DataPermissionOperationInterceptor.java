package io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.interceptor;

import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataAccessOutcome;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.exception.DataPermissionAccessDeniedException;
import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.support.DataPermissionFacade;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 评估Controller声明的数据权限操作。
 *
 * @author surezzzzzz
 */
public final class DataPermissionOperationInterceptor implements HandlerInterceptor {

    /**
     * 请求内数据访问计划属性。
     */
    public static final String ATTRIBUTE_DATA_ACCESS_PLAN = DataPermissionOperationInterceptor.class.getName()
            + ".DATA_ACCESS_PLAN";

    private final DataPermissionFacade facade;

    /**
     * 创建数据权限操作拦截器。
     *
     * @param facade 数据权限门面
     */
    public DataPermissionOperationInterceptor(DataPermissionFacade facade) {
        this.facade = facade;
    }

    /**
     * 评估Controller方法的数据权限操作。
     *
     * @param request  HTTP请求
     * @param response HTTP响应
     * @param handler  Controller处理器
     * @return 是否允许继续执行
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        DataPermissionOperation operation = AnnotatedElementUtils.findMergedAnnotation(
                ((HandlerMethod) handler).getMethod(), DataPermissionOperation.class);
        if (operation == null) {
            return true;
        }
        try {
            DataAccessPlan plan = facade.require(operation.resource(), operation.action());
            if (plan == null || plan.getOutcome() == DataAccessOutcome.DENY) {
                throw new DataPermissionAccessDeniedException("数据权限不足");
            }
            request.setAttribute(ATTRIBUTE_DATA_ACCESS_PLAN, plan);
            return true;
        } catch (DataPermissionAccessDeniedException exception) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "数据权限不足");
            return false;
        } catch (RuntimeException exception) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "数据权限评估失败");
            return false;
        }
    }
}
