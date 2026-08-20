package io.github.surezzzzzz.sdk.ops.middleware.mysql.table;

import io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException;
import io.github.surezzzzzz.sdk.ops.middleware.service.DefaultMiddlewareOpsRequestValidator;
import org.springframework.http.HttpStatus;

/**
 * MySQL 表与视图目录请求校验器。
 *
 * @author surezzzzzz
 */
public class MysqlTableListRequestValidator extends DefaultMiddlewareOpsRequestValidator<MysqlTableListRequest> {

    private final int maxResourceNameLength;
    private final int maxSize;

    public MysqlTableListRequestValidator(int maxResourceNameLength, int maxSize) {
        super(MysqlTableListRequest.class);
        this.maxResourceNameLength = maxResourceNameLength;
        this.maxSize = maxSize;
    }

    @Override
    public void validate(MysqlTableListRequest request) {
        requireDatasource(request.getDatasourceKey());
        if (!isLiteralPrefix(request.getPrefix())) {
            throw new MiddlewareOpsException(HttpStatus.BAD_REQUEST, "表名前缀不符合查询规范");
        }
        if (request.getSize() <= 0 || request.getSize() > maxSize) {
            throw new MiddlewareOpsException(HttpStatus.BAD_REQUEST, "结果数量超出允许范围");
        }
    }

    private boolean isLiteralPrefix(String value) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        if (value.length() > maxResourceNameLength) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isISOControl(character) || character == '%' || character == '_' || character == '\\') {
                return false;
            }
        }
        return true;
    }
}
