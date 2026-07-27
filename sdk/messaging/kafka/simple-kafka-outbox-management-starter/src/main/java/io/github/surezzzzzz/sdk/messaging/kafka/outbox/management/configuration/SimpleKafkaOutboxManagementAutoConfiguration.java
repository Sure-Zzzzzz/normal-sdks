package io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.configuration;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.SimpleKafkaOutboxManagementPackage;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.annotation.SimpleKafkaOutboxManagementComponent;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.constant.SimpleKafkaOutboxManagementConstant;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.repository.JdbcKafkaOutboxManagementRepository;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.repository.KafkaOutboxManagementRepository;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.service.DefaultKafkaOutboxManagementService;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.service.KafkaOutboxManagementService;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.DispatcherServlet;
import org.thymeleaf.spring5.SpringTemplateEngine;

/**
 * Simple Kafka Outbox Management 自动配置。
 *
 * @author surezzzzzz
 */
@Configuration
@AutoConfigureBefore(UserDetailsServiceAutoConfiguration.class)
@EnableWebSecurity
@EnableConfigurationProperties(SimpleKafkaOutboxManagementProperties.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({NamedParameterJdbcTemplate.class, DispatcherServlet.class, SpringTemplateEngine.class,
        PasswordEncoder.class})
@ConditionalOnProperty(prefix = SimpleKafkaOutboxManagementConstant.CONFIG_PREFIX,
        name = SimpleKafkaOutboxManagementConstant.CONFIG_PROPERTY_ENABLE, havingValue = "true")
@Import(SimpleKafkaOutboxManagementAutoConfiguration.UiConfiguration.class)
public class SimpleKafkaOutboxManagementAutoConfiguration {
    /**
     * UI 启用时注册 Management Web 能力。
     */
    @Configuration
    @ConditionalOnProperty(prefix = SimpleKafkaOutboxManagementConstant.CONFIG_PREFIX,
            name = SimpleKafkaOutboxManagementConstant.CONFIG_PROPERTY_UI_ENABLE, havingValue = "true", matchIfMissing = true)
    @ComponentScan(basePackageClasses = SimpleKafkaOutboxManagementPackage.class,
            includeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = SimpleKafkaOutboxManagementComponent.class),
            useDefaultFilters = false)
    @Import({SimpleKafkaOutboxManagementSecurityConfiguration.class, SimpleKafkaOutboxManagementWebMvcConfiguration.class})
    static class UiConfiguration {
        /**
         * 创建 management 数据库资源。
         */
        @Bean
        public SimpleKafkaOutboxManagementResource simpleKafkaOutboxManagementResource(ListableBeanFactory beanFactory,
                                                                                       SimpleKafkaOutboxManagementProperties properties) {
            return SimpleKafkaOutboxManagementConfiguration.selectResource(beanFactory, properties);
        }

        /**
         * 创建 management JDBC 模板。
         */
        @Bean(name = SimpleKafkaOutboxManagementConstant.BEAN_NAMED_JDBC_TEMPLATE)
        public NamedParameterJdbcTemplate simpleKafkaOutboxManagementNamedParameterJdbcTemplate(SimpleKafkaOutboxManagementResource resource) {
            return new NamedParameterJdbcTemplate(resource.getDataSource());
        }

        /**
         * 创建 management 事务模板。
         */
        @Bean(name = SimpleKafkaOutboxManagementConstant.BEAN_TRANSACTION_TEMPLATE)
        public TransactionTemplate simpleKafkaOutboxManagementTransactionTemplate(SimpleKafkaOutboxManagementResource resource) {
            return new TransactionTemplate(resource.getTransactionManager());
        }

        /**
         * 创建默认密码编码器。
         */
        @Bean
        @ConditionalOnMissingBean(PasswordEncoder.class)
        public PasswordEncoder simpleKafkaOutboxManagementPasswordEncoder() {
            return PasswordEncoderFactories.createDelegatingPasswordEncoder();
        }

        /**
         * 创建默认 Repository。
         */
        @Bean
        @ConditionalOnMissingBean(KafkaOutboxManagementRepository.class)
        public KafkaOutboxManagementRepository kafkaOutboxManagementRepository(
                @org.springframework.beans.factory.annotation.Qualifier(SimpleKafkaOutboxManagementConstant.BEAN_NAMED_JDBC_TEMPLATE) NamedParameterJdbcTemplate jdbcTemplate,
                @org.springframework.beans.factory.annotation.Qualifier(SimpleKafkaOutboxManagementConstant.BEAN_TRANSACTION_TEMPLATE) TransactionTemplate transactionTemplate,
                SimpleKafkaOutboxManagementProperties properties) {
            return new JdbcKafkaOutboxManagementRepository(jdbcTemplate, transactionTemplate, properties.getTableName());
        }

        /**
         * 创建默认 Service。
         */
        @Bean
        @ConditionalOnMissingBean(KafkaOutboxManagementService.class)
        public KafkaOutboxManagementService kafkaOutboxManagementService(KafkaOutboxManagementRepository repository,
                                                                         SimpleKafkaOutboxManagementProperties properties) {
            return new DefaultKafkaOutboxManagementService(repository, properties);
        }
    }
}
