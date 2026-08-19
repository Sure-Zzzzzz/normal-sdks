package io.github.surezzzzzz.sdk.ops.middleware.mysql;

import io.github.surezzzzzz.sdk.ops.middleware.service.DefaultMiddlewareOpsRequestValidator;

/**
 * MySQL 索引目录请求校验器。
 *
 * @author surezzzzzz
 */
public class MysqlTableIndexesRequestValidator extends DefaultMiddlewareOpsRequestValidator<MysqlTableIndexesRequest> {

    private final int maxResourceNameLength;

    public MysqlTableIndexesRequestValidator(int maxResourceNameLength) {
        super(MysqlTableIndexesRequest.class);
        this.maxResourceNameLength = maxResourceNameLength;
    }

    @Override
    public void validate(MysqlTableIndexesRequest request) {
        requireDatasource(request.getDatasourceKey());
        MysqlTableRequestValidator.validate(request.getTable(), maxResourceNameLength);
    }
}
