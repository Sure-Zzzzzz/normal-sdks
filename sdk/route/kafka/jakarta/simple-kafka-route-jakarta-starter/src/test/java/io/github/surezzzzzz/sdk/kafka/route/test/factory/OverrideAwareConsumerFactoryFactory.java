package io.github.surezzzzzz.sdk.kafka.route.test.factory;

import io.github.surezzzzzz.sdk.kafka.route.configuration.SimpleKafkaRouteProperties;
import io.github.surezzzzzz.sdk.kafka.route.factory.KafkaConsumerFactoryFactory;
import io.github.surezzzzzz.sdk.kafka.route.model.KafkaConsumerFactoryOverride;
import org.springframework.kafka.core.ConsumerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * 支持派生 factory 的测试用 KafkaConsumerFactoryFactory
 *
 * @author surezzzzzz
 */
public class OverrideAwareConsumerFactoryFactory implements KafkaConsumerFactoryFactory {

    private final List<RecordingConsumerFactory> baseFactories = new ArrayList<>();
    private final List<RecordingConsumerFactory> derivedFactories = new ArrayList<>();
    private CountDownLatch derivedCreateEntered;
    private CountDownLatch derivedCreateRelease;
    private Runnable derivedFactoryCreatedCallback;
    private boolean returnNullDerivedFactory;

    @Override
    public ConsumerFactory<Object, Object> create(String datasourceKey, SimpleKafkaRouteProperties.DataSourceConfig config) {
        RecordingConsumerFactory factory = new RecordingConsumerFactory(datasourceKey);
        baseFactories.add(factory);
        return factory;
    }

    @Override
    public ConsumerFactory<Object, Object> create(String datasourceKey,
                                                  SimpleKafkaRouteProperties.DataSourceConfig config,
                                                  KafkaConsumerFactoryOverride override) {
        awaitDerivedCreateRelease();
        if (returnNullDerivedFactory) {
            return null;
        }
        RecordingConsumerFactory factory = new RecordingConsumerFactory(datasourceKey);
        derivedFactories.add(factory);
        if (derivedFactoryCreatedCallback != null) {
            derivedFactoryCreatedCallback.run();
        }
        return factory;
    }

    /**
     * 设置派生 factory 创建完成后的回调
     *
     * @param derivedFactoryCreatedCallback 创建完成后的回调
     */
    public void setDerivedFactoryCreatedCallback(Runnable derivedFactoryCreatedCallback) {
        this.derivedFactoryCreatedCallback = derivedFactoryCreatedCallback;
    }

    /**
     * 设置派生 factory 创建过程的阻塞同步点
     *
     * @param derivedCreateEntered 已进入创建阶段的同步点
     * @param derivedCreateRelease 允许继续创建的同步点
     */
    public void setDerivedCreateLatches(CountDownLatch derivedCreateEntered,
                                        CountDownLatch derivedCreateRelease) {
        this.derivedCreateEntered = derivedCreateEntered;
        this.derivedCreateRelease = derivedCreateRelease;
    }

    /**
     * 设置派生 factory 是否返回空值
     *
     * @param returnNullDerivedFactory 是否返回空值
     */
    public void setReturnNullDerivedFactory(boolean returnNullDerivedFactory) {
        this.returnNullDerivedFactory = returnNullDerivedFactory;
    }

    /**
     * 获取 registry 持有的基础 factory
     *
     * @return 基础 factory 快照
     */
    public List<RecordingConsumerFactory> getBaseFactories() {
        return Collections.unmodifiableList(new ArrayList<>(baseFactories));
    }

    /**
     * 获取调用方持有的派生 factory
     *
     * @return 派生 factory 快照
     */
    public List<RecordingConsumerFactory> getDerivedFactories() {
        return Collections.unmodifiableList(new ArrayList<>(derivedFactories));
    }

    private void awaitDerivedCreateRelease() {
        if (derivedCreateEntered == null || derivedCreateRelease == null) {
            return;
        }
        derivedCreateEntered.countDown();
        try {
            derivedCreateRelease.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("测试派生 factory 创建被中断", e);
        }
    }
}
