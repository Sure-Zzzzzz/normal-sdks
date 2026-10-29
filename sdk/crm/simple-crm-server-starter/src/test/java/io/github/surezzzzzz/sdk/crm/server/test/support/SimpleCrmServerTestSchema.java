package io.github.surezzzzzz.sdk.crm.server.test.support;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * 受控 schema 执行入口：整个测试 JVM 只执行一次 docs/schema.sql（含 DROP，仅测试库）。
 *
 * <p>通过静态标志防重入；不在任何 Spring 生命周期回调中执行。</p>
 *
 * @author surezzzzzz
 */
public final class SimpleCrmServerTestSchema {

    private static boolean executed = false;

    private SimpleCrmServerTestSchema() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    /**
     * 执行一次基线建表（幂等：重复调用直接返回）。
     *
     * @param dataSource 测试数据源
     * @throws Exception SQL 执行失败
     */
    public static synchronized void ensureExecuted(DataSource dataSource) throws Exception {
        if (executed) {
            return;
        }
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("schema-test.sql", SimpleCrmServerTestSchema.class.getClassLoader()));
        }
        executed = true;
    }
}
