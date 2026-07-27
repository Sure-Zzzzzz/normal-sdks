package io.github.surezzzzzz.sdk.kms.server.repository;

import io.github.surezzzzzz.sdk.kms.core.repository.KmsClock;
import io.github.surezzzzzz.sdk.kms.server.constant.SmartKmsServerConstant;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * 基于当前 JDBC 事务连接的 KMS 权威时间。
 *
 * @author surezzzzzz
 */
public class JdbcKmsClock implements KmsClock {

    /**
     * 执行数据库时间查询的 JDBC 模板。
     */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建数据库 UTC 时间端口实现。
     *
     * @param jdbcTemplate 执行数据库时间查询的 JDBC 模板
     */
    public JdbcKmsClock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 从当前 JDBC 事务连接获取 UTC 毫秒时间。
     *
     * @return 数据库返回的当前 UTC 时间点
     */
    @Override
    public Instant now() {
        Timestamp timestamp = jdbcTemplate.queryForObject(SmartKmsServerConstant.SQL_SELECT_UTC_TIMESTAMP,
                Timestamp.class);
        return timestamp.toInstant();
    }
}
