package io.github.surezzzzzz.sdk.ops.middleware.mysql;

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
}
