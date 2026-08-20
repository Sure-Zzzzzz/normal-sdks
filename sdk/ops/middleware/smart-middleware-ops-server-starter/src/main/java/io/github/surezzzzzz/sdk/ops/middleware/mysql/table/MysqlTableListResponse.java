package io.github.surezzzzzz.sdk.ops.middleware.mysql.table;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * MySQL 表与视图目录响应。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class MysqlTableListResponse {

    private final List<Item> items;
    private final Integer limit;
    private final Integer returned;
    private final Boolean truncated;
    private final Boolean traversalComplete;
    private final String stopReason;

    /**
     * 表或视图安全摘要。
     *
     * @author surezzzzzz
     */
    @Getter
    @Builder
    public static class Item {

        private final String name;
        private final String kind;
        private final String engine;
        private final Long estimatedRows;
    }
}
