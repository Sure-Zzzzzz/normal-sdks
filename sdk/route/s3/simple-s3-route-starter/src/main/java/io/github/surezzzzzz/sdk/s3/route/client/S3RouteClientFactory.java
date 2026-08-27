package io.github.surezzzzzz.sdk.s3.route.client;

import com.amazonaws.services.s3.AmazonS3;
import io.github.surezzzzzz.sdk.s3.route.configuration.SimpleS3RouteProperties;

/**
 * S3 Route target 客户端创建 SPI。
 *
 * @author surezzzzzz
 */
public interface S3RouteClientFactory {

    /**
     * 为单个 target 创建 S3 客户端。
     *
     * @param targetKey target key
     * @param target    target 配置
     * @return 已就绪的 S3 客户端
     */
    AmazonS3 create(String targetKey, SimpleS3RouteProperties.TargetConfig target);
}
