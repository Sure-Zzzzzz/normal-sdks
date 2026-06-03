package io.github.surezzzzzz.sdk.metrics.elasticsearch.search.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 测试应用入口
 *
 * @author surezzzzzz
 */
@SpringBootApplication(scanBasePackages = {
        "io.github.surezzzzzz.sdk.metrics.elasticsearch.search.test",
        "io.github.surezzzzzz.sdk.elasticsearch.search",
        "io.github.surezzzzzz.sdk.elasticsearch.route",
        "io.github.surezzzzzz.sdk.metrics.elasticsearch.search",
        "io.github.surezzzzzz.sdk.naturallanguage.parser"
})
public class SimpleElasticsearchSearchMetricsTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimpleElasticsearchSearchMetricsTestApplication.class, args);
    }
}
