package io.github.surezzzzzz.sdk.lock.redis;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * redis-lock route matrix 测试预期配置。
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties("redis-lock.route.matrix.expectation")
public class RedisLockRouteMatrixExpectationProperties {

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
