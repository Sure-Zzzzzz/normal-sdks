package io.github.surezzzzzz.sdk.mail.configuration;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * @author: Sure.
 * @description
 * @Date: 2025/2/14 16:59
 */
@Getter
@Setter
@NoArgsConstructor
@MailComponent
@ConfigurationProperties("io.github.surezzzzzz.sdk.mail.send.send-cloud")
@ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.mail.send.send-cloud", name = "api-user")
public class SendCloudSMTPMailSenderProperties {
    private Map<String, String> properties = new HashMap<>();
    private String apiUser;
    private String apiKey;
    private String from;
}