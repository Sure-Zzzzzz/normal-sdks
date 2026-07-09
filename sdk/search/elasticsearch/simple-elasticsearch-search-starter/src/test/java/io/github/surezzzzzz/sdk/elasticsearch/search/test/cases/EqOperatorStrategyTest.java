package io.github.surezzzzzz.sdk.elasticsearch.search.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.search.constant.FieldType;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.FieldMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.builder.strategy.operator.EqOperatorStrategy;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import io.github.surezzzzzz.sdk.elasticsearch.search.test.SimpleElasticsearchSearchTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EqOperatorStrategy 单元测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchSearchTestApplication.class)
class EqOperatorStrategyTest {

    @Autowired
    private EqOperatorStrategy eqOperatorStrategy;

    // ==================== TEXT + keyword 子字段 → termQuery ====================

    @Test
    @DisplayName("TEXT + keyword 子字段 = 值 → termQuery（field.keyword）")
    void testEqWithKeywordSubField() {
        FieldMetadata fieldMetadata = buildFieldWithKeywordSubField("externalLevel");
        QueryBuilder result = eqOperatorStrategy.build("externalLevel", eqCondition("高级"), fieldMetadata);

        log.info("======================================");
        log.info("测试: TEXT + keyword 子字段 = 值");
        log.info("字段: externalLevel, 值: 高级");
        log.info("结果: {}", result);
        log.info("======================================");

        assertTrue(result instanceof TermQueryBuilder,
                "TEXT + keyword 子字段应生成 TermQueryBuilder");
        TermQueryBuilder termQuery = (TermQueryBuilder) result;
        assertEquals("externalLevel.keyword", termQuery.fieldName(),
                "应为 field.keyword 子字段路径");
        assertEquals("高级", termQuery.value().toString(),
                "值应为高级");
    }

    // ==================== TEXT 无 keyword 子字段 → matchQuery ====================

    @Test
    @DisplayName("TEXT 无 keyword 子字段 = 值 → matchQuery")
    void testEqWithoutKeywordSubField() {
        FieldMetadata fieldMetadata = FieldMetadata.builder()
                .name("title")
                .type(FieldType.TEXT)
                .searchable(true)
                .sortable(false)
                .aggregatable(false)
                .build();
        QueryBuilder result = eqOperatorStrategy.build("title", eqCondition("测试"), fieldMetadata);

        log.info("======================================");
        log.info("测试: TEXT 无 keyword 子字段 = 值");
        log.info("字段: title, 值: 测试");
        log.info("结果: {}", result);
        log.info("======================================");

        assertTrue(result instanceof MatchQueryBuilder,
                "TEXT 无 keyword 子字段应生成 MatchQueryBuilder");
        MatchQueryBuilder matchQuery = (MatchQueryBuilder) result;
        assertEquals("title", matchQuery.fieldName(),
                "字段名应为 title");
        assertEquals("测试", matchQuery.value(),
                "值应为测试");
    }

    // ==================== KEYWORD 类型 → termQuery ====================

    @Test
    @DisplayName("KEYWORD 类型 = 值 → termQuery（直接字段）")
    void testEqKeywordField() {
        FieldMetadata fieldMetadata = FieldMetadata.builder()
                .name("status")
                .type(FieldType.KEYWORD)
                .searchable(true)
                .sortable(true)
                .aggregatable(true)
                .build();
        QueryBuilder result = eqOperatorStrategy.build("status", eqCondition("active"), fieldMetadata);

        log.info("======================================");
        log.info("测试: KEYWORD 类型 = 值");
        log.info("字段: status, 值: active");
        log.info("结果: {}", result);
        log.info("======================================");

        assertTrue(result instanceof TermQueryBuilder,
                "KEYWORD 类型应生成 TermQueryBuilder");
        TermQueryBuilder termQuery = (TermQueryBuilder) result;
        assertEquals("status", termQuery.fieldName(),
                "KEYWORD 直接用字段名");
        assertEquals("active", termQuery.value().toString(),
                "值应为 active");
    }

    // ==================== 混合 mapping 精确路径 → bool should ====================

