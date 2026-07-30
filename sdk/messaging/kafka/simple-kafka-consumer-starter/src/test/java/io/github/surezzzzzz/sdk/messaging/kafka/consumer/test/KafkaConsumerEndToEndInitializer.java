package io.github.surezzzzzz.sdk.messaging.kafka.consumer.test;

import io.github.surezzzzzz.sdk.messaging.kafka.consumer.test.support.KafkaConsumerEndToEndHelper;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 在 Consumer 容器启动前创建固定端到端 topic。
 *
 * @author surezzzzzz
 */
public class KafkaConsumerEndToEndInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        KafkaConsumerEndToEndHelper.createRequiredTopics();
    }
}
