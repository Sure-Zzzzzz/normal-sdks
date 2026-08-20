package io.github.surezzzzzz.sdk.ops.middleware.mysql.datasource;

import io.github.surezzzzzz.sdk.ops.middleware.service.DefaultMiddlewareOpsRequestValidator;

/**
 * MySQL 数据源状态请求校验器。
 *
 * @author surezzzzzz
 */
public class MysqlDatasourceStatusRequestValidator extends DefaultMiddlewareOpsRequestValidator<MysqlDatasourceStatusRequest> {

    public MysqlDatasourceStatusRequestValidator() {
        super(MysqlDatasourceStatusRequest.class);
    }

    @Override
    public void validate(MysqlDatasourceStatusRequest request) {
        requireDatasource(request.getDatasourceKey());
    }
}
