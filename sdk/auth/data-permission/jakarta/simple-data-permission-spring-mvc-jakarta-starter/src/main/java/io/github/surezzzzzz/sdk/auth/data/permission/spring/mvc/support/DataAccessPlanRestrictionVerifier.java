package io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.support;

import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.exception.DataPermissionAccessDeniedException;

import java.util.Map;

/**
 * 校验单个业务目标是否满足完整数据授权项（MVC 薄壳，1.1.0 起）。
 *
 * <p>实现源已下沉 {@code core.support.DataAccessPlanRestrictionVerifier}；本壳保留旧 FQCN 供既有
 * 调用兼容——委托 core 实现，core 拒绝异常在壳边界回抛为本线薄壳异常（保证存量 catch 的 FQCN
 * 在飞语义不变）。新代码一律使用 core 版。</p>
 *
 * @author surezzzzzz
 */
public final class DataAccessPlanRestrictionVerifier {

    private DataAccessPlanRestrictionVerifier() {
        throw new UnsupportedOperationException("数据访问计划限制校验器不能实例化");
    }

    /**
     * 要求业务目标属于当前数据访问计划。
     *
     * @param plan       数据访问计划
     * @param dimensions 业务目标的受控维度
     */
    public static void requireTargetAllowed(DataAccessPlan plan, Map<String, String> dimensions) {
        try {
            io.github.surezzzzzz.sdk.auth.data.permission.core.support.DataAccessPlanRestrictionVerifier
                    .requireTargetAllowed(plan, dimensions);
        } catch (
                io.github.surezzzzzz.sdk.auth.data.permission.core.exception.DataPermissionAccessDeniedException exception) {
            throw new DataPermissionAccessDeniedException(exception.getMessage());
        }
    }

    /**
     * 判断业务目标是否满足至少一个完整授权项（直通 core 实现）。
     *
     * @param plan       数据访问计划
     * @param dimensions 业务目标的受控维度
     * @return 是否允许访问目标
     */
    public static boolean isTargetAllowed(DataAccessPlan plan, Map<String, String> dimensions) {
        return io.github.surezzzzzz.sdk.auth.data.permission.core.support.DataAccessPlanRestrictionVerifier
                .isTargetAllowed(plan, dimensions);
    }
}
