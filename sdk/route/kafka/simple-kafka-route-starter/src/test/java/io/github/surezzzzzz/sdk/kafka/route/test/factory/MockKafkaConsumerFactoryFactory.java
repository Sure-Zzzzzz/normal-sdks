package io.github.surezzzzzz.sdk.kafka.route.test.factory;

import io.github.surezzzzzz.sdk.kafka.route.configuration.SimpleKafkaRouteProperties;
import io.github.surezzzzzz.sdk.kafka.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.kafka.route.factory.KafkaConsumerFactoryFactory;
import org.springframework.kafka.core.ConsumerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 测试用 KafkaConsumerFactoryFactory
 *
 * @author surezzzzzz
 */
public class MockKafkaConsumerFactoryFactory implements KafkaConsumerFactoryFactory {

    private final Map<String, RecordingConsumerFactory> factories = new LinkedHashMap<>();
    private String failDatasourceKey;
    private String failConfigurationDatasourceKey;
    private String failConfigurationErrorCode;
    private int baseCreateCount;

    @Override
    public ConsumerFactory<Object, Object> create(String datasourceKey, SimpleKafkaRouteProperties.DataSourceConfig config) {
        baseCreateCount++;
        if (datasourceKey.equals(failDatasourceKey)) {
            throw new IllegalStateException("mock consumer create failed: " + datasourceKey);
        }
        if (datasourceKey.equals(failConfigurationDatasourceKey)) {
            throw new ConfigurationException(failConfigurationErrorCode,
                    "mock consumer configuration failed: " + datasourceKey);
        }
        RecordingConsumerFactory factory = new RecordingConsumerFactory(datasourceKey);
        factories.put(datasourceKey, factory);
        return factory;
    }

    /**
     * 获取基础 factory 映射
     *
     * @return 基础 factory 映射
     */
    public Map<String, RecordingConsumerFactory> getFactories() {
        return factories;
    }

    /**
     * 获取两参数 SPI 创建调用次数
     *
     * @return 创建调用次数
     */
    public int getBaseCreateCount() {
        return baseCreateCount;
    }

    /**
     * 设置创建失败的数据源标识
     *
     * @param failDatasourceKey 创建失败的数据源标识
     */
    public void setFailDatasourceKey(String failDatasourceKey) {
        this.failDatasourceKey = failDatasourceKey;
    }

    /**
     * 设置抛出配置异常的数据源标识与错误码
     *
     * @param failConfigurationDatasourceKey 抛出配置异常的数据源标识
     * @param failConfigurationErrorCode     配置异常错误码
     */
    public void setFailConfigurationDatasourceKey(String failConfigurationDatasourceKey,
                                                  String failConfigurationErrorCode) {
        this.failConfigurationDatasourceKey = failConfigurationDatasourceKey;
        this.failConfigurationErrorCode = failConfigurationErrorCode;
    }
}
