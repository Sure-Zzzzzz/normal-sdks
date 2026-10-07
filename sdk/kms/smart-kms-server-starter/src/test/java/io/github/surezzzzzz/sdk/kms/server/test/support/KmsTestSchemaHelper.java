package io.github.surezzzzzz.sdk.kms.server.test.support;

import lombok.experimental.UtilityClass;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.regex.Pattern;

/**
 * 将破坏性结构重置限制在模块专用的可丢弃测试库。
 *
 * @author surezzzzzz
 */
@UtilityClass
public class KmsTestSchemaHelper {

    private static final Pattern TEST_CATALOG = Pattern.compile("smart_kms_server_(?:[0-9]+_)?test(?:_[a-z0-9]+)*");
    private static final String SCHEMA_PATH = "docs/schema.sql";
    private static final String UNSAFE_CATALOG = "拒绝重置非 KMS Server 专用测试库";
    private static final String RESET_FAILED = "KMS Server 测试库结构重置失败";

    /**
     * 在同一连接上核验实际库名并重置结构，避免路由切换绕过核验。
     *
     * @param dataSource 测试数据源
     */
    public static void reset(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            requireTestCatalog(connection);
            new ResourceDatabasePopulator(new FileSystemResource(SCHEMA_PATH)).populate(connection);
            if (!connection.getAutoCommit()) {
                connection.commit();
            }
        } catch (SQLException failure) {
            throw new IllegalStateException(RESET_FAILED, failure);
        }
    }

    /**
     * 只接受模块测试库命名空间，走查库或未选择数据库的连接均在执行 SQL 前拒绝。
     *
     * @param connection 待执行结构重置的连接
     * @throws SQLException 无法读取实际库名
     */
    public static void requireTestCatalog(Connection connection) throws SQLException {
        String catalog = connection.getCatalog();
        if (catalog == null || !TEST_CATALOG.matcher(catalog).matches()) {
            throw new IllegalStateException(UNSAFE_CATALOG);
        }
    }
}
