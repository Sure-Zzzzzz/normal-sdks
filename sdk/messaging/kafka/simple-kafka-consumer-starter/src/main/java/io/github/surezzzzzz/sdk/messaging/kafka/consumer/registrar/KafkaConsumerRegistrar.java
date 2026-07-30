package io.github.surezzzzzz.sdk.messaging.kafka.consumer.registrar;

import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.SimpleKafkaConsumerConstant;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.exception.KafkaConsumerConfigurationException;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.model.ConsumerRegistration;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.support.KafkaConsumerStringHelper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 消费注册中心，收集注解扫描与编程式注册的 ConsumerRegistration，供容器管理器消费。
 * 重复注册（同 topic + 解析后 groupId）的校验在容器管理器完成 route 解析后执行。
 *
 * @author surezzzzzz
 */
@Slf4j
public class KafkaConsumerRegistrar {

    private final List<ConsumerRegistration> registrations = new ArrayList<>();

    /**
     * 注册一个消费入口
     *
     * @param registration 注册项
     */
    public synchronized void register(ConsumerRegistration registration) {
        if (registration == null) {
            throw configInvalid(SimpleKafkaConsumerConstant.REASON_REGISTRATION_MISSING);
        }
        if (!KafkaConsumerStringHelper.hasText(registration.getTopic())) {
            throw configInvalid(SimpleKafkaConsumerConstant.REASON_TOPIC_EMPTY);
        }
        if (registration.getHandler() == null) {
            throw configInvalid(SimpleKafkaConsumerConstant.REASON_HANDLER_MISSING);
        }
        registrations.add(registration);
        log.info("注册消费入口：topic=[{}]，datasource=[{}]，groupId=[{}]，id=[{}]",
                registration.getTopic(),
                KafkaConsumerStringHelper.safeDisplay(registration.getDatasource()),
                KafkaConsumerStringHelper.safeDisplay(registration.getGroupId()),
                KafkaConsumerStringHelper.safeDisplay(registration.getId()));
    }

    /**
     * 获取全部注册项
     *
     * @return 不可变注册项列表
     */
    public synchronized List<ConsumerRegistration> getRegistrations() {
        return Collections.unmodifiableList(new ArrayList<>(registrations));
    }

    private KafkaConsumerConfigurationException configInvalid(String reason) {
        return new KafkaConsumerConfigurationException(ErrorCode.CONFIG_INVALID,
                String.format(ErrorMessage.CONFIG_INVALID, reason));
    }
}
