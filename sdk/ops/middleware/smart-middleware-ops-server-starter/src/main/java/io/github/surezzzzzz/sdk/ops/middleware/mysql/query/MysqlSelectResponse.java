package io.github.surezzzzzz.sdk.ops.middleware.mysql.query;

import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * MySQL 受控 SELECT 的受限结果窗口。
 *
 * @author surezzzzzz
 */
@Getter
public class MysqlSelectResponse {

    private final List<String> columns;
    private final List<List<String>> rows;
    private final Boolean truncated;

    @Builder
    public MysqlSelectResponse(List<String> columns, List<List<String>> rows, Boolean truncated) {
        this.columns = Collections.unmodifiableList(new ArrayList<>(columns));
        List<List<String>> copiedRows = new ArrayList<>();
        for (List<String> row : rows) {
            copiedRows.add(Collections.unmodifiableList(new ArrayList<>(row)));
        }
        this.rows = Collections.unmodifiableList(copiedRows);
        this.truncated = truncated;
    }
}
