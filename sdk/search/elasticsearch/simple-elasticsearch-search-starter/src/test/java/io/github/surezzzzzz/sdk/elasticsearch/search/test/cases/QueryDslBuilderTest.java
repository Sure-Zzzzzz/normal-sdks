package io.github.surezzzzzz.sdk.elasticsearch.search.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.search.constant.FieldType;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.FieldMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.IndexMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.builder.QueryDslBuilder;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import io.github.surezzzzzz.sdk.elasticsearch.search.test.SimpleElasticsearchSearchTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * QueryDslBuilder 单元测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchSearchTestApplication.class)
class QueryDslBuilderTest {

    @Autowired
    private QueryDslBuilder queryDslBuilder;

    private IndexMetadata indexMetadata;

    @BeforeEach
    void setUp() {
        List<FieldMetadata> fields = Arrays.asList(
                FieldMetadata.builder().name("fieldA").type(FieldType.KEYWORD)
                        .searchable(true).sortable(true).aggregatable(true).build(),
                FieldMetadata.builder().name("fieldB").type(FieldType.LONG)
                        .searchable(true).sortable(true).aggregatable(true).build(),
                FieldMetadata.builder().name("fieldC").type(FieldType.DATE)
                        .searchable(true).sortable(true).aggregatable(true)
                        .format("epoch_millis").build()
        );
        indexMetadata = IndexMetadata.builder()
                .alias("test_index")
                .indexName("test_index")
                .fields(fields)
                .actualIndices(Arrays.asList("test_index"))
                .cachedAt(System.currentTimeMillis())
                .build();
        indexMetadata.buildFieldMap();
    }

    // ==================== AND 扁平化测试 ====================

    @Test
    @DisplayName("三个 AND 条件 → 1层 bool + 3个 must，无嵌套")
    void testThreeAndConditionsFlat() {
        QueryCondition condition = QueryCondition.builder()
                .logic("and")
                .conditions(Arrays.asList(
                        QueryCondition.builder().field("fieldA").op("eq").value("A").build(),
                        QueryCondition.builder().field("fieldB").op("gte").value(10).build(),
                        QueryCondition.builder().field("fieldB").op("lte").value(100).build()
                ))
                .build();

        QueryBuilder result = queryDslBuilder.build(indexMetadata, condition);
        String dsl = result.toString();

        log.info("======================================");
        log.info("测试: 三个 AND 条件扁平化");
        log.info("DSL: {}", dsl);
        log.info("======================================");

        assertTrue(result instanceof BoolQueryBuilder,
                "结果应为 BoolQueryBuilder");
        BoolQueryBuilder boolQuery = (BoolQueryBuilder) result;

        // 一层 bool，不应有嵌套
        assertEquals(3, boolQuery.must().size(),
                "must 中应有 3 个条件");
        assertEquals(0, boolQuery.should().size(),
                "should 应为空");
    }

    @Test
    @DisplayName("两个 AND 条件 → 1层 bool + 2个 must")
    void testTwoAndConditionsFlat() {
        QueryCondition condition = QueryCondition.builder()
                .logic("and")
                .conditions(Arrays.asList(
                        QueryCondition.builder().field("fieldA").op("eq").value("A").build(),
                        QueryCondition.builder().field("fieldB").op("eq").value(20).build()
                ))
                .build();

        QueryBuilder result = queryDslBuilder.build(indexMetadata, condition);
        String dsl = result.toString();

        log.info("======================================");
        log.info("测试: 两个 AND 条件扁平化");
        log.info("DSL: {}", dsl);
        log.info("======================================");

        assertTrue(result instanceof BoolQueryBuilder);
        BoolQueryBuilder boolQuery = (BoolQueryBuilder) result;
        assertEquals(2, boolQuery.must().size(),
                "must 中应有 2 个条件");
    }

    // ==================== OR 扁平化测试 ====================

    @Test
    @DisplayName("三个 OR 条件 → 1层 bool + 3个 should，无嵌套")
    void testThreeOrConditionsFlat() {
        QueryCondition condition = QueryCondition.builder()
                .logic("or")
                .conditions(Arrays.asList(
                        QueryCondition.builder().field("fieldA").op("eq").value("A").build(),
                        QueryCondition.builder().field("fieldA").op("eq").value("B").build(),
                        QueryCondition.builder().field("fieldA").op("eq").value("C").build()
                ))
                .build();

        QueryBuilder result = queryDslBuilder.build(indexMetadata, condition);
        String dsl = result.toString();

        log.info("======================================");
        log.info("测试: 三个 OR 条件扁平化");
        log.info("DSL: {}", dsl);
        log.info("======================================");

        assertTrue(result instanceof BoolQueryBuilder,
                "结果应为 BoolQueryBuilder");
        BoolQueryBuilder boolQuery = (BoolQueryBuilder) result;
        assertEquals(3, boolQuery.should().size(),
                "should 中应有 3 个条件");
        assertEquals("1", boolQuery.minimumShouldMatch(),
                "minimumShouldMatch 应为 1");
    }

