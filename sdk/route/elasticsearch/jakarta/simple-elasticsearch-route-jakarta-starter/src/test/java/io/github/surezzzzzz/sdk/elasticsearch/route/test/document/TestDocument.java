package io.github.surezzzzzz.sdk.elasticsearch.route.test.document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/**
 * 测试文档 - 使用 SpEL 动态索引名
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "#{T(io.github.surezzzzzz.sdk.elasticsearch.route.test.DocumentIndexHelper).processIndexName('test_index')}")
public class TestDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String code;

    @Field(type = FieldType.Text)
    private String name;

    @Field(type = FieldType.Text)
    private String description;
}
