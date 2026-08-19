package io.github.surezzzzzz.sdk.ops.middleware.mysql;

import io.github.surezzzzzz.sdk.ops.middleware.service.AbstractMiddlewareOpsExecutor;

/**
 * MySQL 索引目录执行器。
 *
 * @author surezzzzzz
 */
public class MysqlTableIndexesExecutor extends AbstractMiddlewareOpsExecutor<MysqlTableIndexesRequest, MysqlTableIndexesResponse> {

    private final MysqlOperationsViewAdapter adapter;

    public MysqlTableIndexesExecutor(MysqlOperationsViewAdapter adapter) {
        super(MysqlTableIndexesRequest.class);
        this.adapter = adapter;
    }

    @Override
    public MysqlTableIndexesResponse execute(MysqlTableIndexesRequest request) {
        return adapter.listTableIndexes(request);
    }
}