    // ==================== AND + OR 混合测试 ====================

    @Test
    @DisplayName("A and (B or C) → bool + must[A, OR(B,C)]，OR 子组放 must")
    void testAndOrMixedConditions() {
        QueryCondition condition = QueryCondition.builder()
                .logic("and")
                .conditions(Arrays.asList(
                        QueryCondition.builder().field("fieldA").op("eq").value("A").build(),
                        QueryCondition.builder()
                                .logic("or")
                                .conditions(Arrays.asList(
                                        QueryCondition.builder().field("fieldA").op("eq").value("B").build(),
                                        QueryCondition.builder().field("fieldA").op("eq").value("C").build()
                                ))
                                .build()
                ))
                .build();

        QueryBuilder result = queryDslBuilder.build(indexMetadata, condition);
        String dsl = result.toString();

        log.info("======================================");
        log.info("测试: A and (B or C) 混合条件");
        log.info("DSL: {}", dsl);
        log.info("======================================");

        assertTrue(result instanceof BoolQueryBuilder,
                "结果应为 BoolQueryBuilder");
        BoolQueryBuilder boolQuery = (BoolQueryBuilder) result;
        // A AND (B OR C)：两个条件都是必须的，均放入 must
        assertEquals(2, boolQuery.must().size(),
                "must 中应有 2 个条件（fieldA=A 和 OR(B,C) 的嵌套 bool）");
        assertEquals(0, boolQuery.should().size(),
                "should 应为空（AND 语义下子条件均放 must）");

        // 验证内层 bool（OR(B,C) 在 must[1]）
        QueryBuilder innerBool = boolQuery.must().get(1);
        assertTrue(innerBool instanceof BoolQueryBuilder,
                "内层应为嵌套的 BoolQueryBuilder（should）");
        BoolQueryBuilder innerShouldBool = (BoolQueryBuilder) innerBool;
        assertEquals(2, innerShouldBool.should().size(),
                "内层 should 中应有 2 个条件");
    }

    // ==================== 单条件测试 ====================

    @Test
    @DisplayName("单条件 → 直接返回子查询，不包额外 bool")
    void testSingleConditionNoBool() {
        QueryCondition condition = QueryCondition.builder()
                .field("fieldA")
                .op("eq")
                .value("test")
                .build();

        QueryBuilder result = queryDslBuilder.build(indexMetadata, condition);
        String dsl = result.toString();

        log.info("======================================");
        log.info("测试: 单条件");
        log.info("DSL: {}", dsl);
        log.info("======================================");

        // 单条件不包 bool，直接返回
        assertFalse(result instanceof BoolQueryBuilder,
                "单条件不应包额外的 BoolQueryBuilder");
    }

    // ==================== 复杂组合测试 ====================

    @Test
    @DisplayName("(A or B) and (C or D) → 两层 OR 均保留，各自扁平")
    void testComplexAndOr() {
        QueryCondition condition = QueryCondition.builder()
                .logic("and")
                .conditions(Arrays.asList(
                        QueryCondition.builder()
                                .logic("or")
                                .conditions(Arrays.asList(
                                        QueryCondition.builder().field("fieldA").op("eq").value("A").build(),
                                        QueryCondition.builder().field("fieldA").op("eq").value("B").build()
                                ))
                                .build(),
                        QueryCondition.builder()
                                .logic("or")
                                .conditions(Arrays.asList(
                                        QueryCondition.builder().field("fieldB").op("eq").value(1).build(),
                                        QueryCondition.builder().field("fieldB").op("eq").value(2).build()
                                ))
                                .build()
                ))
                .build();

        QueryBuilder result = queryDslBuilder.build(indexMetadata, condition);
        String dsl = result.toString();

        log.info("======================================");
        log.info("测试: (A or B) and (C or D)");
        log.info("DSL: {}", dsl);
        log.info("======================================");

        assertTrue(result instanceof BoolQueryBuilder);
        BoolQueryBuilder boolQuery = (BoolQueryBuilder) result;
        assertEquals(2, boolQuery.must().size(),
                "must 中应有 2 个条件");

        // 两个 OR 都应该扁平
        for (QueryBuilder sub : boolQuery.must()) {
            if (sub instanceof BoolQueryBuilder) {
                BoolQueryBuilder subBool = (BoolQueryBuilder) sub;
                assertEquals(2, subBool.should().size(),
                        "每个 OR 分支都应扁平为 2 个 should");
            }
        }
    }
}