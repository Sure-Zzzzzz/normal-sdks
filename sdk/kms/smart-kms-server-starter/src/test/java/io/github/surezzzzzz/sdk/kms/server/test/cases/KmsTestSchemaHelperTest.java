package io.github.surezzzzzz.sdk.kms.server.test.cases;

import io.github.surezzzzzz.sdk.kms.server.test.support.KmsTestSchemaHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * 验证结构重置在任何 SQL 执行前拒绝走查库及非专用连接。
 *
 * @author surezzzzzz
 */
@Slf4j
class KmsTestSchemaHelperTest {

    /**
     * 错误连接只允许读取库名并关闭，不得创建执行 SQL 的语句。
     */
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "smart_kms_test", "smart_kms_server", "smart_kms_server_prod",
            "smart_kms_server_test_backup.prod", "smart_kms_server_test;other"})
    void shouldRejectUnsafeCatalogBeforeExecutingSql(String catalog) throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getCatalog()).thenReturn(catalog);

        assertThrows(IllegalStateException.class, () -> KmsTestSchemaHelper.reset(source));

        verify(connection).getCatalog();
        verify(connection).close();
        verifyNoMoreInteractions(connection);
    }

    /**
     * 默认库与已有版本化可丢弃库均符合命名约束。
     */
    @ParameterizedTest
    @ValueSource(strings = {"smart_kms_server_test", "smart_kms_server_201_test_20261005",
            "smart_kms_server_test_20261006"})
    void shouldAcceptDedicatedTestCatalog(String catalog) throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.getCatalog()).thenReturn(catalog);

        assertDoesNotThrow(() -> KmsTestSchemaHelper.requireTestCatalog(connection));
    }
}
