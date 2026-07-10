package io.github.surezzzzzz.sdk.mail.factory;

import io.github.surezzzzzz.sdk.mail.configuration.MailProperties;
import io.github.surezzzzzz.sdk.mail.support.MailPropertiesHelper;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * JavaMailSender 工厂
 *
 * @author surezzzzzz
 */
public class JavaMailSenderFactory {

    public JavaMailSenderImpl create(MailProperties.Normal properties) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(properties.getHost());
        sender.setPort(properties.getPort());
        sender.setProtocol(properties.getProtocol());
        sender.setUsername(properties.getUsername());
        sender.setPassword(properties.getPassword());
        sender.setDefaultEncoding(properties.getDefaultEncoding());
        sender.setJavaMailProperties(MailPropertiesHelper.toProperties(properties.getProperties()));
        return sender;
    }
}