    @Test
    @DisplayName("keyword 与 text.keyword 混用 = 值 → 同时查询主字段和 keyword 子字段")
    void testEqWithMixedExactQueryFields() {
        FieldMetadata fieldMetadata = FieldMetadata.builder()
                .name("extraField")
                .type(FieldType.TEXT)
                .searchable(true)
                .exactQueryFields(Arrays.asList("extraField", "extraField.keyword"))
                .matchQueryFields(Collections.emptyList())
                .build();
        QueryBuilder result = eqOperatorStrategy.build("extraField", eqCondition("ACTIVE"), fieldMetadata);

        log.info("======================================");
        log.info("测试: keyword 与 text.keyword 混用 = 值");
        log.info("字段: extraField, 值: ACTIVE");
        log.info("结果: {}", result);
        log.info("======================================");

        assertTrue(result instanceof BoolQueryBuilder,
                "混合精确路径应生成 BoolQueryBuilder");
        BoolQueryBuilder boolQuery = (BoolQueryBuilder) result;
        assertEquals(2, boolQuery.should().size(),
                "应同时查询两个精确路径");
        assertEquals("1", boolQuery.minimumShouldMatch(),
                "minimumShouldMatch 应为 1");
    }

    @Test
    @DisplayName("纯 text 与 text.keyword 混用 = 值 → 同时保留 term 与 match")
    void testEqWithExactAndMatchQueryFields() {
        FieldMetadata fieldMetadata = FieldMetadata.builder()
                .name("extraField")
                .type(FieldType.TEXT)
                .searchable(true)
                .exactQueryFields(Collections.singletonList("extraField.keyword"))
                .matchQueryFields(Collections.singletonList("extraField"))
                .build();
        QueryBuilder result = eqOperatorStrategy.build("extraField", eqCondition("ACTIVE"), fieldMetadata);

        log.info("======================================");
        log.info("测试: 纯 text 与 text.keyword 混用 = 值");
        log.info("字段: extraField, 值: ACTIVE");
        log.info("结果: {}", result);
        log.info("======================================");

        assertTrue(result instanceof BoolQueryBuilder,
                "混合 term/match 路径应生成 BoolQueryBuilder");
        BoolQueryBuilder boolQuery = (BoolQueryBuilder) result;
        assertEquals(2, boolQuery.should().size(),
                "应同时保留 term 和 match 查询");
        assertTrue(boolQuery.should().stream().anyMatch(q -> q instanceof TermQueryBuilder),
                "应包含 term 查询");
        assertTrue(boolQuery.should().stream().anyMatch(q -> q instanceof MatchQueryBuilder),
                "应包含 match 查询");
    }

    // ==================== 子字段 Map 为 null → 走 TEXT 分支 ====================

    @Test
    @DisplayName("subFields 为 null 的 TEXT 字段 → matchQuery")
    void testEqWithNullSubFields() {
        FieldMetadata fieldMetadata = FieldMetadata.builder()
                .name("description")
                .type(FieldType.TEXT)
                .searchable(true)
                .sortable(false)
                .aggregatable(false)
                .subFields(null)
                .build();
        QueryBuilder result = eqOperatorStrategy.build("description", eqCondition("文本内容"), fieldMetadata);

        log.info("======================================");
        log.info("测试: subFields 为 null");
        log.info("字段: description, 值: 文本内容");
        log.info("结果: {}", result);
        log.info("======================================");

        assertTrue(result instanceof MatchQueryBuilder,
                "subFields 为 null 的 TEXT 应生成 MatchQueryBuilder");
    }

    // ==================== Helper ====================

    private FieldMetadata buildFieldWithKeywordSubField(String fieldName) {
        Map<String, FieldMetadata> subFields = new HashMap<>();
        subFields.put("keyword", FieldMetadata.builder()
                .name(fieldName + ".keyword")
                .type(FieldType.KEYWORD)
                .searchable(true)
                .sortable(true)
                .aggregatable(true)
                .build());

        return FieldMetadata.builder()
                .name(fieldName)
                .type(FieldType.TEXT)
                .searchable(true)
                .sortable(false)
                .aggregatable(false)
                .subFields(subFields)
                .build();
    }

    private QueryCondition eqCondition(Object value) {
        return QueryCondition.builder()
                .field("test")
                .op("eq")
                .value(value)
                .build();
    }
}