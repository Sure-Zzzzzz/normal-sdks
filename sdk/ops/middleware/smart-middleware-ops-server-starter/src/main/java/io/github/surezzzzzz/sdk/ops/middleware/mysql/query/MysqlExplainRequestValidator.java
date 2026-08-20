package io.github.surezzzzzz.sdk.ops.middleware.mysql.query;

import io.github.surezzzzzz.sdk.ops.middleware.service.DefaultMiddlewareOpsRequestValidator;

/**
 * MySQL 受控 EXPLAIN 请求校验器。
 *
 * @author surezzzzzz
 */
public class MysqlExplainRequestValidator extends DefaultMiddlewareOpsRequestValidator<MysqlExplainRequest> {

    private final MysqlControlledSelectPolicy policy;

    /**
     * 创建 MySQL 受控 EXPLAIN 请求校验器。
     *
     * @param maxSqlLength SQL 最大字符数
     * @param maxColumns   查询投影最大列数
     */
    public MysqlExplainRequestValidator(int maxSqlLength, int maxColumns) {
        super(MysqlExplainRequest.class);
        this.policy = new MysqlControlledSelectPolicy(maxSqlLength, maxColumns);
    }

    @Override
    public void validate(MysqlExplainRequest request) {
        requireDatasource(request.getDatasourceKey());
        policy.validate(request.getSql());
    }
}
