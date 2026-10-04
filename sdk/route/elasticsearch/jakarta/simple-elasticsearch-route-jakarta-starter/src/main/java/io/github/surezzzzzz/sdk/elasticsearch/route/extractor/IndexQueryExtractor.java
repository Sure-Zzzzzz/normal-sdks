package io.github.surezzzzzz.sdk.elasticsearch.route.extractor;

import io.github.surezzzzzz.sdk.elasticsearch.route.annotation.SimpleElasticsearchRouteComponent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Query 索引名提取器 - 从 IndexQuery / UpdateQuery 参数中提取显式索引名。
 *
 * <p><b>适用场景:</b></p>
 * <ul>
 *   <li>用户通过 IndexQueryBuilder 手动指定索引名</li>
 *   <li>批量索引和批量更新操作</li>
 * </ul>
 *
 * <p><b>示例:</b></p>
 * <pre>
 * IndexQuery indexQuery = new IndexQueryBuilder()
 *     .withId("1")
 *     .withObject(doc)
 *     .withIndex("my-custom-index")  // 手动指定索引名
 *     .build();
 *
 * template.index(indexQuery);
 * </pre>
 *
 * <p><b>优先级:</b> Order(2) - 仅次于调用方显式传入的 IndexCoordinates。Query 自带的
 * indexName 也是调用方的显式目标，应优先于实体注解推导的默认索引。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleElasticsearchRouteComponent
@Order(2)
public class IndexQueryExtractor implements IndexNameExtractor {

    @Override
    public String extract(Method method, Object[] args) {
        List<String> indexNames = extractAll(method, args);
        return indexNames.isEmpty() ? null : indexNames.get(0);
    }

    @Override
    public List<String> extractAll(Method method, Object[] args) {
        if (args == null || args.length == 0) {
            return java.util.Collections.emptyList();
        }
        Set<String> indexNames = new LinkedHashSet<>();
        for (Object arg : args) {
            collectIndexNames(arg, indexNames);
        }
        return new ArrayList<>(indexNames);
    }

    @Override
    public boolean supports(Object arg) {
        return arg instanceof IndexQuery || arg instanceof UpdateQuery;
    }

    private void collectIndexNames(Object value, Set<String> indexNames) {
        if (value == null) {
            return;
        }
        if (supports(value)) {
            String indexName = value instanceof IndexQuery
                    ? ((IndexQuery) value).getIndexName()
                    : ((UpdateQuery) value).getIndexName();
            if (indexName != null && !indexName.trim().isEmpty()) {
                indexNames.add(indexName);
                log.trace("从 Query 提取索引名成功，index=[{}]", indexName);
            }
            return;
        }
        if (value instanceof Iterable) {
            for (Object item : (Iterable<?>) value) {
                collectIndexNames(item, indexNames);
            }
            return;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int index = 0; index < length; index++) {
                collectIndexNames(Array.get(value, index), indexNames);
            }
        }
    }
}
