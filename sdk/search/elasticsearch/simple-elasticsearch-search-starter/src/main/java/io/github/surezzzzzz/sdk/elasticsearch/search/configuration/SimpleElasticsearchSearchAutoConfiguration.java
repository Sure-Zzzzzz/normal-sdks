package io.github.surezzzzzz.sdk.elasticsearch.search.configuration;

import io.github.surezzzzzz.sdk.elasticsearch.search.SimpleElasticsearchSearchPackage;
import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * Simple Elasticsearch Search Auto Configuration
 *
 * <p>依赖 simple-elasticsearch-route-starter 提供的 SimpleElasticsearchRouteRegistry，
 * 通过路由解析获取版本自适应的 RestHighLevelClient。</p>
 *
 * <p>ElasticsearchRestTemplate bean 由 route-starter 负责创建，
 * route-starter 1.0.7+ 已内置 Spring Boot 2.4.x CGLIB 兼容性降级逻辑，
 * search-starter 无需干预。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.elasticsearch.search", name = "enable", havingValue = "true")
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
