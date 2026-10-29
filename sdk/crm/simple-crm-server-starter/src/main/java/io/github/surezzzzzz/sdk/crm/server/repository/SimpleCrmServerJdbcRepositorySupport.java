package io.github.surezzzzzz.sdk.crm.server.repository;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

/**
 * JDBC 仓储基座：统一持有模块专用 NamedParameterJdbcTemplate 与参数/行映射助手。
 *
 * @author surezzzzzz
 */
public abstract class SimpleCrmServerJdbcRepositorySupport {

    /** COUNT 查询前缀。 */
    private static final String COUNT_PREFIX = "SELECT COUNT(*)";
    /** 同一查询的 FROM 边界。 */
    private static final String FROM_TOKEN = " FROM ";
    /** 固定主键排序和单页限制。 */
    private static final String WINDOW_SUFFIX = " ORDER BY id ASC LIMIT :limit";
    /** 页码模式的偏移。 */
    private static final String OFFSET_SUFFIX = " OFFSET :offset";
    /** 每页参数名。 */
    private static final String LIMIT_PARAMETER = "limit";
    /** 偏移参数名。 */
    private static final String OFFSET_PARAMETER = "offset";

    /**
     * 模块专用命名参数 JDBC 模板。
     */
    protected final NamedParameterJdbcTemplate jdbc;

    /**
     * 创建仓储基座。
     *
     * @param jdbc 模块专用 JDBC 模板
     */
    protected SimpleCrmServerJdbcRepositorySupport(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 取可空 Instant 列。
     *
     * @param rs  结果集
     * @param column 列名
     * @return Instant 或 null
     * @throws SQLException SQL 异常
     */
    protected static Instant instant(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toInstant();
    }

    /**
     * 取可空 Integer 包装列。
     *
     * @param rs 结果集
     * @param column 列名
     * @return Integer 或 null
     * @throws SQLException SQL 异常
     */
    protected static Integer nullableInt(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    /**
     * 取可空 String 列。
     *
     * @param rs 结果集
     * @param column 列名
     * @return String 或 null
     * @throws SQLException SQL 异常
     */
    protected static String nullableString(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        String value = rs.getString(column);
        return value == null ? null : value;
    }

    /**
     * 单行查询转 Optional。
     *
     * @param sql 查询语句
     * @param params 参数
     * @param mapper 行映射器
     * @param <T> 行类型
     * @return Optional 行
     */
    protected <T> Optional<T> queryOne(String sql, MapSqlParameterSource params, RowMapper<T> mapper) {
        return jdbc.query(sql, params, rs -> rs.next() ? Optional.of(mapper.mapRow(rs, 0)) : Optional.empty());
    }

    /**
     * 共用原查询的 FROM/WHERE，保证总数、筛选和 DATA 范围逐字一致。
     * 游标查询多读一条，仅以额外行判断是否还有下一页。
     *
     * @param sql 已包含全部过滤条件且尚无分页子句的查询
     * @param params 查询参数
     * @param limit 游标模式单页条数
     * @param request 页码请求，游标模式为空
     * @return 页码模式总数，游标模式为空
     */
    protected Long prepareWindow(StringBuilder sql, MapSqlParameterSource params, int limit,
            io.github.surezzzzzz.sdk.crm.server.core.model.CrmPageRequest request) {
        Long total = null;
        if (request == null) {
            // 内部完整集合查询可使用 Integer.MAX_VALUE，探测窗口使用长整数避免溢出。
            params.addValue(LIMIT_PARAMETER, (long) limit + 1);
        } else {
            total = jdbc.queryForObject(COUNT_PREFIX + sql.substring(sql.indexOf(FROM_TOKEN)),
                    params, Long.class);
            params.addValue(LIMIT_PARAMETER, request.getPageSize());
            params.addValue(OFFSET_PARAMETER, request.offset());
        }
        sql.append(WINDOW_SUFFIX);
        if (request != null) {
            sql.append(OFFSET_SUFFIX);
        }
        return total;
    }
}
