package io.github.surezzzzzz.sdk.ops.middleware.mysql;

import io.github.surezzzzzz.sdk.ops.middleware.service.AbstractMiddlewareOpsExecutor;

/**
 * MySQL 列目录执行器。
 *
 * @author surezzzzzz
 */
public class MysqlTableColumnsExecutor extends AbstractMiddlewareOpsExecutor<MysqlTableColumnsRequest, MysqlTableColumnsResponse> {

    private final MysqlOperationsViewAdapter adapter;

    public MysqlTableColumnsExecutor(MysqlOperationsViewAdapter adapter) {
        super(MysqlTableColumnsRequest.class);
        this.adapter = adapter;
    }

    @Override
    public MysqlTableColumnsResponse execute(MysqlTableColumnsRequest request) {
        return adapter.listTableColumns(request);
    }
}
