package io.github.surezzzzzz.sdk.elasticsearch.route.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.route.constant.SimpleElasticsearchRouteConstant;
import io.github.surezzzzzz.sdk.elasticsearch.route.support.ElasticsearchAggregationCompatibilityHelper;
import io.github.surezzzzzz.sdk.elasticsearch.route.support.ElasticsearchReflectionHelper;
import io.github.surezzzzzz.sdk.elasticsearch.route.test.SimpleElasticsearchRouteTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ElasticsearchAggregationCompatibilityHelper 单元测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchRouteTestApplication.class)
public class ElasticsearchAggregationCompatibilityHelperTest {

    private static void assertCompatibleClass(Class<?> actual, String es7Name, String es6Name) {
        assertTrue(es7Name.equals(actual.getName()) || es6Name.equals(actual.getName()));
    }

    public static class BucketSortBuilderStub {
        private Integer size;
        private int from;

        public void size(Integer size) {
            this.size = size;
        }

        public void from(int from) {
            this.from = from;
        }
    }

    @Test
    public void testAggregationClassLoading() {
        log.info("=== testAggregationClassLoading ===");
        assertCompatibleClass(ElasticsearchAggregationCompatibilityHelper.loadStatsClass(),
                SimpleElasticsearchRouteConstant.AGG_PACKAGE_METRICS_ES7 + ".Stats",
                SimpleElasticsearchRouteConstant.AGG_PACKAGE_STATS_ES6 + ".Stats");
        assertEquals(SimpleElasticsearchRouteConstant.AGG_CLASS_NUMERIC_SINGLE_VALUE,
                ElasticsearchAggregationCompatibilityHelper.loadNumericSingleValueClass().getName());
        assertCompatibleClass(ElasticsearchAggregationCompatibilityHelper.loadPipelineAggregatorBuildersClass(),
                SimpleElasticsearchRouteConstant.AGG_CLASS_PIPELINE_BUILDERS_ES7,
                SimpleElasticsearchRouteConstant.AGG_CLASS_PIPELINE_BUILDERS_ES6);
        assertEquals(Integer.class, ElasticsearchReflectionHelper.loadMethod(
                ElasticsearchAggregationCompatibilityHelper.loadBucketSortPipelineAggregationBuilderClass(), "size", Integer.class)
                .getParameterTypes()[0]);
    }

    @Test
    public void testBucketSortSetterSignatures() {
        log.info("=== testBucketSortSetterSignatures ===");
        BucketSortBuilderStub stub = new BucketSortBuilderStub();
        ElasticsearchAggregationCompatibilityHelper.applyBucketSortSize(stub, 10);
        ElasticsearchAggregationCompatibilityHelper.applyBucketSortFrom(stub, 2);

        assertEquals(Integer.valueOf(10), stub.size);
        assertEquals(2, stub.from);
    }

    @Test
    public void testBucketValueDetection() {
        log.info("=== testBucketValueDetection ===");
        Map<String, Object> aggregation = new LinkedHashMap<>();
        aggregation.put("buckets", Arrays.asList("a", "b"));

        Object buckets = ElasticsearchAggregationCompatibilityHelper.extractBucketsValue(aggregation);
        assertTrue(ElasticsearchAggregationCompatibilityHelper.isBucketList(buckets));
        assertFalse(ElasticsearchAggregationCompatibilityHelper.isBucketMap(buckets));

        aggregation.put("buckets", Collections.singletonMap("key", "value"));
        buckets = ElasticsearchAggregationCompatibilityHelper.extractBucketsValue(aggregation);
        assertFalse(ElasticsearchAggregationCompatibilityHelper.isBucketList(buckets));
        assertTrue(ElasticsearchAggregationCompatibilityHelper.isBucketMap(buckets));
    }

    @Test
    public void testExtractAfterKey() {
        log.info("=== testExtractAfterKey ===");
        Map<String, Object> aggregation = new LinkedHashMap<>();
        Map<String, Object> afterKey = Collections.singletonMap("extraField", "value");
        aggregation.put("after_key", afterKey);

        assertSame(afterKey, ElasticsearchAggregationCompatibilityHelper.extractAfterKey(aggregation));
    }
}
