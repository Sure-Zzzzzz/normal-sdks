package io.github.surezzzzzz.sdk.elasticsearch.search.configuration;

import io.github.surezzzzzz.sdk.elasticsearch.search.SimpleElasticsearchSearchPackage;
import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
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
 * Simple Elasticsearch Search Auto Configuration
 *
 * @author surezzzzzz
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.elasticsearch.search", name = "enable", havingValue = "true")
@AutoConfigureAfter(SimpleElasticsearchRouteConfiguration.class)
@ComponentScan(
        basePackageClasses = SimpleElasticsearchSearchPackage.class,
        includeFilters = @ComponentScan.Filter(SimpleElasticsearchSearchComponent.class)
)
public class SimpleElasticsearchSearchAutoConfiguration {

    @PostConstruct
    public void init() {
        log.info("===== SimpleElasticsearchSearch 自动配置加载成功 =====");
    }

    /**
     * 从 ElasticsearchRestTemplate 提取 RestHighLevelClient 并注册为 Bean
     * 供 Search 组件使用（通过反射获取内部的 final client 字段）
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
