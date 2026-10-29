package io.github.surezzzzzz.sdk.crm.server.test.cases;

import io.github.surezzzzzz.sdk.crm.server.test.SimpleCrmServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 核验真实数据源、JDBC 模板和事务管理器是否按宿主接管形态统一装配。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleCrmServerTestApplication.class)
class SimpleCrmServerDataSourceTopologyMySqlIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private Environment environment;

    /**
     * 配置启用必须对应真实 Route Bean，并确认 JDBC 与事务使用同一 MySQL 数据源。
     *
     * @throws Exception 数据源连接失败
     */
    @Test
    void effectiveDataSourceAndTransactionMatchConfiguredTopology() throws Exception {
        boolean route = environment.getProperty("io.github.surezzzzzz.sdk.mysql.route.enable",
                Boolean.class, false);
        log.info("CRM 测试数据源形态：route={}，dataSourceType={}", route,
                dataSource.getClass().getSimpleName());
        assertSame(dataSource, jdbc.getDataSource());
        assertTrue(transactionManager instanceof DataSourceTransactionManager);
        assertSame(dataSource, ((DataSourceTransactionManager) transactionManager).getDataSource());
        if (route) {
            assertSame(dataSource, context.getBean("mysqlRouteRoutingDataSource"));
        } else {
            assertFalse(context.containsBean("mysqlRouteRoutingDataSource"));
        }
        try (Connection connection = dataSource.getConnection()) {
            assertEquals("MySQL", connection.getMetaData().getDatabaseProductName());
            assertEquals("simple_crm_server_test", connection.getCatalog(),
                    "业务集成验收仅连接专用测试库");
        }
    }
}
