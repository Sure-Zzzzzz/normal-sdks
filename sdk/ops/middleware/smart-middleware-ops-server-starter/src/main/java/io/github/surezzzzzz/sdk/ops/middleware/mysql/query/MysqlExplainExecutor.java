package io.github.surezzzzzz.sdk.ops.middleware.mysql.query;

import io.github.surezzzzzz.sdk.ops.middleware.mysql.adapter.MysqlOperationsViewAdapter;
import io.github.surezzzzzz.sdk.ops.middleware.service.AbstractMiddlewareOpsExecutor;

/**
 * MySQL 受控 EXPLAIN 执行器。
 *
 * @author surezzzzzz
 */
public class MysqlExplainExecutor extends AbstractMiddlewareOpsExecutor<MysqlExplainRequest, MysqlExplainResponse> {

    private final MysqlOperationsViewAdapter adapter;

    /**
     * 创建 MySQL 受控 EXPLAIN 执行器。
     *
     * @param adapter MySQL Route 安全适配器
     */
    public MysqlExplainExecutor(MysqlOperationsViewAdapter adapter) {
        super(MysqlExplainRequest.class);
        this.adapter = adapter;
    }

    @Override
    public MysqlExplainResponse execute(MysqlExplainRequest request) {
        return adapter.explain(request);
    }
}
