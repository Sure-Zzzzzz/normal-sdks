package io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.configuration;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.exception.KafkaOutboxManagementConfigurationException;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;
import java.util.Map;

/**
 * Management 资源选择配置。
 *
 * @author surezzzzzz
 */
public final class SimpleKafkaOutboxManagementConfiguration {
    private SimpleKafkaOutboxManagementConfiguration() {
    }

    /**
     * 选择并验证资源。
     */
    public static SimpleKafkaOutboxManagementResource selectResource(ListableBeanFactory beanFactory,
                                                                     SimpleKafkaOutboxManagementProperties properties) {
        DataSource dataSource = select(beanFactory, properties.getDataSourceBeanName(), DataSource.class, "DataSource");
        DataSourceTransactionManager transactionManager = select(beanFactory, properties.getTransactionManagerBeanName(),
                DataSourceTransactionManager.class, "DataSourceTransactionManager");
        if (transactionManager.getDataSource() != dataSource) {
            throw invalid("事务管理器与 DataSource 不匹配");
        }
        return new SimpleKafkaOutboxManagementResource(dataSource, transactionManager);
    }

    private static <T> T select(ListableBeanFactory beanFactory, String name, Class<T> type, String label) {
        if (hasText(name)) {
            if (!beanFactory.containsBean(name) || !beanFactory.isTypeMatch(name, type)) {
                throw invalid(label + " Bean 不存在或类型不符");
            }
            return beanFactory.getBean(name, type);
        }
        Map<String, T> beans = beanFactory.getBeansOfType(type);
        if (beans.size() != 1) {
            throw invalid(label + " 必须唯一");
        }
        return beans.values().iterator().next();
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static KafkaOutboxManagementConfigurationException invalid(String reason) {
        return new KafkaOutboxManagementConfigurationException(ErrorCode.CONFIGURATION_INVALID,
                String.format(ErrorMessage.CONFIGURATION_INVALID, reason));
    }
}
