package io.github.surezzzzzz.sdk.ops.middleware.mysql.adapter;

import io.github.surezzzzzz.sdk.ops.middleware.mysql.datasource.MysqlDatasourceStatusResponse;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.query.MysqlExplainRequest;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.query.MysqlExplainResponse;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.query.MysqlSelectRequest;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.query.MysqlSelectResponse;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.table.*;

/**
 * MySQL Route 安全只读视图适配器。
 *
 * @author surezzzzzz
 */
public interface MysqlOperationsViewAdapter {

    /**
     * 读取数据源安全状态。
     *
     * @param datasourceKey 启动期数据源标识
     * @return 安全状态投影
     */
    MysqlDatasourceStatusResponse getStatus(String datasourceKey);

    /**
     * 执行已校验的受控 SELECT。
     *
     * @param request 已校验查询请求
     * @return 受限结果窗口
     */
    MysqlSelectResponse select(MysqlSelectRequest request);

    /**
     * 执行已校验的受控 EXPLAIN。
     *
     * @param request 已校验查询请求
     * @return 固定执行计划投影
     */
    MysqlExplainResponse explain(MysqlExplainRequest request);

    MysqlTableListResponse listTables(MysqlTableListRequest request);

    MysqlTableColumnsResponse listTableColumns(MysqlTableColumnsRequest request);

    MysqlTableIndexesResponse listTableIndexes(MysqlTableIndexesRequest request);
}
