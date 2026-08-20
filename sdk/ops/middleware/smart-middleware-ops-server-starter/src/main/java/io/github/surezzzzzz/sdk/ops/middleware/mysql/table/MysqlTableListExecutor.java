package io.github.surezzzzzz.sdk.ops.middleware.mysql.table;

import io.github.surezzzzzz.sdk.ops.middleware.mysql.adapter.MysqlOperationsViewAdapter;
import io.github.surezzzzzz.sdk.ops.middleware.service.AbstractMiddlewareOpsExecutor;

/**
 * MySQL 表与视图目录执行器。
 *
 * @author surezzzzzz
 */
public class MysqlTableListExecutor extends AbstractMiddlewareOpsExecutor<MysqlTableListRequest, MysqlTableListResponse> {

    private final MysqlOperationsViewAdapter adapter;

    public MysqlTableListExecutor(MysqlOperationsViewAdapter adapter) {
        super(MysqlTableListRequest.class);
        this.adapter = adapter;
    }

    @Override
    public MysqlTableListResponse execute(MysqlTableListRequest request) {
        return adapter.listTables(request);
    }
}
