package io.github.surezzzzzz.sdk.ops.middleware.mysql;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * MySQL 受控 EXPLAIN 安全投影。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class MysqlExplainResponse {

    private final List<Row> items;
    private final Boolean truncated;

    /**
     * 单行执行计划安全字段。
     *
     * @author surezzzzzz
     */
    @Getter
    @Builder
    public static class Row {

        private final String selectType;
        private final String table;
        private final String accessType;
        private final String possibleKeys;
        private final String key;
        private final String keyLength;
        private final String ref;
        private final Long estimatedRows;
        private final Double filteredPercent;
        private final String extra;
    }
}
