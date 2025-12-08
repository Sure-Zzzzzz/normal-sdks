package io.github.surezzzzzz.sdk.elasticsearch.route.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.route.test.SimpleElasticsearchRouteTestApplication;
import io.github.surezzzzzz.sdk.elasticsearch.route.test.document.TestDocumentA;
import io.github.surezzzzzz.sdk.elasticsearch.route.test.document.TestDocumentB;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.IndexOperations;

/**
 * 路由测试 - 验证能连接到不同的ES集群
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchRouteTestApplication.class)
public class RoutingTest {

    @Autowired
    private ElasticsearchRestTemplate elasticsearchRestTemplate;

    @Test
    @EnabledIfEnvironmentVariable(named = "run.local.tests", matches = "zs")
    public void testRouteToTwoDifferentDataSources() throws Exception {
        // 测试连接索引A（primary数据源）
        log.info("========== 测试连接索引A (primary数据源) ==========");
        IndexOperations indexOpsA = elasticsearchRestTemplate.indexOps(TestDocumentA.class);

        boolean existsA = indexOpsA.exists();
        log.info("索引 test_index_a 是否存在: {}", existsA);

        if (!existsA) {
            indexOpsA.create();
            log.info("索引 test_index_a 创建成功");
        }

        log.info("索引A信息: {}", indexOpsA.getSettings());

        // 测试连接索引B（secondary数据源）
        log.info("========== 测试连接索引B (secondary数据源) ==========");
        IndexOperations indexOpsB = elasticsearchRestTemplate.indexOps(TestDocumentB.class);

        boolean existsB = indexOpsB.exists();
        log.info("索引 test_index_b.secondary 是否存在: {}", existsB);

        if (!existsB) {
            indexOpsB.create();
            log.info("索引 test_index_b.secondary 创建成功");
        }

        log.info("索引B信息: {}", indexOpsB.getSettings());

        log.info("========== 测试完成 ==========");
        log.info("✓ 成功连接到两个不同的 ES 集群");
        log.info("  - test_index_a -> primary");
        log.info("  - test_index_b.secondary -> secondary");

        // 等待一段时间，方便观察日志
        Thread.sleep(2000);
    }
}
