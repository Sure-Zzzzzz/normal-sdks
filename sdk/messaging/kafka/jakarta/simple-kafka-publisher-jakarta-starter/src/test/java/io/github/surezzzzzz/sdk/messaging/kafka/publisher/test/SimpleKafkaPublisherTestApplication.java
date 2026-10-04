package io.github.surezzzzzz.sdk.messaging.kafka.publisher.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Simple Kafka Publisher 测试应用
 *
 * @author surezzzzzz
 */
@SpringBootApplication
public class SimpleKafkaPublisherTestApplication {

    /**
     * 启动测试应用
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(SimpleKafkaPublisherTestApplication.class, args);
    }
}
