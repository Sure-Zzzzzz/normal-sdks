package io.github.surezzzzzz.sdk.kafka.route.test.factory;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.kafka.core.ConsumerFactory;

/**
 * 测试用可记录 ConsumerFactory
 *
 * @author surezzzzzz
 */
public class RecordingConsumerFactory implements ConsumerFactory<Object, Object>, DisposableBean {

    private final String datasourceKey;
    private int createConsumerCount;
    private boolean destroyed;

    public RecordingConsumerFactory(String datasourceKey) {
        this.datasourceKey = datasourceKey;
    }

    @Override
    public Consumer<Object, Object> createConsumer(String groupId, String clientIdPrefix, String clientIdSuffix) {
        createConsumerCount++;
        return new MockConsumer<>(OffsetResetStrategy.EARLIEST);
    }

    @Override
    public boolean isAutoCommit() {
        return false;
    }

    @Override
    public void destroy() {
        this.destroyed = true;
    }

    public String getDatasourceKey() {
        return datasourceKey;
    }

    public int getCreateConsumerCount() {
        return createConsumerCount;
    }

    public boolean isDestroyed() {
        return destroyed;
    }
}
