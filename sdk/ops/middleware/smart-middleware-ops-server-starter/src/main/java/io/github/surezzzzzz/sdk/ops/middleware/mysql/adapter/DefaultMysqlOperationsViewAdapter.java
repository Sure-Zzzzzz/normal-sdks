package io.github.surezzzzzz.sdk.ops.middleware.mysql.adapter;

import io.github.surezzzzzz.sdk.mysql.route.registry.SimpleMysqlRouteRegistry;
import io.github.surezzzzzz.sdk.mysql.route.template.MySqlRouteTemplate;
import io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.datasource.MysqlDatasourceStatusResponse;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.query.MysqlExplainRequest;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.query.MysqlExplainResponse;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.query.MysqlSelectRequest;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.query.MysqlSelectResponse;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.table.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
                selectStatement(request.getSql() + " LIMIT " + (request.getSize() + 1), request.getSize() + 1),
                (ResultSetExtractor<MysqlSelectResponse>) resultSet -> readResult(resultSet, request.getSize())));
    }

    @Override
    public MysqlExplainResponse explain(MysqlExplainRequest request) {
        requireDatasource(request.getDatasourceKey());
        return routeTemplate.executeOn(request.getDatasourceKey(), () -> routeTemplate.routingJdbcTemplate().query(
                explainStatement(request.getSql()), (ResultSetExtractor<MysqlExplainResponse>) this::readExplain));
    }

    @Override
    public MysqlTableListResponse listTables(MysqlTableListRequest request) {
        requireDatasource(request.getDatasourceKey());
        return routeTemplate.executeOn(request.getDatasourceKey(), () -> routeTemplate.routingJdbcTemplate().query(
                tableListStatement(request.getPrefix(), request.getSize() + 1),
                (ResultSetExtractor<MysqlTableListResponse>) resultSet -> readTables(resultSet, request.getSize())));
    }

    @Override
    public MysqlTableColumnsResponse listTableColumns(MysqlTableColumnsRequest request) {
        requireDatasource(request.getDatasourceKey());
        return routeTemplate.executeOn(request.getDatasourceKey(), () -> routeTemplate.routingJdbcTemplate().query(
                tableColumnsStatement(request.getTable()), (ResultSetExtractor<MysqlTableColumnsResponse>) this::readColumns));
    }

    @Override
    public MysqlTableIndexesResponse listTableIndexes(MysqlTableIndexesRequest request) {
        requireDatasource(request.getDatasourceKey());
        return routeTemplate.executeOn(request.getDatasourceKey(), () -> routeTemplate.routingJdbcTemplate().query(
                tableIndexesStatement(request.getTable()), (ResultSetExtractor<MysqlTableIndexesResponse>) this::readIndexes));
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

    private MysqlTableListResponse readTables(ResultSet resultSet, int limit) throws SQLException {
        List<MysqlTableListResponse.Item> items = new ArrayList<>();
        int remaining = maxResponseLength;
        String stopReason = "COMPLETED";
        while (resultSet.next()) {
            MysqlTableListResponse.Item item = MysqlTableListResponse.Item.builder()
                    .name(limitValue(resultSet.getString(1), maxCellLength))
                    .kind(limitValue(resultSet.getString(2), maxCellLength))
                    .engine(limitValue(resultSet.getString(3), maxCellLength))
                    .estimatedRows(numberValue(resultSet.getObject(4))).build();
            if (items.size() >= limit) {
                stopReason = "RESULT_LIMIT";
                break;
            }
            int itemLength = tableLength(item);
            if (itemLength > remaining) {
                stopReason = "RESPONSE_LIMIT";
                break;
            }
            remaining -= itemLength;
            items.add(item);
        }
        boolean traversalComplete = "COMPLETED".equals(stopReason);
        return MysqlTableListResponse.builder().items(items).limit(limit).returned(items.size())
                .truncated(!traversalComplete).traversalComplete(traversalComplete).stopReason(stopReason).build();
    }

    private MysqlTableColumnsResponse readColumns(ResultSet resultSet) throws SQLException {
        List<MysqlTableColumnsResponse.Item> items = new ArrayList<>();
        int remaining = maxResponseLength;
        boolean truncated = false;
        while (resultSet.next()) {
            if (items.size() >= maxColumns) {
                truncated = true;
                break;
            }
            MysqlTableColumnsResponse.Item item = MysqlTableColumnsResponse.Item.builder()
                    .name(limitValue(resultSet.getString(1), maxCellLength)).position(resultSet.getInt(2))
                    .dataType(limitValue(resultSet.getString(3), maxCellLength))
                    .columnType(limitValue(resultSet.getString(4), maxCellLength))
                    .nullable("YES".equalsIgnoreCase(resultSet.getString(5)))
                    .defaultPresent(resultSet.getObject(6) != null).keyRole(limitValue(resultSet.getString(7), maxCellLength))
                    .extra(limitValue(resultSet.getString(8), maxCellLength)).build();
            int itemLength = columnLength(item);
            if (itemLength > remaining) {
                truncated = true;
                break;
            }
            remaining -= itemLength;
            items.add(item);
        }
        return MysqlTableColumnsResponse.builder().items(items).truncated(truncated).build();
    }

    private MysqlTableIndexesResponse readIndexes(ResultSet resultSet) throws SQLException {
        Map<String, MysqlTableIndexesResponse.Item.ItemBuilder> indexes = new LinkedHashMap<>();
        Map<String, List<String>> columns = new LinkedHashMap<>();
        int remaining = maxResponseLength;
        boolean truncated = false;
        while (resultSet.next()) {
            String name = resultSet.getString(1);
            if (!indexes.containsKey(name) && indexes.size() >= maxColumns) {
                truncated = true;
                break;
            }
            if (!indexes.containsKey(name)) {
                String indexName = limitValue(name, maxCellLength);
                String type = limitValue(resultSet.getString(3), maxCellLength);
                int indexLength = valueLength(indexName) + valueLength(type);
                if (indexLength > remaining) {
                    truncated = true;
                    break;
                }
                remaining -= indexLength;
                indexes.put(name, MysqlTableIndexesResponse.Item.builder().name(indexName)
                        .unique(!resultSet.getBoolean(2)).type(type).visible(null));
                columns.put(name, new ArrayList<String>());
            }
            List<String> indexColumns = columns.get(name);
            if (indexColumns.size() >= maxColumns) {
                truncated = true;
                continue;
            }
            String column = limitValue(resultSet.getString(4), maxCellLength);
            if (valueLength(column) > remaining) {
                truncated = true;
                break;
            }
            remaining -= valueLength(column);
            indexColumns.add(column);
        }
        List<MysqlTableIndexesResponse.Item> items = new ArrayList<>();
        for (Map.Entry<String, MysqlTableIndexesResponse.Item.ItemBuilder> entry : indexes.entrySet()) {
            items.add(entry.getValue().columns(columns.get(entry.getKey())).build());
        }
        return MysqlTableIndexesResponse.builder().items(items).truncated(truncated).build();
    }

    private MysqlExplainResponse readExplain(ResultSet resultSet) throws SQLException {
        List<MysqlExplainResponse.Row> items = new ArrayList<>();
        int remaining = maxResponseLength;
        boolean truncated = false;
        while (resultSet.next()) {
            if (items.size() >= maxColumns) {
                truncated = true;
                break;
            }
            MysqlExplainResponse.Row item = MysqlExplainResponse.Row.builder()
                    .selectType(limitValue(resultSet.getString("select_type"), maxCellLength))
                    .table(limitValue(resultSet.getString("table"), maxCellLength))
                    .accessType(limitValue(resultSet.getString("type"), maxCellLength))
                    .possibleKeys(limitValue(resultSet.getString("possible_keys"), maxCellLength))
                    .key(limitValue(resultSet.getString("key"), maxCellLength))
                    .keyLength(limitValue(resultSet.getString("key_len"), maxCellLength))
                    .ref(limitValue(resultSet.getString("ref"), maxCellLength))
                    .estimatedRows(numberValue(resultSet.getObject("rows")))
                    .filteredPercent(decimalValue(resultSet.getObject("filtered")))
                    .extra(limitValue(resultSet.getString("Extra"), maxCellLength)).build();
            int itemLength = explainLength(item);
            if (itemLength > remaining) {
                truncated = true;
                break;
            }
            remaining -= itemLength;
            items.add(item);
        }
        return MysqlExplainResponse.builder().items(items).truncated(truncated).build();
    }

    private PreparedStatementCreator tableListStatement(String prefix, int maxRows) {
        return connection -> {
            PreparedStatement statement = connection.prepareStatement("SELECT TABLE_NAME, TABLE_TYPE, ENGINE, TABLE_ROWS "
                    + "FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() "
                    + "AND (? = '' OR LEFT(TABLE_NAME, CHAR_LENGTH(?)) = ?) ORDER BY TABLE_NAME LIMIT ?");
            statement.setString(1, prefix == null ? "" : prefix);
            statement.setString(2, prefix == null ? "" : prefix);
            statement.setString(3, prefix == null ? "" : prefix);
            statement.setInt(4, maxRows);
            configure(statement, maxRows);
            return statement;
        };
    }

    private PreparedStatementCreator tableColumnsStatement(String table) {
        return connection -> {
            PreparedStatement statement = connection.prepareStatement("SELECT COLUMN_NAME, ORDINAL_POSITION, DATA_TYPE, "
                    + "COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, COLUMN_KEY, EXTRA FROM information_schema.COLUMNS "
                    + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION");
            statement.setString(1, table);
            configure(statement, maxColumns + 1);
            return statement;
        };
    }

    private PreparedStatementCreator tableIndexesStatement(String table) {
        return connection -> {
            PreparedStatement statement = connection.prepareStatement("SELECT INDEX_NAME, NON_UNIQUE, INDEX_TYPE, COLUMN_NAME "
                    + "FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? "
                    + "ORDER BY INDEX_NAME, SEQ_IN_INDEX");
            statement.setString(1, table);
            configure(statement, maxColumns * maxColumns + 1);
            return statement;
        };
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

    private PreparedStatementCreator explainStatement(String sql) {
        return connection -> {
            PreparedStatement statement = connection.prepareStatement("EXPLAIN " + sql);
            configure(statement, maxColumns + 1);
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

    private int tableLength(MysqlTableListResponse.Item item) {
        return valueLength(item.getName()) + valueLength(item.getKind()) + valueLength(item.getEngine())
                + numberLength(item.getEstimatedRows());
    }

    private int columnLength(MysqlTableColumnsResponse.Item item) {
        return valueLength(item.getName()) + valueLength(item.getDataType()) + valueLength(item.getColumnType())
                + valueLength(item.getKeyRole()) + valueLength(item.getExtra())
                + numberLength(item.getPosition());
    }

    private int explainLength(MysqlExplainResponse.Row item) {
        return valueLength(item.getSelectType()) + valueLength(item.getTable()) + valueLength(item.getAccessType())
                + valueLength(item.getPossibleKeys()) + valueLength(item.getKey()) + valueLength(item.getKeyLength())
                + valueLength(item.getRef()) + valueLength(item.getExtra()) + numberLength(item.getEstimatedRows())
                + numberLength(item.getFilteredPercent());
    }

    private int numberLength(Object value) {
        return value == null ? 0 : String.valueOf(value).length();
    }

    private Long numberValue(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : null;
    }

    private Double decimalValue(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : null;
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
