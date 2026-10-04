package io.github.surezzzzzz.sdk.elasticsearch.route.test.document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/**
 * 测试文档B - 路由到secondary数据源
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "test_index_b.secondary")
public class TestDocumentB {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String code;

    @Field(type = FieldType.Text)
    private String content;
}
