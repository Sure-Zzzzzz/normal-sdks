package io.github.surezzzzzz.sdk.ops.middleware.mysql;

import io.github.surezzzzzz.sdk.mysql.route.registry.SimpleMysqlRouteRegistry;
import io.github.surezzzzzz.sdk.mysql.route.template.MySqlRouteTemplate;
import io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 仅通过 MySQL Route 显式作用域执行安全读取的适配器。
 *
 * @author surezzzzzz
 */
public class DefaultMysqlOperationsViewAdapter implements MysqlOperationsViewAdapter {

    private final SimpleMysqlRouteRegistry registry;
    private final MySqlRouteTemplate routeTemplate;
    private final long deadlineMillis;
    private final int maxColumns;
    private final int maxCellLength;
    private final int maxResponseLength;

    /**
     * 创建 MySQL Route 安全适配器。
     *
     * @param registry          MySQL Route 安全注册表
     * @param routeTemplate     MySQL Route 显式作用域模板
     * @param deadlineMillis    查询截止时间毫秒数
     * @param maxColumns        最大返回列数
     * @param maxCellLength     单个单元格最大字符数
     * @param maxResponseLength 单次响应最大字符数
     */
    public DefaultMysqlOperationsViewAdapter(SimpleMysqlRouteRegistry registry, MySqlRouteTemplate routeTemplate,
                                             long deadlineMillis, int maxColumns, int maxCellLength,
                                             int maxResponseLength) {
        this.registry = registry;
        this.routeTemplate = routeTemplate;
        this.deadlineMillis = deadlineMillis;
        this.maxColumns = maxColumns;
        this.maxCellLength = maxCellLength;
        this.maxResponseLength = maxResponseLength;
    }

    @Override
    public MysqlDatasourceStatusResponse getStatus(String datasourceKey) {
        requireDatasource(datasourceKey);
        long startedAt = System.currentTimeMillis();
        return routeTemplate.executeOn(datasourceKey, () -> routeTemplate.routingJdbcTemplate().query(
                statusStatement(), (ResultSetExtractor<MysqlDatasourceStatusResponse>) resultSet -> {
                    if (!resultSet.next()) {
                        throw unavailable();
                    }
                    return MysqlDatasourceStatusResponse.builder().datasourceKey(datasourceKey)
                            .database(resultSet.getString(1)).connected(true)
                            .durationMillis(System.currentTimeMillis() - startedAt).serverVersion(resultSet.getString(2))
                            .readOnly(toBoolean(resultSet.getObject(3))).superReadOnly(toBoolean(resultSet.getObject(4)))
                            .build();
                }));
    }

    @Override
    public MysqlSelectResponse select(MysqlSelectRequest request) {
        requireDatasource(request.getDatasourceKey());
        return routeTemplate.executeOn(request.getDatasourceKey(), () -> routeTemplate.routingJdbcTemplate().query(
                selectStatement(request.getSql(), request.getSize() + 1),
                (ResultSetExtractor<MysqlSelectResponse>) resultSet -> readResult(resultSet, request.getSize())));
    }

    private MysqlSelectResponse readResult(ResultSet resultSet, int maxRows) throws SQLException {
        ResultSetMetaData metadata = resultSet.getMetaData();
        int columnCount = metadata.getColumnCount();
        if (columnCount <= 0 || columnCount > maxColumns) {
            throw new MiddlewareOpsException(HttpStatus.BAD_REQUEST, "查询返回列数超出允许范围");
        }
        List<String> columns = new ArrayList<>();
        int remaining = maxResponseLength;
        boolean truncated = false;
        for (int index = 1; index <= columnCount; index++) {
            String column = metadata.getColumnLabel(index);
            String value = limitValue(column, Math.min(maxCellLength, remaining));
            columns.add(value);
            remaining -= valueLength(value);
            truncated = truncated || column != null && column.length() > valueLength(value);
        }
        List<List<String>> rows = new ArrayList<>();
        while (resultSet.next()) {
            if (rows.size() >= maxRows) {
                truncated = true;
                break;
            }
            List<String> row = new ArrayList<>();
            int rowLength = 0;
            boolean cellTruncated = false;
            for (int index = 1; index <= columnCount; index++) {
                String raw = resultSet.getString(index);
                String value = limitValue(raw, maxCellLength);
                row.add(value);
                rowLength += valueLength(value);
                cellTruncated = cellTruncated || raw != null && raw.length() > valueLength(value);
            }
            if (rowLength > remaining) {
                truncated = true;
                break;
            }
            remaining -= rowLength;
            rows.add(row);
            if (cellTruncated) {
                truncated = true;
                break;
            }
        }
        return MysqlSelectResponse.builder().columns(columns).rows(rows).truncated(truncated).build();
    }

    private PreparedStatementCreator statusStatement() {
        return connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "SELECT DATABASE(), VERSION(), @@read_only, @@super_read_only");
            configure(statement, 1);
            return statement;
        };
    }

    private PreparedStatementCreator selectStatement(String sql, int maxRows) {
        return connection -> {
            PreparedStatement statement = connection.prepareStatement(sql);
            configure(statement, maxRows);
            return statement;
        };
    }

    private void configure(PreparedStatement statement, int maxRows) throws SQLException {
        statement.setQueryTimeout((int) Math.max(1L, (deadlineMillis + 999L) / 1000L));
        statement.setMaxRows(maxRows);
    }

    private void requireDatasource(String datasourceKey) {
        if (!registry.containsDatasource(datasourceKey)) {
            throw new MiddlewareOpsException(HttpStatus.NOT_FOUND, "目标数据源不存在");
        }
    }

    private String limitValue(String value, int limit) {
        if (value == null) {
            return null;
        }
        return value.substring(0, Math.max(0, Math.min(limit, value.length())));
    }

    private int valueLength(String value) {
        return value == null ? 0 : value.length();
    }

    private Boolean toBoolean(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        if ("1".equals(text) || "true".equalsIgnoreCase(text)) {
            return true;
        }
        if ("0".equals(text) || "false".equalsIgnoreCase(text)) {
            return false;
        }
        return null;
    }

    private MiddlewareOpsException unavailable() {
        return new MiddlewareOpsException(HttpStatus.SERVICE_UNAVAILABLE, "MySQL 数据源状态探测暂不可用");
    }
}
