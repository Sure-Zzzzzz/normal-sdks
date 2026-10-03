package io.github.surezzzzzz.sdk.s3.route.validator;

import io.github.surezzzzzz.sdk.s3.route.configuration.SimpleS3RouteProperties;

/**
 * S3 Route 配置校验 SPI。
 *
 * @author surezzzzzz
 */
public interface S3RoutePropertiesValidator {

    /**
     * 校验启用时的完整 Route 配置。
     *
     * @param properties Route 配置
     */
    void validate(SimpleS3RouteProperties properties);
}
