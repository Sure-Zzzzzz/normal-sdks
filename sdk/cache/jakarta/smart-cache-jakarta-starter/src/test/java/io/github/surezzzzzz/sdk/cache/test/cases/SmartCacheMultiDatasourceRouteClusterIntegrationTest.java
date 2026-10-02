package io.github.surezzzzzz.sdk.cache.test.cases;

import io.github.surezzzzzz.sdk.cache.test.SmartCacheTestApplication;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Redis Cluster 多数据源路由端到端测试
 *
 * @author surezzzzzz
 */
@SpringBootTest(
        classes = {
                SmartCacheTestApplication.class,
                SmartCacheMultiDatasourceRouteIntegrationTest.RouteFixtureConfiguration.class
        },
        properties = "spring.config.additional-location=classpath:/application-route-cluster.yml"
)
class SmartCacheMultiDatasourceRouteClusterIntegrationTest extends SmartCacheMultiDatasourceRouteIntegrationTest {

    @Override
    protected boolean usesRedis7Cluster() {
        return true;
    }
}
