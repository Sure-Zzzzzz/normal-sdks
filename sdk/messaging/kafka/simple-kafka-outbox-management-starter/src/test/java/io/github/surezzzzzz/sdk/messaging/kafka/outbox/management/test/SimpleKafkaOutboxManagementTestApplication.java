package io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Simple Kafka Outbox Management 测试应用。
 *
 * @author surezzzzzz
 */
@SpringBootApplication
public class SimpleKafkaOutboxManagementTestApplication {
    /**
     * 启动测试应用。
     */
    public static void main(String[] args) {
        SpringApplication.run(SimpleKafkaOutboxManagementTestApplication.class, args);
    }
}
