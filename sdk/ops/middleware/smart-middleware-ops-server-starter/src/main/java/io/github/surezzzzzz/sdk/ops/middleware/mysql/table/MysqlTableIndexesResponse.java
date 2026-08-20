package io.github.surezzzzzz.sdk.ops.middleware.mysql.table;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * MySQL 索引目录响应。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class MysqlTableIndexesResponse {

    private final List<Item> items;
    private final Boolean truncated;

    /**
     * 索引安全摘要。
     *
     * @author surezzzzzz
     */
    @Getter
    @Builder
    public static class Item {

        private final String name;
        private final Boolean unique;
        private final String type;
        private final Boolean visible;
        private final List<String> columns;
    }
}
