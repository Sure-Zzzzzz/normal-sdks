package io.github.surezzzzzz.sdk.ops.middleware.mysql.query;

import io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException;
import io.github.surezzzzzz.sdk.ops.middleware.service.DefaultMiddlewareOpsRequestValidator;
import org.springframework.http.HttpStatus;

/**
 * MySQL 受控 SELECT 请求校验器。
 *
 * @author surezzzzzz
 */
public class MysqlSelectRequestValidator extends DefaultMiddlewareOpsRequestValidator<MysqlSelectRequest> {

    private final int maxSize;
    private final MysqlControlledSelectPolicy policy;

    /**
     * 创建 MySQL 受控 SELECT 请求校验器。
     *
     * @param maxSqlLength SQL 最大字符数
     * @param maxSize      返回行数上限
     * @param maxColumns   返回列数上限
     */
    public MysqlSelectRequestValidator(int maxSqlLength, int maxSize, int maxColumns) {
        super(MysqlSelectRequest.class);
        this.maxSize = maxSize;
        this.policy = new MysqlControlledSelectPolicy(maxSqlLength, maxColumns);
    }

    @Override
    public void validate(MysqlSelectRequest request) {
        requireDatasource(request.getDatasourceKey());
        if (request.getSize() <= 0 || request.getSize() > maxSize) {
            throw new MiddlewareOpsException(HttpStatus.BAD_REQUEST, "结果数量超出允许范围");
        }
        policy.validate(request.getSql());
    }
}
