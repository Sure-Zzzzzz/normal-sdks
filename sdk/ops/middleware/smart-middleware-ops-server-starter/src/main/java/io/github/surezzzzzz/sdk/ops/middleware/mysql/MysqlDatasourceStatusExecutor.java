package io.github.surezzzzzz.sdk.ops.middleware.mysql;

import io.github.surezzzzzz.sdk.ops.middleware.service.AbstractMiddlewareOpsExecutor;

/**
 * MySQL 数据源状态探测执行器。
 *
 * @author surezzzzzz
 */
public class MysqlDatasourceStatusExecutor
        extends AbstractMiddlewareOpsExecutor<MysqlDatasourceStatusRequest, MysqlDatasourceStatusResponse> {

    private final MysqlOperationsViewAdapter adapter;

    /**
     * 创建 MySQL 状态探测执行器。
     *
     * @param adapter MySQL Route 安全适配器
     */
    public MysqlDatasourceStatusExecutor(MysqlOperationsViewAdapter adapter) {
        super(MysqlDatasourceStatusRequest.class);
        this.adapter = adapter;
    }

    @Override
    public MysqlDatasourceStatusResponse execute(MysqlDatasourceStatusRequest request) {
        return adapter.getStatus(request.getDatasourceKey());
    }
}
