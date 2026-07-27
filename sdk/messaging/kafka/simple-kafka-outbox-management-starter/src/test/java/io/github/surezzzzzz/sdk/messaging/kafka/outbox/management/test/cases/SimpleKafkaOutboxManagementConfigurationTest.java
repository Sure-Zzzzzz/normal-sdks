package io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.configuration.SimpleKafkaOutboxManagementConfiguration;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.configuration.SimpleKafkaOutboxManagementProperties;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.exception.KafkaOutboxManagementConfigurationException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Management 配置测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class SimpleKafkaOutboxManagementConfigurationTest {
    @Test
    void shouldRejectInvalidProperties() {
        SimpleKafkaOutboxManagementProperties properties = properties();
        properties.setTableName("bad-table-name");
        assertThrows(KafkaOutboxManagementConfigurationException.class, properties::init);
        properties = properties();
        properties.getUi().setBasePath("outbox");
        assertThrows(KafkaOutboxManagementConfigurationException.class, properties::init);
    }

    @Test
    void shouldSelectOnlyMatchingDataSourceAndTransactionManager() {
        DataSource dataSource = Mockito.mock(DataSource.class);
        DataSourceTransactionManager transactionManager = Mockito.mock(DataSourceTransactionManager.class);
        when(transactionManager.getDataSource()).thenReturn(dataSource);
        ListableBeanFactory beanFactory = Mockito.mock(ListableBeanFactory.class);
        when(beanFactory.getBeansOfType(DataSource.class)).thenReturn(Collections.singletonMap("dataSource", dataSource));
        when(beanFactory.getBeansOfType(DataSourceTransactionManager.class)).thenReturn(Collections.singletonMap("transactionManager", transactionManager));
        assertDoesNotThrow(() -> SimpleKafkaOutboxManagementConfiguration.selectResource(beanFactory, properties()));
        log.info("Management 资源选择只接受同一 DataSource 的事务管理器");
    }

    @Test
    void shouldRejectAmbiguousOrMismatchedResources() {
        ListableBeanFactory emptyFactory = Mockito.mock(ListableBeanFactory.class);
        when(emptyFactory.getBeansOfType(DataSource.class)).thenReturn(Collections.emptyMap());
        assertThrows(KafkaOutboxManagementConfigurationException.class,
                () -> SimpleKafkaOutboxManagementConfiguration.selectResource(emptyFactory, properties()));

        DataSource dataSource = Mockito.mock(DataSource.class);
        DataSource otherDataSource = Mockito.mock(DataSource.class);
        DataSourceTransactionManager transactionManager = Mockito.mock(DataSourceTransactionManager.class);
        when(transactionManager.getDataSource()).thenReturn(otherDataSource);
        ListableBeanFactory mismatchedFactory = Mockito.mock(ListableBeanFactory.class);
        when(mismatchedFactory.getBeansOfType(DataSource.class)).thenReturn(Collections.singletonMap("dataSource", dataSource));
        when(mismatchedFactory.getBeansOfType(DataSourceTransactionManager.class))
                .thenReturn(Collections.singletonMap("transactionManager", transactionManager));
        assertThrows(KafkaOutboxManagementConfigurationException.class,
                () -> SimpleKafkaOutboxManagementConfiguration.selectResource(mismatchedFactory, properties()));
    }

    private SimpleKafkaOutboxManagementProperties properties() {
        SimpleKafkaOutboxManagementProperties properties = new SimpleKafkaOutboxManagementProperties();
        properties.setEnable(true);
        properties.getAdmin().setUsername("test");
        properties.getAdmin().setPassword("test");
        return properties;
    }
}
