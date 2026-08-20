package io.github.surezzzzzz.sdk.ops.middleware.mysql.table;

import io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException;
import org.springframework.http.HttpStatus;

/**
 * MySQL 精确表名校验支持。
 *
 * @author surezzzzzz
 */
final class MysqlTableRequestValidator {

    private MysqlTableRequestValidator() {
        throw new UnsupportedOperationException("表名校验支持不能实例化");
    }

    static void validate(String table, int maxResourceNameLength) {
        if (table == null || table.isEmpty() || table.length() > maxResourceNameLength) {
            throw rejected();
        }
        for (int index = 0; index < table.length(); index++) {
            char character = table.charAt(index);
            if (!Character.isLetterOrDigit(character) && character != '_' && character != '$') {
                throw rejected();
            }
        }
    }

    private static MiddlewareOpsException rejected() {
        return new MiddlewareOpsException(HttpStatus.BAD_REQUEST, "表名不符合查询规范");
    }
}
