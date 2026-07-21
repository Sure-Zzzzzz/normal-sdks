package io.github.surezzzzzz.sdk.messaging.kafka.outbox.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Simple Kafka Outbox 测试应用
 *
 * @author surezzzzzz
 */
@SpringBootApplication
public class SimpleKafkaOutboxTestApplication {

    /**
     * 启动测试应用
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(SimpleKafkaOutboxTestApplication.class, args);
    }
}
