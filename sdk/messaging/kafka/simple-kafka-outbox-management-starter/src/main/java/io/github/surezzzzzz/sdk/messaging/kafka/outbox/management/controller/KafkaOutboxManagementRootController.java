package io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.controller;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.annotation.SimpleKafkaOutboxManagementComponent;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.configuration.SimpleKafkaOutboxManagementProperties;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.constant.SimpleKafkaOutboxManagementConstant;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Outbox Management 根路径入口。
 *
 * @author surezzzzzz
 */
@Controller
@RequiredArgsConstructor
@SimpleKafkaOutboxManagementComponent
@ConditionalOnProperty(prefix = SimpleKafkaOutboxManagementConstant.CONFIG_PREFIX,
        name = SimpleKafkaOutboxManagementConstant.CONFIG_PROPERTY_UI_REDIRECT_ROOT, havingValue = "true", matchIfMissing = true)
public class KafkaOutboxManagementRootController {
    private final SimpleKafkaOutboxManagementProperties properties;

    /**
     * 跳转到配置的 Management 页面根路径。
     */
    @GetMapping("/")
    public String redirectToManagement() {
        return "redirect:" + properties.getUi().getBasePath() + "/";
    }
}
