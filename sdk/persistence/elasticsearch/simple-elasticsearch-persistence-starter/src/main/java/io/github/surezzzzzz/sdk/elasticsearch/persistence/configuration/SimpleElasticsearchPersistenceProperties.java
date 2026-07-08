package io.github.surezzzzzz.sdk.elasticsearch.persistence.configuration;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.constant.SimpleElasticsearchPersistenceConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Simple Elasticsearch Persistence Properties
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(SimpleElasticsearchPersistenceConstant.CONFIG_PREFIX)
public class SimpleElasticsearchPersistenceProperties {

    private boolean enable = false;
    private Async async = new Async();

    @Data
    public static class Async {
        private int coreSize = SimpleElasticsearchPersistenceConstant.DEFAULT_ASYNC_EXECUTOR_CORE_SIZE;
        private int maxSize = SimpleElasticsearchPersistenceConstant.DEFAULT_ASYNC_EXECUTOR_MAX_SIZE;
        private int queueCapacity = SimpleElasticsearchPersistenceConstant.DEFAULT_ASYNC_EXECUTOR_QUEUE_CAPACITY;
    }
}
