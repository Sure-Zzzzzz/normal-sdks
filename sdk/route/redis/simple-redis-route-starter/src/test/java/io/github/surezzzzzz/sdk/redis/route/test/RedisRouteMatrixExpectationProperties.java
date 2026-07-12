package io.github.surezzzzzz.sdk.redis.route.test;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * redis-route matrix 测试预期配置。
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties("redis-route.matrix.expectation")
public class RedisRouteMatrixExpectationProperties {

    /**
     * 应探测成功的 datasource。
     */
    private List<String> knownDatasources = new ArrayList<>();

    /**
     * 应探测失败的 datasource。
     */
    private List<String> unknownDatasources = new ArrayList<>();

    /**
     * 不兼容边界说明。
     */
    private String compatibilityBoundary;
}
