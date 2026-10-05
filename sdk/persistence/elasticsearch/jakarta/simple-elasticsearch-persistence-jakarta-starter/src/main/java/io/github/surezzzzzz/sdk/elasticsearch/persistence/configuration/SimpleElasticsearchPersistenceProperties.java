package io.github.surezzzzzz.sdk.elasticsearch.persistence.configuration;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.constant.SimpleElasticsearchPersistenceConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Jakarta 业务写入的唯一配置入口。
 */
@Data
@ConfigurationProperties(SimpleElasticsearchPersistenceConstant.CONFIG_PREFIX)
public class SimpleElasticsearchPersistenceProperties {
    /**
     * 显式启用；缺少 Route 时启动失败。
     */
    private boolean enable;
    /**
     * 客户端线程池，不等同于 ES 服务端 task。
     */
    private Async async = new Async();
    /**
     * 按查询修改的危险全量范围保护。
     */
    private ByQuery byQuery = new ByQuery();

    /**
     * 客户端异步执行配置，队列满时拒绝并通过 Future 返回失败。
     */
    @Data
    public static class Async {
        private int coreSize = SimpleElasticsearchPersistenceConstant.DEFAULT_ASYNC_EXECUTOR_CORE_SIZE;
        private int maxSize = SimpleElasticsearchPersistenceConstant.DEFAULT_ASYNC_EXECUTOR_MAX_SIZE;
        private int queueCapacity = SimpleElasticsearchPersistenceConstant.DEFAULT_ASYNC_EXECUTOR_QUEUE_CAPACITY;
    }

    /**
     * 全量修改必须由调用方显式放开，并限定为具体索引。
     */
    @Data
    public static class ByQuery {
        private boolean allowMatchAll;
    }
}
