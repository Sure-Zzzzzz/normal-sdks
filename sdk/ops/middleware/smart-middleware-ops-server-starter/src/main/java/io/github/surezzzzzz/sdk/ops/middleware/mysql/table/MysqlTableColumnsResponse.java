package io.github.surezzzzzz.sdk.ops.middleware.mysql.table;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * MySQL 列目录响应。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class MysqlTableColumnsResponse {

    private final List<Item> items;
    private final Boolean truncated;

    /**
     * 列安全摘要。
     *
     * @author surezzzzzz
     */
    @Getter
    @Builder
    public static class Item {

        private final String name;
        private final Integer position;
        private final String dataType;
        private final String columnType;
        private final Boolean nullable;
        private final Boolean defaultPresent;
        private final String keyRole;
        private final String extra;
    }
}
