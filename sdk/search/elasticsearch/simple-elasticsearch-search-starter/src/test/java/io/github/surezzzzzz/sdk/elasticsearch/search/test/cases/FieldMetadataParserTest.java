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
}
