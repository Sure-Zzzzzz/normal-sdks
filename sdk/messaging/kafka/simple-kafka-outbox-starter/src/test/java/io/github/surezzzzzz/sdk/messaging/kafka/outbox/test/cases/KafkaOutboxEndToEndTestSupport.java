package io.github.surezzzzzz.sdk.messaging.kafka.outbox.test.cases;

import io.github.surezzzzzz.sdk.kafka.route.configuration.SimpleKafkaRouteProperties;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.test.support.KafkaOutboxEndToEndHelper;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * Kafka Outbox 端到端测试 Route 环境支持
 *
 * @author surezzzzzz
 */
public abstract class KafkaOutboxEndToEndTestSupport {

    @Autowired
    private SimpleKafkaRouteProperties routeProperties;

    protected void createSharedTopic(String topic) {
        KafkaOutboxEndToEndHelper.createSharedTopic(topic,
                bootstrapServers(KafkaOutboxEndToEndHelper.DATASOURCE_V110),
                bootstrapServers(KafkaOutboxEndToEndHelper.DATASOURCE_V28),
                bootstrapServers(KafkaOutboxEndToEndHelper.DATASOURCE_V37),
                bootstrapServers(KafkaOutboxEndToEndHelper.DATASOURCE_CLUSTER));
    }

    protected String bootstrapServers(String datasourceKey) {
        SimpleKafkaRouteProperties.DataSourceConfig source = routeProperties.getSources().get(datasourceKey);
        if (source == null) {
            throw new IllegalStateException("缺少 Kafka Route datasource 配置: " + datasourceKey);
        }
        List<String> servers = source.getBootstrapServers();
        if (servers == null || servers.isEmpty()) {
            throw new IllegalStateException("Kafka Route datasource 未配置 bootstrap servers: " + datasourceKey);
        }
        return String.join(",", servers);
    }
}
