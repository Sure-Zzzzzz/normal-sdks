package io.github.surezzzzzz.sdk.auth.data.permission.core.support;

import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataPermissionRequest;

/**
 * 默认数据权限评估器。
 *
 * @author surezzzzzz
 */
public final class DefaultDataPermissionEvaluator implements DataPermissionEvaluator {

    /**
     * 按资源和动作精确匹配授权项。
     *
     * @param document 已验证的授权文档
     * @param request  资源动作请求
     * @return 访问计划
     */
    @Override
    public DataAccessPlan evaluate(DataGrantDocument document, DataPermissionRequest request) {
        return DataAccessPlan.evaluate(document, request);
    }
}
