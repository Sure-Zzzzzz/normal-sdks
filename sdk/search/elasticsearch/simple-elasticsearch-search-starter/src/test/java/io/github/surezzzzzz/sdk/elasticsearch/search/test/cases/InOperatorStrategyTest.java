package io.github.surezzzzzz.sdk.elasticsearch.search.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.search.constant.FieldType;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.FieldMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.builder.strategy.operator.InOperatorStrategy;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import io.github.surezzzzzz.sdk.elasticsearch.search.test.SimpleElasticsearchSearchTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * InOperatorStrategy 单元测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchSearchTestApplication.class)
class InOperatorStrategyTest {

    @Autowired
    private InOperatorStrategy inOperatorStrategy;

    @Test
    @DisplayName("keyword 与 text.keyword 混用 in → 同时查询主字段和 keyword 子字段")
    void testInWithMixedExactQueryFields() {
        FieldMetadata fieldMetadata = FieldMetadata.builder()
                .name("extraField")
                .type(FieldType.TEXT)
                .searchable(true)
                .exactQueryFields(Arrays.asList("extraField", "extraField.keyword"))
                .matchQueryFields(Collections.emptyList())
                .build();
        QueryBuilder result = inOperatorStrategy.build("extraField",
                inCondition("ACTIVE", "INACTIVE"), fieldMetadata);

        log.info("======================================");
        log.info("测试: keyword 与 text.keyword 混用 in");
        log.info("字段: extraField, 值: ACTIVE/INACTIVE");
        log.info("结果: {}", result);
        log.info("======================================");

        assertTrue(result instanceof BoolQueryBuilder,
                "混合精确路径应生成 BoolQueryBuilder");
        BoolQueryBuilder boolQuery = (BoolQueryBuilder) result;
        assertEquals(2, boolQuery.should().size(),
                "应同时查询两个精确路径");
        assertEquals("1", boolQuery.minimumShouldMatch(),
                "minimumShouldMatch 应为 1");
        assertTrue(boolQuery.should().stream().allMatch(q -> q instanceof TermsQueryBuilder),
                "两个分支都应为 terms 查询");
    }

    @Test
    @DisplayName("纯 text 与 text.keyword 混用 in → 同时保留 terms 与 match")
    void testInWithExactAndMatchQueryFields() {
        FieldMetadata fieldMetadata = FieldMetadata.builder()
                .name("extraField")
                .type(FieldType.TEXT)
                .searchable(true)
                .exactQueryFields(Collections.singletonList("extraField.keyword"))
                .matchQueryFields(Collections.singletonList("extraField"))
                .build();
        QueryBuilder result = inOperatorStrategy.build("extraField",
                inCondition("ACTIVE", "INACTIVE"), fieldMetadata);

        log.info("======================================");
        log.info("测试: 纯 text 与 text.keyword 混用 in");
        log.info("字段: extraField, 值: ACTIVE/INACTIVE");
        log.info("结果: {}", result);
        log.info("======================================");

        assertTrue(result instanceof BoolQueryBuilder,
                "混合 terms/match 路径应生成 BoolQueryBuilder");
        BoolQueryBuilder boolQuery = (BoolQueryBuilder) result;
        assertEquals(3, boolQuery.should().size(),
                "应包含一个 terms 查询和两个 match 查询");
        assertTrue(boolQuery.should().stream().anyMatch(q -> q instanceof TermsQueryBuilder),
                "应包含 terms 查询");
        assertTrue(boolQuery.should().stream().anyMatch(q -> q instanceof MatchQueryBuilder),
                "应包含 match 查询");
    }

    private QueryCondition inCondition(Object... values) {
        return QueryCondition.builder()
                .field("test")
                .op("in")
                .values(Arrays.asList(values))
                .build();
    }
}
