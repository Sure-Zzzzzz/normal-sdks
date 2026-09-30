package io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.support;

import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataAccessOutcome;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataPermissionRequest;
import io.github.surezzzzzz.sdk.auth.data.permission.core.spi.DataGrantDocumentSource;
import io.github.surezzzzzz.sdk.auth.data.permission.core.support.DataPermissionEvaluator;
import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.exception.DataPermissionAccessDeniedException;

/**
 * 默认数据权限门面。
 *
 * @author surezzzzzz
 */
public final class DefaultDataPermissionFacade implements DataPermissionFacade {

    private final DataPermissionEvaluator evaluator;
    private final DataGrantDocumentSource source;

    /**
     * 创建数据权限门面。
     *
     * @param evaluator 数据权限评估器
     * @param source    已验证授权文档来源
     */
    public DefaultDataPermissionFacade(DataPermissionEvaluator evaluator, DataGrantDocumentSource source) {
        this.evaluator = evaluator;
        this.source = source;
    }

    /**
     * 要求当前调用方可执行指定资源动作。
     *
     * @param resource 资源标识
     * @param action   动作标识
     * @return 数据访问计划
     */
    @Override
    public DataAccessPlan require(String resource, String action) {
        try {
            DataAccessPlan plan = evaluator.evaluate(source, new DataPermissionRequest(resource, action));
            if (plan.getOutcome() == DataAccessOutcome.DENY) {
                throw new DataPermissionAccessDeniedException("数据权限不足");
            }
            return plan;
        } catch (DataPermissionAccessDeniedException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DataPermissionAccessDeniedException("数据权限评估失败");
        }
    }
}
