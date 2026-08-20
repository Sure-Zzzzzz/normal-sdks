package io.github.surezzzzzz.sdk.ops.middleware.test.cases;

import io.github.surezzzzzz.sdk.mysql.route.registry.SimpleMysqlRouteRegistry;
import io.github.surezzzzzz.sdk.mysql.route.template.MySqlRouteTemplate;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.adapter.DefaultMysqlOperationsViewAdapter;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.datasource.MysqlDatasourceStatusResponse;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.query.MysqlExplainRequest;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.query.MysqlExplainResponse;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.query.MysqlSelectRequest;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.query.MysqlSelectResponse;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.table.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.Arrays;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * MySQL Route 只读适配器作用域与结果边界测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class DefaultMysqlOperationsViewAdapterTest {

    @Test
    void shouldReadStatusInsideExplicitRouteScope() throws Exception {
        Fixture fixture = fixture(1500L, 4, 32, 128);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString(1)).thenReturn("orders");
        when(resultSet.getString(2)).thenReturn("8.4.2");
        when(resultSet.getObject(3)).thenReturn(1);
        when(resultSet.getObject(4)).thenReturn(0);
        stubStatusQuery(fixture.jdbcTemplate, resultSet);

        MysqlDatasourceStatusResponse response = fixture.adapter.getStatus("orders-reader");
        log.info("MySQL 状态：datasource={}，database={}，readOnly={}，superReadOnly={}",
                response.getDatasourceKey(), response.getDatabase(), response.getReadOnly(), response.getSuperReadOnly());

        assertEquals("orders-reader", response.getDatasourceKey());
        assertEquals("orders", response.getDatabase());
        assertTrue(response.getConnected());
        assertEquals("8.4.2", response.getServerVersion());
        assertTrue(response.getReadOnly());
        assertFalse(response.getSuperReadOnly());
        verify(fixture.routeTemplate).executeOn(eq("orders-reader"), any(Supplier.class));
        verify(fixture.routeTemplate).routingJdbcTemplate();

        PreparedStatement statement = configuredStatement(fixture.jdbcTemplate);
        verify(statement).setQueryTimeout(2);
        verify(statement).setMaxRows(1);
    }

    @Test
    void shouldExposeCurrentDatabaseAndKeepUnknownReadOnlyFlagsNullable() throws Exception {
        Fixture fixture = fixture(1000L, 4, 32, 128);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString(1)).thenReturn("other_database");
        when(resultSet.getString(2)).thenReturn("8.4.2");
        when(resultSet.getObject(3)).thenReturn("unknown");
        when(resultSet.getObject(4)).thenReturn(2);
        stubStatusQuery(fixture.jdbcTemplate, resultSet);

        MysqlDatasourceStatusResponse response = fixture.adapter.getStatus("orders-reader");
        log.info("MySQL 状态未知只读标记：readOnly={}，superReadOnly={}", response.getReadOnly(),
                response.getSuperReadOnly());

        assertEquals("other_database", response.getDatabase());
        assertNull(response.getReadOnly());
        assertNull(response.getSuperReadOnly());
    }

    @Test
    void shouldRejectUnavailableStatusWithoutDownstreamDetail() throws Exception {
        Fixture fixture = fixture(1000L, 4, 32, 128);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(false);
        stubStatusQuery(fixture.jdbcTemplate, resultSet);

        io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException exception =
                assertThrows(io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException.class,
                        () -> fixture.adapter.getStatus("orders-reader"));
        log.info("MySQL 状态不可用：status={}，message={}", exception.getStatus(), exception.getMessage());

        assertEquals(503, exception.getStatus().value());
        assertEquals("MySQL 数据源状态探测暂不可用", exception.getMessage());
    }

    @Test
    void shouldRejectMissingDatasourceBeforeRouteAccess() {
        SimpleMysqlRouteRegistry registry = mock(SimpleMysqlRouteRegistry.class);
        MySqlRouteTemplate routeTemplate = mock(MySqlRouteTemplate.class);
        DefaultMysqlOperationsViewAdapter adapter = new DefaultMysqlOperationsViewAdapter(registry,
                routeTemplate, 1000L, 4, 32, 128);

        io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException exception =
                assertThrows(io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException.class,
                        () -> adapter.getStatus("unknown"));
        log.info("MySQL 缺失数据源：status={}，message={}", exception.getStatus(), exception.getMessage());

        assertEquals(404, exception.getStatus().value());
        assertEquals("目标数据源不存在", exception.getMessage());
        verifyNoInteractions(routeTemplate);
    }

    @Test
    void shouldLimitSelectAndPreserveSqlNull() throws Exception {
        Fixture fixture = fixture(1500L, 4, 32, 128);
        ResultSet resultSet = resultSet(2, new String[]{"id", "name"}, true, true, false);
        when(resultSet.getString(1)).thenReturn("1");
        when(resultSet.getString(2)).thenReturn(null);
        stubSelectQuery(fixture.jdbcTemplate, resultSet);

        MysqlSelectResponse response = fixture.adapter.select(MysqlSelectRequest.builder()
                .datasourceKey("orders-reader").sql("SELECT id, name FROM orders").size(1).build());
        log.info("MySQL SELECT：columnCount={}，rowCount={}，truncated={}", response.getColumns().size(),
                response.getRows().size(), response.getTruncated());

        assertEquals(Arrays.asList("id", "name"), response.getColumns());
        assertEquals(1, response.getRows().size());
        assertEquals("1", response.getRows().get(0).get(0));
        assertNull(response.getRows().get(0).get(1));
        assertTrue(response.getTruncated());
        verify(fixture.routeTemplate).executeOn(eq("orders-reader"), any(Supplier.class));

        PreparedStatement statement = configuredStatement(fixture.jdbcTemplate);
        verify(statement).setQueryTimeout(2);
        verify(statement).setMaxRows(2);
        verifySelectSql(fixture.jdbcTemplate, "SELECT id, name FROM orders LIMIT 2");
    }

    @Test
    void shouldRejectSelectWithTooManyResultColumns() throws Exception {
        Fixture fixture = fixture(1000L, 1, 32, 128);
        ResultSet resultSet = resultSet(2, new String[]{"id", "name"}, false);
        stubSelectQuery(fixture.jdbcTemplate, resultSet);

        io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException exception =
                assertThrows(io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException.class,
                        () -> fixture.adapter.select(MysqlSelectRequest.builder().datasourceKey("orders-reader")
                                .sql("SELECT id, name FROM orders").size(1).build()));
        log.info("MySQL 列数限制：status={}，message={}", exception.getStatus(), exception.getMessage());

        assertEquals(400, exception.getStatus().value());
        assertEquals("查询返回列数超出允许范围", exception.getMessage());
    }

    @Test
    void shouldTruncateLongColumnLabelWithinResponseBudget() throws Exception {
        Fixture fixture = fixture(1000L, 4, 3, 128);
        ResultSet resultSet = resultSet(1, new String[]{"identifier"}, false);
        stubSelectQuery(fixture.jdbcTemplate, resultSet);

        MysqlSelectResponse response = fixture.adapter.select(MysqlSelectRequest.builder()
                .datasourceKey("orders-reader").sql("SELECT id FROM orders").size(10).build());
        log.info("MySQL 列名截断：columnCount={}，truncated={}", response.getColumns().size(),
                response.getTruncated());

        assertEquals(Arrays.asList("ide"), response.getColumns());
        assertTrue(response.getTruncated());
    }

    @Test
    void shouldNotMarkCompleteResponseAsTruncatedAtExactBudget() throws Exception {
        Fixture fixture = fixture(1000L, 4, 32, 8);
        ResultSet resultSet = resultSet(2, new String[]{"id", "name"}, true, false);
        when(resultSet.getString(1)).thenReturn("1");
        when(resultSet.getString(2)).thenReturn("x");
        stubSelectQuery(fixture.jdbcTemplate, resultSet);

        MysqlSelectResponse response = fixture.adapter.select(MysqlSelectRequest.builder()
                .datasourceKey("orders-reader").sql("SELECT id, name FROM orders").size(1).build());
        log.info("MySQL 精确预算：rowCount={}，truncated={}", response.getRows().size(), response.getTruncated());

        assertEquals(1, response.getRows().size());
        assertFalse(response.getTruncated());
    }

    @Test
    void shouldDropWholeRowWhenResponseBudgetIsExhausted() throws Exception {
        Fixture fixture = fixture(1000L, 4, 32, 9);
        ResultSet resultSet = resultSet(2, new String[]{"id", "name"}, true, false);
        when(resultSet.getString(1)).thenReturn("123");
        when(resultSet.getString(2)).thenReturn("456");
        stubSelectQuery(fixture.jdbcTemplate, resultSet);

        MysqlSelectResponse response = fixture.adapter.select(MysqlSelectRequest.builder()
                .datasourceKey("orders-reader").sql("SELECT id, name FROM orders").size(10).build());
        log.info("MySQL 响应预算：rowCount={}，truncated={}", response.getRows().size(), response.getTruncated());

        assertTrue(response.getRows().isEmpty());
        assertTrue(response.getTruncated());
    }

    @Test
    void shouldListTablesWithCurrentDatabaseBoundedPrefixAndResultLimit() throws Exception {
        Fixture fixture = fixture(1000L, 4, 32, 128);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true, true, true);
        when(resultSet.getString(1)).thenReturn("orders", "orders_archive", "payments");
        when(resultSet.getString(2)).thenReturn("BASE TABLE");
        when(resultSet.getString(3)).thenReturn("InnoDB");
        when(resultSet.getObject(4)).thenReturn(12L);
        stubSelectQuery(fixture.jdbcTemplate, resultSet);

        MysqlTableListResponse response = fixture.adapter.listTables(MysqlTableListRequest.builder()
                .datasourceKey("orders-reader").prefix("orders").size(2).build());

        assertEquals(2, response.getItems().size());
        assertEquals(2, response.getReturned());
        assertTrue(response.getTruncated());
        assertFalse(response.getTraversalComplete());
        assertEquals("RESULT_LIMIT", response.getStopReason());
        verify(fixture.routeTemplate).executeOn(eq("orders-reader"), any(Supplier.class));

        PreparedStatement statement = configuredStatement(fixture.jdbcTemplate);
        verify(statement).setString(1, "orders");
        verify(statement).setString(2, "orders");
        verify(statement).setString(3, "orders");
        verify(statement).setInt(4, 3);
        verify(statement).setMaxRows(3);
    }

    @Test
    void shouldMarkTableListTruncatedWhenResponseBudgetStopsTraversal() throws Exception {
        Fixture fixture = fixture(1000L, 4, 32, 5);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString(1)).thenReturn("orders");
        when(resultSet.getString(2)).thenReturn("BASE TABLE");
        when(resultSet.getString(3)).thenReturn("InnoDB");
        when(resultSet.getObject(4)).thenReturn(1L);
        stubSelectQuery(fixture.jdbcTemplate, resultSet);

        MysqlTableListResponse response = fixture.adapter.listTables(MysqlTableListRequest.builder()
                .datasourceKey("orders-reader").prefix(null).size(2).build());

        assertTrue(response.getItems().isEmpty());
        assertTrue(response.getTruncated());
        assertFalse(response.getTraversalComplete());
        assertEquals("RESPONSE_LIMIT", response.getStopReason());
    }

    @Test
    void shouldProjectColumnAndIndexMetadataWithoutDefaultValue() throws Exception {
        Fixture columnFixture = fixture(1000L, 4, 32, 128);
        ResultSet columnResultSet = mock(ResultSet.class);
        when(columnResultSet.next()).thenReturn(true, false);
        when(columnResultSet.getString(1)).thenReturn("id");
        when(columnResultSet.getInt(2)).thenReturn(1);
        when(columnResultSet.getString(3)).thenReturn("bigint");
        when(columnResultSet.getString(4)).thenReturn("bigint unsigned");
        when(columnResultSet.getString(5)).thenReturn("NO");
        when(columnResultSet.getObject(6)).thenReturn(42);
        when(columnResultSet.getString(7)).thenReturn("PRI");
        when(columnResultSet.getString(8)).thenReturn("auto_increment");
        stubSelectQuery(columnFixture.jdbcTemplate, columnResultSet);

        MysqlTableColumnsResponse columns = columnFixture.adapter.listTableColumns(MysqlTableColumnsRequest.builder()
                .datasourceKey("orders-reader").table("orders").build());

        assertEquals(1, columns.getItems().size());
        assertTrue(columns.getItems().get(0).getDefaultPresent());
        PreparedStatement columnStatement = configuredStatement(columnFixture.jdbcTemplate);
        verify(columnStatement).setString(1, "orders");
        verify(columnStatement).setMaxRows(5);

        Fixture indexFixture = fixture(1000L, 4, 32, 128);
        ResultSet indexResultSet = mock(ResultSet.class);
        when(indexResultSet.next()).thenReturn(true, false);
        when(indexResultSet.getString(1)).thenReturn("PRIMARY");
        when(indexResultSet.getBoolean(2)).thenReturn(false);
        when(indexResultSet.getString(3)).thenReturn("BTREE");
        when(indexResultSet.getString(4)).thenReturn("id");
        stubSelectQuery(indexFixture.jdbcTemplate, indexResultSet);

        MysqlTableIndexesResponse indexes = indexFixture.adapter.listTableIndexes(MysqlTableIndexesRequest.builder()
                .datasourceKey("orders-reader").table("orders").build());

        assertEquals(1, indexes.getItems().size());
        assertTrue(indexes.getItems().get(0).getUnique());
        assertNull(indexes.getItems().get(0).getVisible());
        assertEquals(Arrays.asList("id"), indexes.getItems().get(0).getColumns());
        PreparedStatement indexStatement = configuredStatement(indexFixture.jdbcTemplate);
        verify(indexStatement).setString(1, "orders");
        verify(indexStatement).setMaxRows(17);
    }

    @Test
    void shouldExecuteServerControlledExplainInsideRouteScope() throws Exception {
        Fixture fixture = fixture(1000L, 4, 32, 128);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getString("select_type")).thenReturn("SIMPLE");
        when(resultSet.getString("table")).thenReturn("orders");
        when(resultSet.getString("type")).thenReturn("ref");
        when(resultSet.getString("possible_keys")).thenReturn("idx_state");
        when(resultSet.getString("key")).thenReturn("idx_state");
        when(resultSet.getString("key_len")).thenReturn("4");
        when(resultSet.getString("ref")).thenReturn("const");
        when(resultSet.getObject("rows")).thenReturn(3L);
        when(resultSet.getObject("filtered")).thenReturn(100.0D);
        when(resultSet.getString("Extra")).thenReturn("Using where");
        stubSelectQuery(fixture.jdbcTemplate, resultSet);

        MysqlExplainResponse response = fixture.adapter.explain(MysqlExplainRequest.builder()
                .datasourceKey("orders-reader").sql("SELECT id FROM orders WHERE state = 'NEW'").build());

        assertEquals(1, response.getItems().size());
        assertEquals("orders", response.getItems().get(0).getTable());
        assertEquals("Using where", response.getItems().get(0).getExtra());
        assertFalse(response.getTruncated());
        verify(fixture.routeTemplate).executeOn(eq("orders-reader"), any(Supplier.class));

        ArgumentCaptor<PreparedStatementCreator> creatorCaptor = ArgumentCaptor.forClass(PreparedStatementCreator.class);
        verify(fixture.jdbcTemplate).query(creatorCaptor.capture(), any(ResultSetExtractor.class));
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement("EXPLAIN SELECT id FROM orders WHERE state = 'NEW'")).thenReturn(statement);
        creatorCaptor.getValue().createPreparedStatement(connection);
        verify(connection).prepareStatement("EXPLAIN SELECT id FROM orders WHERE state = 'NEW'");
        verify(statement).setMaxRows(5);
    }

    private Fixture fixture(long deadlineMillis, int maxColumns, int maxCellLength, int maxResponseLength) {
        SimpleMysqlRouteRegistry registry = mock(SimpleMysqlRouteRegistry.class);
        when(registry.containsDatasource("orders-reader")).thenReturn(true);
        MySqlRouteTemplate routeTemplate = mock(MySqlRouteTemplate.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(routeTemplate.routingJdbcTemplate()).thenReturn(jdbcTemplate);
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get())
                .when(routeTemplate).executeOn(eq("orders-reader"), any(Supplier.class));
        return new Fixture(routeTemplate, jdbcTemplate, new DefaultMysqlOperationsViewAdapter(registry, routeTemplate,
                deadlineMillis, maxColumns, maxCellLength, maxResponseLength));
    }

    private void stubStatusQuery(JdbcTemplate jdbcTemplate, ResultSet resultSet) {
        doAnswer(invocation -> {
            ResultSetExtractor<?> extractor = invocation.getArgument(1);
            return extractor.extractData(resultSet);
        }).when(jdbcTemplate).query(any(PreparedStatementCreator.class), any(ResultSetExtractor.class));
    }

    private void stubSelectQuery(JdbcTemplate jdbcTemplate, ResultSet resultSet) {
        doAnswer(invocation -> {
            ResultSetExtractor<?> extractor = invocation.getArgument(1);
            return extractor.extractData(resultSet);
        }).when(jdbcTemplate).query(any(PreparedStatementCreator.class), any(ResultSetExtractor.class));
    }

    private PreparedStatement configuredStatement(JdbcTemplate jdbcTemplate) throws Exception {
        ArgumentCaptor<PreparedStatementCreator> creatorCaptor = ArgumentCaptor.forClass(PreparedStatementCreator.class);
        verify(jdbcTemplate).query(creatorCaptor.capture(), any(ResultSetExtractor.class));
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(any(String.class))).thenReturn(statement);
        creatorCaptor.getValue().createPreparedStatement(connection);
        return statement;
    }

    private void verifySelectSql(JdbcTemplate jdbcTemplate, String expectedSql) throws Exception {
        ArgumentCaptor<PreparedStatementCreator> creatorCaptor = ArgumentCaptor.forClass(PreparedStatementCreator.class);
        verify(jdbcTemplate).query(creatorCaptor.capture(), any(ResultSetExtractor.class));
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(expectedSql)).thenReturn(statement);
        creatorCaptor.getValue().createPreparedStatement(connection);
        verify(connection).prepareStatement(expectedSql);
    }

    private ResultSet resultSet(int columnCount, String[] labels, Boolean... nextValues) {
        ResultSet resultSet = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        try {
            when(resultSet.getMetaData()).thenReturn(metadata);
            when(metadata.getColumnCount()).thenReturn(columnCount);
            for (int index = 0; index < labels.length; index++) {
                when(metadata.getColumnLabel(index + 1)).thenReturn(labels[index]);
            }
            when(resultSet.next()).thenReturn(nextValues[0], Arrays.copyOfRange(nextValues, 1, nextValues.length));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return resultSet;
    }

    private static class Fixture {

        private final MySqlRouteTemplate routeTemplate;
        private final JdbcTemplate jdbcTemplate;
        private final DefaultMysqlOperationsViewAdapter adapter;

        private Fixture(MySqlRouteTemplate routeTemplate, JdbcTemplate jdbcTemplate,
                        DefaultMysqlOperationsViewAdapter adapter) {
            this.routeTemplate = routeTemplate;
            this.jdbcTemplate = jdbcTemplate;
            this.adapter = adapter;
        }
    }
}
