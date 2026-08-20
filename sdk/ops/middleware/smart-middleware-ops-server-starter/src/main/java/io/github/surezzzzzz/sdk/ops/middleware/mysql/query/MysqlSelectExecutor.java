package io.github.surezzzzzz.sdk.ops.middleware.mysql.query;

import io.github.surezzzzzz.sdk.ops.middleware.mysql.adapter.MysqlOperationsViewAdapter;
import io.github.surezzzzzz.sdk.ops.middleware.service.AbstractMiddlewareOpsExecutor;

/**
 * MySQL 受控 SELECT 执行器。
 *
 * @author surezzzzzz
 */
public class MysqlSelectExecutor extends AbstractMiddlewareOpsExecutor<MysqlSelectRequest, MysqlSelectResponse> {

    private final MysqlOperationsViewAdapter adapter;

    /**
     * 创建 MySQL 受控 SELECT 执行器。
     *
     * @param adapter MySQL Route 安全适配器
     */
    public MysqlSelectExecutor(MysqlOperationsViewAdapter adapter) {
        super(MysqlSelectRequest.class);
        this.adapter = adapter;
    }

    @Override
    public MysqlSelectResponse execute(MysqlSelectRequest request) {
        return adapter.select(request);
    }
}
