package io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.configuration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

/**
 * Management 数据库资源组合。
 *
 * @author surezzzzzz
 */
@Getter
@RequiredArgsConstructor
public class SimpleKafkaOutboxManagementResource {
    private final DataSource dataSource;
    private final DataSourceTransactionManager transactionManager;
}
