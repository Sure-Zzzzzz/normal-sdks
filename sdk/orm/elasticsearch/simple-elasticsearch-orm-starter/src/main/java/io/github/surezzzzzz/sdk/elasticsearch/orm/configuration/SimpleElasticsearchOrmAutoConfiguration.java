package io.github.surezzzzzz.sdk.elasticsearch.orm.configuration;

import io.github.surezzzzzz.sdk.elasticsearch.orm.SimpleElasticsearchOrmPackage;
import io.github.surezzzzzz.sdk.elasticsearch.orm.annotation.SimpleElasticsearchOrmComponent;
import io.github.surezzzzzz.sdk.elasticsearch.route.configuration.SimpleElasticsearchRouteConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;

import javax.annotation.PostConstruct;

/**
 * Simple Elasticsearch ORM Auto Configuration
 *
 * @author surezzzzzz
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.elasticsearch.orm", name = "enable", havingValue = "true")
@AutoConfigureAfter(SimpleElasticsearchRouteConfiguration.class)
@ComponentScan(
        basePackageClasses = SimpleElasticsearchOrmPackage.class,
        includeFilters = @ComponentScan.Filter(SimpleElasticsearchOrmComponent.class)
)
public class SimpleElasticsearchOrmAutoConfiguration {

    @PostConstruct
    public void init() {
        log.info("===== SimpleElasticsearchOrm 自动配置加载成功 =====");
    }

    /**
     * 从 ElasticsearchRestTemplate 提取 RestHighLevelClient 并注册为 Bean
     * 供 ORM 组件使用（通过反射获取内部的 final client 字段）
     */
    @Bean
    @ConditionalOnMissingBean
    public RestHighLevelClient restHighLevelClient(ElasticsearchRestTemplate template) {
        log.info("Extracting RestHighLevelClient from ElasticsearchRestTemplate via reflection");

        try {
            // ElasticsearchRestTemplate 内部有一个 RestHighLevelClient client 字段
            java.lang.reflect.Field field = org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate.class
                    .getDeclaredField("client");
            field.setAccessible(true);
            RestHighLevelClient client = (RestHighLevelClient) field.get(template);

            if (client == null) {
                throw new IllegalStateException("RestHighLevelClient is null in ElasticsearchRestTemplate");
            }

            log.info("✓ RestHighLevelClient extracted successfully");
            return client;
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(
                    "Failed to find 'client' field in ElasticsearchRestTemplate. " +
                            "This may be due to Spring Data Elasticsearch version mismatch.", e);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to access 'client' field in ElasticsearchRestTemplate", e);
        } catch (ClassCastException e) {
            throw new IllegalStateException(
                    "The 'client' field is not a RestHighLevelClient instance", e);
        }
    }

}
