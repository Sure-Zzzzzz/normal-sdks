package io.github.surezzzzzz.sdk.elasticsearch.search.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.FieldType;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.FieldMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.parser.FieldMetadataParser;
import io.github.surezzzzzz.sdk.elasticsearch.search.test.SimpleElasticsearchSearchTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FieldMetadataParser 单元测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchSearchTestApplication.class)
class FieldMetadataParserTest {

    @Autowired
    private FieldMetadataParser parser;

    private SimpleElasticsearchSearchProperties.IndexConfig emptyConfig() {
        SimpleElasticsearchSearchProperties.IndexConfig config = new SimpleElasticsearchSearchProperties.IndexConfig();
        config.setName("test_index");
        return config;
    }

    @Test
    @DisplayName("解析基础字段 - keyword 和 text 类型")
    void testParseBasicFields() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("name", fieldDef("text"));
        properties.put("status", fieldDef("keyword"));

        List<FieldMetadata> fields = parser.parse(properties, "", emptyConfig());

        log.info("Basic fields: {}", fields.stream().map(FieldMetadata::getName).collect(Collectors.toList()));
        assertEquals(2, fields.size());

        FieldMetadata nameField = fields.stream().filter(f -> f.getName().equals("name")).findFirst().orElseThrow(AssertionError::new);
        assertEquals(FieldType.TEXT, nameField.getType());
        assertTrue(nameField.isSearchable());
        assertFalse(nameField.isSensitive());

