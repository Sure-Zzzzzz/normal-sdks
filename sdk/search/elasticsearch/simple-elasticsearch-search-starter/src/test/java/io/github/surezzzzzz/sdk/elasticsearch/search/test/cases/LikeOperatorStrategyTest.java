package io.github.surezzzzzz.sdk.elasticsearch.search.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.search.constant.FieldType;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.FieldMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.builder.strategy.operator.LikeOperatorStrategy;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.builder.strategy.operator.NotLikeOperatorStrategy;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import io.github.surezzzzzz.sdk.elasticsearch.search.test.SimpleElasticsearchSearchTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.WildcardQueryBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LikeOperatorStrategy 单元测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchSearchTestApplication.class)
class LikeOperatorStrategyTest {

    @Autowired
    private LikeOperatorStrategy likeOperatorStrategy;

    @Autowired
    private NotLikeOperatorStrategy notLikeOperatorStrategy;

    @Test
    @DisplayName("keyword 字段 LIKE 未显式通配符时生成包含 wildcard")
    void testLikeKeywordField() {
        FieldMetadata fieldMetadata = FieldMetadata.builder()
                .name("status")
                .type(FieldType.KEYWORD)
                .searchable(true)
                .exactQueryFields(Collections.singletonList("status"))
                .build();

        QueryBuilder result = likeOperatorStrategy.build("status", likeCondition("mock-value"), fieldMetadata);
        log.info("keyword LIKE metadata={}, result={}", fieldMetadata, result);

        assertTrue(result instanceof WildcardQueryBuilder, "keyword LIKE 应生成 WildcardQueryBuilder");
        WildcardQueryBuilder wildcardQuery = (WildcardQueryBuilder) result;
        assertEquals("status", wildcardQuery.fieldName(), "keyword LIKE 应查询根字段");
        assertEquals("*mock-value*", wildcardQuery.value(), "未提供通配符时应补齐前后星号");
    }

    @Test
    @DisplayName("text 加 keyword 子字段 LIKE 使用 keyword wildcard")
    void testLikeTextWithKeywordSubField() {
        FieldMetadata fieldMetadata = buildTextWithKeywordSubField("extraField");

        QueryBuilder result = likeOperatorStrategy.build("extraField", likeCondition("mock-value"), fieldMetadata);
        log.info("text keyword LIKE metadata={}, result={}", fieldMetadata, result);

        assertTrue(result instanceof WildcardQueryBuilder, "text 加 keyword 子字段应生成 WildcardQueryBuilder");
        WildcardQueryBuilder wildcardQuery = (WildcardQueryBuilder) result;
        assertEquals("extraField.keyword", wildcardQuery.fieldName(), "应查询 keyword 子字段");
        assertEquals("*mock-value*", wildcardQuery.value(), "未提供通配符时应补齐前后星号");
    }

    @Test
    @DisplayName("纯 text LIKE 使用根字段 wildcard")
    void testLikePureTextField() {
        FieldMetadata fieldMetadata = FieldMetadata.builder()
                .name("description")
                .type(FieldType.TEXT)
                .searchable(true)
                .matchQueryFields(Collections.singletonList("description"))
                .build();

        QueryBuilder result = likeOperatorStrategy.build("description", likeCondition("mock-value"), fieldMetadata);
        log.info("pure text LIKE metadata={}, result={}", fieldMetadata, result);

        assertTrue(result instanceof WildcardQueryBuilder, "纯 text LIKE 应生成 WildcardQueryBuilder");
        WildcardQueryBuilder wildcardQuery = (WildcardQueryBuilder) result;
        assertEquals("description", wildcardQuery.fieldName(), "纯 text LIKE 应查询根字段");
        assertEquals("*mock-value*", wildcardQuery.value(), "纯 text LIKE 应保留包含匹配");
    }

    @Test
    @DisplayName("LIKE 无合并查询路径时保留 mapping fallback")
    void testLikeFallbackWithoutQueryFields() {
        FieldMetadata keywordField = FieldMetadata.builder()
                .name("status").type(FieldType.KEYWORD).searchable(true).build();
        FieldMetadata textField = FieldMetadata.builder()
                .name("description").type(FieldType.TEXT).searchable(true).build();

        QueryBuilder keywordResult = likeOperatorStrategy.build("status", likeCondition("mock-value"), keywordField);
        QueryBuilder textResult = likeOperatorStrategy.build("description", likeCondition("mock-value"), textField);

        assertTrue(keywordResult instanceof WildcardQueryBuilder, "无查询路径的 keyword 应回退根字段 wildcard");
        assertEquals("status", ((WildcardQueryBuilder) keywordResult).fieldName(), "keyword fallback 应查询根字段");
        assertTrue(textResult instanceof WildcardQueryBuilder, "无查询路径的纯 text 应回退根字段 wildcard");
        assertEquals("description", ((WildcardQueryBuilder) textResult).fieldName(), "纯 text fallback 应查询根字段");
    }

    @Test
    @DisplayName("LIKE 显式星号和问号模式保持原样")
    void testLikeKeepsExplicitWildcardPattern() {
        FieldMetadata fieldMetadata = FieldMetadata.builder()
                .name("status")
                .type(FieldType.KEYWORD)
                .searchable(true)
                .exactQueryFields(Collections.singletonList("status"))
                .build();

        QueryBuilder starResult = likeOperatorStrategy.build("status", likeCondition("prefix-*"), fieldMetadata);
        QueryBuilder questionResult = likeOperatorStrategy.build("status", likeCondition("??-value"), fieldMetadata);
        log.info("explicit star LIKE result={}", starResult);
        log.info("explicit question LIKE result={}", questionResult);

        assertEquals("prefix-*", ((WildcardQueryBuilder) starResult).value(), "显式星号模式不应改写");
        assertEquals("??-value", ((WildcardQueryBuilder) questionResult).value(), "显式问号模式不应改写");
    }

