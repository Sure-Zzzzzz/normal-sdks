package io.github.surezzzzzz.sdk.ops.middleware.mysql;

import io.github.surezzzzzz.sdk.ops.middleware.service.DefaultMiddlewareOpsRequestValidator;

/**
 * MySQL 列目录请求校验器。
 *
 * @author surezzzzzz
 */
public class MysqlTableColumnsRequestValidator extends DefaultMiddlewareOpsRequestValidator<MysqlTableColumnsRequest> {

    private final int maxResourceNameLength;

    public MysqlTableColumnsRequestValidator(int maxResourceNameLength) {
        super(MysqlTableColumnsRequest.class);
        this.maxResourceNameLength = maxResourceNameLength;
    }

    @Override
    public void validate(MysqlTableColumnsRequest request) {
        requireDatasource(request.getDatasourceKey());
        MysqlTableRequestValidator.validate(request.getTable(), maxResourceNameLength);
    }
}
