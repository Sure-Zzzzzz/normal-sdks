package io.github.surezzzzzz.sdk.elasticsearch.search.query;

import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.FieldMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.ResolvedIndex;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;

import java.util.Map;

/**
 * 不暴露 ES 客户端类型的查询构建扩展口。
 */
public interface SearchDslBuilder {
    /**
     * 编译校验后的查询对象，并追加必要的日期过滤。
     */
    Map<String, Object> query(QueryCondition condition, ResolvedIndex target, Map<String, FieldMetadata> fields);

    /**
     * 校验字段能力并选择有效 keyword 路径。
     */
    String field(String name, ResolvedIndex target, Map<String, FieldMetadata> fields, boolean sortable);
}
