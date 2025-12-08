package io.github.surezzzzzz.sdk.elasticsearch.route.test.document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/**
 * 测试文档A - 路由到primary数据源
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "test_index_a")
public class TestDocumentA {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String code;

    @Field(type = FieldType.Text)
    private String content;
}