    @Test
    @DisplayName("keyword 与 text keyword 混合 LIKE 覆盖全部精确路径")
    void testLikeMixedExactQueryFields() {
        FieldMetadata fieldMetadata = FieldMetadata.builder()
                .name("extraField")
                .type(FieldType.TEXT)
                .searchable(true)
                .exactQueryFields(Arrays.asList("extraField", "extraField.keyword"))
                .matchQueryFields(Collections.emptyList())
                .build();

        QueryBuilder result = likeOperatorStrategy.build("extraField", likeCondition("mock-value"), fieldMetadata);
        log.info("mixed exact LIKE metadata={}, result={}", fieldMetadata, result);

        assertTrue(result instanceof BoolQueryBuilder, "混合精确路径 LIKE 应生成 BoolQueryBuilder");
        BoolQueryBuilder boolQuery = (BoolQueryBuilder) result;
        assertEquals(2, boolQuery.should().size(), "应覆盖两个精确查询路径");
        assertEquals("1", boolQuery.minimumShouldMatch(), "应至少命中一个查询路径");
        assertWildcardFields(boolQuery, new java.util.HashSet<>(Arrays.asList("extraField", "extraField.keyword")),
                "混合精确路径应完整覆盖 root 与 keyword 字段");
    }

    @Test
    @DisplayName("纯 text 与 text keyword 混合 LIKE 覆盖全部路径")
    void testLikeExactAndMatchQueryFields() {
        FieldMetadata fieldMetadata = FieldMetadata.builder()
                .name("extraField")
                .type(FieldType.TEXT)
                .searchable(true)
                .exactQueryFields(Collections.singletonList("extraField.keyword"))
                .matchQueryFields(Collections.singletonList("extraField"))
                .build();

        QueryBuilder result = likeOperatorStrategy.build("extraField", likeCondition("mock-value"), fieldMetadata);
        log.info("mixed text LIKE metadata={}, result={}", fieldMetadata, result);

        assertTrue(result instanceof BoolQueryBuilder, "混合 text 路径 LIKE 应生成 BoolQueryBuilder");
        BoolQueryBuilder boolQuery = (BoolQueryBuilder) result;
        assertEquals(2, boolQuery.should().size(), "应同时覆盖根字段和 keyword 子字段");
        assertEquals("1", boolQuery.minimumShouldMatch(), "应至少命中一个查询路径");
        assertWildcardFields(boolQuery, new java.util.HashSet<>(Arrays.asList("extraField", "extraField.keyword")),
                "混合 text 路径应完整覆盖 root 与 keyword 字段");
    }

    @Test
    @DisplayName("not_like 使用 metadata wildcard 选路并包装 must_not")
    void testNotLikeUsesWildcardRouting() {
        FieldMetadata fieldMetadata = FieldMetadata.builder()
                .name("extraField")
                .type(FieldType.TEXT)
                .searchable(true)
                .exactQueryFields(Collections.singletonList("extraField.keyword"))
                .matchQueryFields(Collections.singletonList("extraField"))
                .build();

        QueryBuilder result = notLikeOperatorStrategy.build("extraField", likeCondition("mock-value"), fieldMetadata);
        log.info("not_like metadata={}, result={}", fieldMetadata, result);

        assertTrue(result instanceof BoolQueryBuilder, "not_like 应生成 BoolQueryBuilder");
        BoolQueryBuilder boolQuery = (BoolQueryBuilder) result;
        assertEquals(1, boolQuery.mustNot().size(), "not_like 应只包含一个 must_not 子查询");
        assertTrue(boolQuery.mustNot().get(0) instanceof BoolQueryBuilder,
                "多路径 LIKE 应作为一个 bool 子查询放入 must_not");
        BoolQueryBuilder wildcardQuery = (BoolQueryBuilder) boolQuery.mustNot().get(0);
        assertEquals("1", wildcardQuery.minimumShouldMatch(), "多路径 wildcard 应至少命中一个字段");
        assertWildcardFields(wildcardQuery, new java.util.HashSet<>(Arrays.asList("extraField", "extraField.keyword")),
                "not_like 的 must_not 内应完整保留 LIKE 字段选路");
    }

    private void assertWildcardFields(BoolQueryBuilder boolQuery, Set<String> expectedFields, String message) {
        assertTrue(boolQuery.should().stream().allMatch(query -> query instanceof WildcardQueryBuilder),
                message + "，所有分支都必须是 wildcard");
        Set<String> actualFields = boolQuery.should().stream()
                .map(query -> (WildcardQueryBuilder) query)
                .peek(query -> assertEquals("*mock-value*", query.value(), message + "，每个分支的 pattern 必须一致"))
                .map(WildcardQueryBuilder::fieldName)
                .collect(Collectors.toSet());
        assertEquals(expectedFields, actualFields, message);
    }

    private FieldMetadata buildTextWithKeywordSubField(String fieldName) {
        Map<String, FieldMetadata> subFields = new HashMap<>();
        subFields.put("keyword", FieldMetadata.builder()
                .name(fieldName + ".keyword")
                .type(FieldType.KEYWORD)
                .searchable(true)
                .build());
        return FieldMetadata.builder()
                .name(fieldName)
                .type(FieldType.TEXT)
                .searchable(true)
                .subFields(subFields)
                .build();
    }

    private QueryCondition likeCondition(String value) {
        return QueryCondition.builder()
                .field("test")
                .op("like")
                .value(value)
                .build();
    }
}
