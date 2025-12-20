package io.github.surezzzzzz.sdk.elasticsearch.search.configuration;

import io.github.surezzzzzz.sdk.elasticsearch.route.configuration.SimpleElasticsearchRouteConfiguration;
import io.github.surezzzzzz.sdk.elasticsearch.search.SimpleElasticsearchSearchPackage;
import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * Simple Elasticsearch Search Auto Configuration
 *
 * <p>依赖 simple-elasticsearch-route-starter 提供的 SimpleElasticsearchRouteRegistry，
 * 通过路由解析获取版本自适应的 RestHighLevelClient</p>
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
        log.info("使用 simple-elasticsearch-route-starter 提供的版本自适应客户端");
    }
}
