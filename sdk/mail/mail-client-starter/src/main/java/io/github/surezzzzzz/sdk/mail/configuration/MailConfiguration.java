package io.github.surezzzzzz.sdk.mail.configuration;

import io.github.surezzzzzz.sdk.mail.MailPackage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import javax.mail.Authenticator;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import java.util.Map;
import java.util.Properties;

@Configuration
@ComponentScan(
        basePackageClasses = MailPackage.class,
        includeFilters = @ComponentScan.Filter(MailComponent.class)
)
public class MailConfiguration {

    @Autowired(required = false)
    private NormalMailSenderProperties normalMailSenderProperties;
    @Autowired(required = false)
    private SendCloudSMTPMailSenderProperties sendCloudSMTPMailSenderProperties;
    @Autowired(required = false)
    private MailReaderProperties mailReaderProperties;

    @Bean
    @ConditionalOnBean(NormalMailSenderProperties.class)
    public JavaMailSender javaMailSender() {
        JavaMailSenderImpl javaMailSender = new JavaMailSenderImpl();
        javaMailSender.setHost(normalMailSenderProperties.getHost());
        javaMailSender.setPort(normalMailSenderProperties.getPort());
        javaMailSender.setUsername(normalMailSenderProperties.getUsername());
        javaMailSender.setPassword(normalMailSenderProperties.getPassword());
        javaMailSender.setProtocol(normalMailSenderProperties.getProtocol());
        javaMailSender.setDefaultEncoding(normalMailSenderProperties.getDefaultEncoding());
        javaMailSender.setJavaMailProperties(mapToProperties(normalMailSenderProperties.getProperties()));
        return javaMailSender;
    }

    @Bean
    @ConditionalOnBean(MailReaderProperties.class)
    public Session readMailSession() {
        return Session.getInstance(mapToProperties(mailReaderProperties.getProperties()));
    }

    @Bean
    @ConditionalOnBean(SendCloudSMTPMailSenderProperties.class)
    public Session sendMailSession() {
        Properties properties = mapToProperties(sendCloudSMTPMailSenderProperties.getProperties());
        properties.setProperty("mail.transport.protocol", "smtp");
        properties.put("mail.smtp.connectiontimeout", 180);
        properties.put("mail.smtp.timeout", 600);
        return Session.getInstance(properties, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(
                        sendCloudSMTPMailSenderProperties.getApiUser(),
                        sendCloudSMTPMailSenderProperties.getApiKey()
                );
            }
        });
    }

    // 将Map转换为Properties的方法
    private Properties mapToProperties(Map<String, String> map) {
        Properties properties = new Properties();
        if (map != null) {
            for (Map.Entry<String, String> entry : map.entrySet()) {
                properties.setProperty(entry.getKey(), entry.getValue());
            }
        }
        return properties;
    }
}
