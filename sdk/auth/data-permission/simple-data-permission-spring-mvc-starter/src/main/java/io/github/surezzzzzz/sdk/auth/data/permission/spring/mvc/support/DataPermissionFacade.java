package io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.support;

import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;

/**
 * 显式评估当前调用方数据访问计划。
 *
 * @author surezzzzzz
 */
public interface DataPermissionFacade {

    /**
     * 要求当前调用方可执行指定资源动作。
     *
     * @param resource 资源标识
     * @param action   动作标识
     * @return 数据访问计划
     */
    DataAccessPlan require(String resource, String action);
}