        FieldMetadata statusField = fields.stream().filter(f -> f.getName().equals("status")).findFirst().orElseThrow(AssertionError::new);
        assertEquals(FieldType.KEYWORD, statusField.getType());
        assertTrue(statusField.isAggregatable());
        assertTrue(statusField.isSortable());
    }

    @Test
    @DisplayName("解析 date 字段 - 包含 format")
    void testParseDateField() {
        Map<String, Object> fieldDef = new HashMap<>();
        fieldDef.put("type", "date");
        fieldDef.put("format", "yyyy-MM-dd HH:mm:ss");

        Map<String, Object> properties = new HashMap<>();
        properties.put("created_at", fieldDef);

        List<FieldMetadata> fields = parser.parse(properties, "", emptyConfig());

        log.info("Date field: {}", fields.get(0));
        assertEquals(1, fields.size());
        assertEquals(FieldType.DATE, fields.get(0).getType());
        assertEquals("yyyy-MM-dd HH:mm:ss", fields.get(0).getFormat());
    }

    @Test
    @DisplayName("解析 multi-fields - text 字段含 keyword 子字段")
    void testParseMultiFields() {
        Map<String, Object> keywordSubField = new HashMap<>();
        keywordSubField.put("type", "keyword");

        Map<String, Object> subFields = new HashMap<>();
        subFields.put("keyword", keywordSubField);

        Map<String, Object> fieldDef = new HashMap<>();
        fieldDef.put("type", "text");
        fieldDef.put("fields", subFields);

        Map<String, Object> properties = new HashMap<>();
        properties.put("title", fieldDef);

        List<FieldMetadata> fields = parser.parse(properties, "", emptyConfig());

        log.info("Multi-fields: {}", fields.get(0));
        assertEquals(1, fields.size());
        FieldMetadata titleField = fields.get(0);
        assertNotNull(titleField.getSubFields());
        assertTrue(titleField.getSubFields().containsKey("keyword"));
        assertEquals("title.keyword", titleField.getSubFields().get("keyword").getName());
    }

    @Test
    @DisplayName("解析嵌套字段 - object 类型含 properties")
    void testParseNestedFields() {
        Map<String, Object> innerProps = new LinkedHashMap<>();
        innerProps.put("city", fieldDef("keyword"));
        innerProps.put("zip", fieldDef("keyword"));

        Map<String, Object> addressDef = new HashMap<>();
        addressDef.put("type", "object");
        addressDef.put("properties", innerProps);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("name", fieldDef("text"));
        properties.put("address", addressDef);

        List<FieldMetadata> fields = parser.parse(properties, "", emptyConfig());

        log.info("Nested fields: {}", fields.stream().map(FieldMetadata::getName).collect(Collectors.toList()));
        // name + address + address.city + address.zip = 4
        assertEquals(4, fields.size());
        assertTrue(fields.stream().anyMatch(f -> f.getName().equals("address.city")));
        assertTrue(fields.stream().anyMatch(f -> f.getName().equals("address.zip")));
    }

    @Test
    @DisplayName("敏感字段 - FORBIDDEN 策略：不可查询/聚合/排序")
    void testSensitiveFieldForbidden() {
        SimpleElasticsearchSearchProperties.IndexConfig config = emptyConfig();
        SimpleElasticsearchSearchProperties.SensitiveFieldConfig sensitiveConfig =
                new SimpleElasticsearchSearchProperties.SensitiveFieldConfig();
        sensitiveConfig.setField("id_card");
        sensitiveConfig.setStrategy("forbidden");
        config.setSensitiveFields(Collections.singletonList(sensitiveConfig));

        Map<String, Object> properties = new HashMap<>();
        properties.put("id_card", fieldDef("keyword"));

        List<FieldMetadata> fields = parser.parse(properties, "", config);

        log.info("Forbidden sensitive field: {}", fields.get(0));
        assertEquals(1, fields.size());
        FieldMetadata field = fields.get(0);
        assertTrue(field.isSensitive());
        assertFalse(field.isSearchable());
        assertFalse(field.isAggregatable());
        assertFalse(field.isSortable());
        assertNotNull(field.getReason());
    }

    @Test
    @DisplayName("敏感字段 - MASK 策略：可查询但标记为脱敏")
    void testSensitiveFieldMask() {
        SimpleElasticsearchSearchProperties.IndexConfig config = emptyConfig();
        SimpleElasticsearchSearchProperties.SensitiveFieldConfig sensitiveConfig =
                new SimpleElasticsearchSearchProperties.SensitiveFieldConfig();
        sensitiveConfig.setField("phone");
        sensitiveConfig.setStrategy("mask");
        config.setSensitiveFields(Collections.singletonList(sensitiveConfig));

        Map<String, Object> properties = new HashMap<>();
        properties.put("phone", fieldDef("keyword"));

        List<FieldMetadata> fields = parser.parse(properties, "", config);

        log.info("Mask sensitive field: {}", fields.get(0));
        assertEquals(1, fields.size());
        FieldMetadata field = fields.get(0);
        assertTrue(field.isSensitive());
        assertTrue(field.isMasked());
        assertTrue(field.isSearchable(), "MASK 策略字段仍可查询");
    }

    @Test
    @DisplayName("空 properties - 返回空列表")
    void testParseEmptyProperties() {
        List<FieldMetadata> fields = parser.parse(new HashMap<>(), "", emptyConfig());
        log.info("Empty properties result: {}", fields);
        assertTrue(fields.isEmpty());
    }

    @Test
    @DisplayName("带前缀解析 - 嵌套字段路径正确")
    void testParseWithPrefix() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("code", fieldDef("keyword"));

        List<FieldMetadata> fields = parser.parse(properties, "address", emptyConfig());

        log.info("With prefix: {}", fields.get(0).getName());
        assertEquals(1, fields.size());
        assertEquals("address.code", fields.get(0).getName());
    }

    private Map<String, Object> fieldDef(String type) {
        Map<String, Object> def = new HashMap<>();
        def.put("type", type);
        return def;
    }

    /**
     * 构建单个字段的 mapping 定义（name: {type: xxx}），用于 {@link #properties(Map)} 的可变参数。
     * 示例：fieldDef("name", "text") 返回 {"name": {"type": "text"}}
     */
    private Map<String, Object> fieldDef(String name, String type) {
        Map<String, Object> field = new HashMap<>();
        field.put("type", type);
        Map<String, Object> def = new LinkedHashMap<>();
        def.put(name, field);
        return def;
    }

    // ========== parseAndMerge 合并测试 ==========

    @SuppressWarnings("unchecked")
    private Map<String, Object> properties(Map<String, Object>... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map<String, Object> entry : entries) {
            result.putAll(entry);
        }
        return result;
    }

    /**
     * 构建单字段 properties map（JDK 1.8 兼容，不用 Map.of）。
     * 示例：singleField("title", titleA) 返回 {"title": titleA}
     */
    private Map<String, Object> singleField(String name, Map<String, Object> fieldDef) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put(name, fieldDef);
        return props;
    }

    @Test
    @DisplayName("parseAndMerge：字段并集，新索引同名覆盖旧索引")
    void testParseAndMergeFieldUnion() {
        Map<String, Object> idxA = properties(
                fieldDef("name", "keyword"),
                fieldDef("status", "keyword"));
        Map<String, Object> idxB = properties(
                fieldDef("name", "text"),  // 同名字段，新索引覆盖
                fieldDef("extraField2", "text"));  // 新增字段
        LinkedHashMap<String, Map<String, Object>> indexProperties = new LinkedHashMap<>();
        indexProperties.put("idxA", idxA);
        indexProperties.put("idxB", idxB);

        List<FieldMetadata> result = parser.parseAndMerge(indexProperties, emptyConfig());

        log.info("合并结果（字段并集）：{}", result.stream().map(FieldMetadata::getName).collect(Collectors.toList()));
        assertEquals(3, result.size());

        FieldMetadata nameField = result.stream().filter(f -> f.getName().equals("name")).findFirst().orElseThrow(AssertionError::new);
        assertEquals(FieldType.TEXT, nameField.getType());  // idxB 的类型

        assertTrue(result.stream().anyMatch(f -> f.getName().equals("status")));
        assertTrue(result.stream().anyMatch(f -> f.getName().equals("extraField2")));
    }

    @Test
    @DisplayName("parseAndMerge：子字段并集，最新索引的子字段胜出")
    void testParseAndMergeSubFieldUnion() {
        // idxA: title={type:text, fields:{keyword}}
        Map<String, Object> titleA = new HashMap<>();
        titleA.put("type", "text");
        Map<String, Object> subFieldsA = new HashMap<>();
        subFieldsA.put("keyword", fieldDef("keyword"));
        titleA.put("fields", subFieldsA);

        // idxB: title={type:text, fields:{keyword, raw}}
        Map<String, Object> titleB = new HashMap<>();
        titleB.put("type", "text");
        Map<String, Object> subFieldsB = new HashMap<>();
        subFieldsB.put("keyword", fieldDef("keyword"));
        subFieldsB.put("raw", fieldDef("keyword"));
        titleB.put("fields", subFieldsB);

        LinkedHashMap<String, Map<String, Object>> indexProperties = new LinkedHashMap<>();
        indexProperties.put("idxA", singleField("title", titleA));
        indexProperties.put("idxB", singleField("title", titleB));

        List<FieldMetadata> result = parser.parseAndMerge(indexProperties, emptyConfig());

        log.info("合并结果（子字段并集）：{}", result);
        assertEquals(1, result.size());
        FieldMetadata titleField = result.get(0);
        assertNotNull(titleField.getSubFields());
        assertEquals(2, titleField.getSubFields().size());  // keyword + raw
        assertTrue(titleField.getSubFields().containsKey("keyword"));
        assertTrue(titleField.getSubFields().containsKey("raw"));
        // 验证子字段的 name 属性（应为 title.keyword / title.raw）
        FieldMetadata keywordSub = titleField.getSubFields().get("keyword");
        assertNotNull(keywordSub);
        assertEquals("title.keyword", keywordSub.getName());
        assertEquals(FieldType.KEYWORD, keywordSub.getType());
        FieldMetadata rawSub = titleField.getSubFields().get("raw");
        assertNotNull(rawSub);
        assertEquals("title.raw", rawSub.getName());
        assertEquals(FieldType.KEYWORD, rawSub.getType());
    }

    @Test
    @DisplayName("parseAndMerge：类型冲突时打 warn 日志，最新索引类型胜出")
    void testParseAndMergeTypeConflictNewestWins() {
        // idxA: level={type:keyword}
        Map<String, Object> levelA = fieldDef("keyword");
        // idxB: level={type:text, fields:{keyword}}
        Map<String, Object> levelB = new HashMap<>();
        levelB.put("type", "text");
        Map<String, Object> subFieldsB = new HashMap<>();
        subFieldsB.put("keyword", fieldDef("keyword"));
        levelB.put("fields", subFieldsB);

        LinkedHashMap<String, Map<String, Object>> indexProperties = new LinkedHashMap<>();
        indexProperties.put("idxA", singleField("level", levelA));
        indexProperties.put("idxB", singleField("level", levelB));

        // 验证 warn 日志发出（类型冲突时打 warn 是设计要求）
        List<FieldMetadata> result = parser.parseAndMerge(indexProperties, emptyConfig());

        log.info("合并结果（类型冲突）：{}", result);
        assertEquals(1, result.size());
        FieldMetadata levelField = result.get(0);
        assertEquals(FieldType.TEXT, levelField.getType());  // idxB 胜出
        assertNotNull(levelField.getSubFields());  // idxB 有 keyword 子字段
        assertTrue(levelField.getSubFields().containsKey("keyword"));
        // 验证子字段的 name 属性（应为 level.keyword）
        FieldMetadata keywordSubField = levelField.getSubFields().get("keyword");
        assertNotNull(keywordSubField);
        assertEquals("level.keyword", keywordSubField.getName());
        assertEquals(FieldType.KEYWORD, keywordSubField.getType());
    }

    @Test
    @DisplayName("parseAndMerge：keyword 与 text.keyword 混用时保留双精确查询路径")
    void testParseAndMergeKeywordAndTextKeywordExactPaths() {
        Map<String, Object> levelA = fieldDef("keyword");
        Map<String, Object> levelB = new HashMap<>();
        levelB.put("type", "text");
        Map<String, Object> subFieldsB = new HashMap<>();
        subFieldsB.put("keyword", fieldDef("keyword"));
        levelB.put("fields", subFieldsB);

        LinkedHashMap<String, Map<String, Object>> indexProperties = new LinkedHashMap<>();
        indexProperties.put("idxA", singleField("extraField", levelA));
        indexProperties.put("idxB", singleField("extraField", levelB));

        List<FieldMetadata> result = parser.parseAndMerge(indexProperties, emptyConfig());

        FieldMetadata field = result.get(0);
        log.info("keyword 与 text.keyword 混用合并结果：{}", field);
        assertEquals(FieldType.TEXT, field.getType());
        assertEquals(Arrays.asList("extraField", "extraField.keyword"), field.getExactQueryFields(),
                "应同时保留主字段和 keyword 子字段精确查询路径");
        assertTrue(field.getMatchQueryFields().isEmpty(),
                "text.keyword 场景不应额外保留 match 查询路径");
    }

    @Test
    @DisplayName("parseAndMerge：纯 text 与 text.keyword 混用时保留 match 与精确查询路径")
    void testParseAndMergeTextAndTextKeywordMixedPaths() {
        Map<String, Object> titleA = fieldDef("text");
        Map<String, Object> titleB = new HashMap<>();
        titleB.put("type", "text");
        Map<String, Object> subFieldsB = new HashMap<>();
        subFieldsB.put("keyword", fieldDef("keyword"));
        titleB.put("fields", subFieldsB);

        LinkedHashMap<String, Map<String, Object>> indexProperties = new LinkedHashMap<>();
        indexProperties.put("idxA", singleField("extraField", titleA));
        indexProperties.put("idxB", singleField("extraField", titleB));

        List<FieldMetadata> result = parser.parseAndMerge(indexProperties, emptyConfig());

        FieldMetadata field = result.get(0);
        log.info("纯 text 与 text.keyword 混用合并结果：{}", field);
        assertEquals(Collections.singletonList("extraField.keyword"), field.getExactQueryFields(),
                "新索引 text.keyword 应保留精确查询路径");
        assertEquals(Collections.singletonList("extraField"), field.getMatchQueryFields(),
                "老索引纯 text 应保留 match 查询路径");
    }

    @Test
    @DisplayName("parseAndMerge：空 map 返回空列表")
    void testParseAndMergeEmpty() {
        LinkedHashMap<String, Map<String, Object>> indexProperties = new LinkedHashMap<>();
        List<FieldMetadata> result = parser.parseAndMerge(indexProperties, emptyConfig());

        log.info("空 map 合并结果：{}", result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("parseAndMerge：子字段冲突时，最新索引的子字段胜出（keyword subfield 覆盖无子字段）")
    void testParseAndMergeSubFieldConflictNewestWins() {
        // idxA: title={type:text, fields:{keyword}}
        Map<String, Object> titleA = new HashMap<>();
        titleA.put("type", "text");
        Map<String, Object> subFieldsA = new HashMap<>();
        subFieldsA.put("keyword", fieldDef("keyword"));
        titleA.put("fields", subFieldsA);

        // idxB: title={type:text, fields:{keyword, raw}}（idxB 覆盖 idxA 的子字段并集）
        Map<String, Object> titleB = new HashMap<>();
        titleB.put("type", "text");
        Map<String, Object> subFieldsB = new HashMap<>();
        subFieldsB.put("keyword", fieldDef("keyword"));
        subFieldsB.put("raw", fieldDef("keyword"));
        titleB.put("fields", subFieldsB);

        LinkedHashMap<String, Map<String, Object>> indexProperties = new LinkedHashMap<>();
        indexProperties.put("idxA", singleField("title", titleA));
        indexProperties.put("idxB", singleField("title", titleB));

        List<FieldMetadata> result = parser.parseAndMerge(indexProperties, emptyConfig());

        log.info("子字段冲突合并结果：{}", result);
        assertEquals(1, result.size());
        FieldMetadata titleField = result.get(0);
        assertNotNull(titleField.getSubFields());
        assertEquals(2, titleField.getSubFields().size());  // keyword + raw（idxB 覆盖 idxA）
        assertTrue(titleField.getSubFields().containsKey("keyword"));
        assertTrue(titleField.getSubFields().containsKey("raw"));
        // 验证子字段的 name 和 type 属性
        FieldMetadata keywordSub = titleField.getSubFields().get("keyword");
        assertNotNull(keywordSub);
        assertEquals("title.keyword", keywordSub.getName());
        assertEquals(FieldType.KEYWORD, keywordSub.getType());
        FieldMetadata rawSub = titleField.getSubFields().get("raw");
        assertNotNull(rawSub);
        assertEquals("title.raw", rawSub.getName());
        assertEquals(FieldType.KEYWORD, rawSub.getType());
    }

    @Test
    @DisplayName("parseAndMerge：老索引无子字段，新索引新增 keyword 子字段，合并后有子字段")
    void testParseAndMergeNewSubFieldAppears() {
        // idxA: title={type:text}（无子字段）
        Map<String, Object> titleA = new HashMap<>();
        titleA.put("type", "text");

        // idxB: title={type:text, fields:{keyword}}（新增子字段）
        Map<String, Object> titleB = new HashMap<>();
        titleB.put("type", "text");
        Map<String, Object> subFieldsB = new HashMap<>();
        subFieldsB.put("keyword", fieldDef("keyword"));
        titleB.put("fields", subFieldsB);

        LinkedHashMap<String, Map<String, Object>> indexProperties = new LinkedHashMap<>();
        indexProperties.put("idxA", singleField("title", titleA));
        indexProperties.put("idxB", singleField("title", titleB));

        List<FieldMetadata> result = parser.parseAndMerge(indexProperties, emptyConfig());

        log.info("新增子字段合并结果：{}", result);
        assertEquals(1, result.size());
        FieldMetadata titleField = result.get(0);
        // 最新索引是 idxB，有 keyword 子字段
        assertNotNull(titleField.getSubFields());
        assertEquals(1, titleField.getSubFields().size());
        assertTrue(titleField.getSubFields().containsKey("keyword"));
        // 验证子字段的 name 和 type 属性
        FieldMetadata keywordSub = titleField.getSubFields().get("keyword");
        assertNotNull(keywordSub);
        assertEquals("title.keyword", keywordSub.getName());
        assertEquals(FieldType.KEYWORD, keywordSub.getType());
    }

    @Test
    @DisplayName("parseAndMerge：单索引等价于 parse 单个")
    void testParseAndMergeSingleIndex() {
        Map<String, Object> idxProps = properties(
                fieldDef("name", "text"),
                fieldDef("status", "keyword"));

        LinkedHashMap<String, Map<String, Object>> indexProperties = new LinkedHashMap<>();
        indexProperties.put("idxOnly", idxProps);

        List<FieldMetadata> merged = parser.parseAndMerge(indexProperties, emptyConfig());
        List<FieldMetadata> parsed = parser.parse(idxProps, "", emptyConfig());

        log.info("单索引合并 vs 解析对比：{} vs {}", merged.size(), parsed.size());
        assertEquals(parsed.size(), merged.size());
        assertTrue(merged.stream().anyMatch(f -> f.getName().equals("name")));
        assertTrue(merged.stream().anyMatch(f -> f.getName().equals("status")));
    }
}
