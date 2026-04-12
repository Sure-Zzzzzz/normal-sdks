package io.github.surezzzzzz.sdk.elasticsearch.route.extractor;

import io.github.surezzzzzz.sdk.elasticsearch.route.annotation.SimpleElasticsearchRouteComponent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.data.elasticsearch.core.query.IndexQuery;

import java.lang.reflect.Method;

/**
 * IndexQuery 提取器 - 从 IndexQuery 参数中提取索引名
 *
 * <p><b>适用场景:</b></p>
 * <ul>
 *   <li>用户通过 IndexQueryBuilder 手动指定索引名</li>
 *   <li>批量索引操作 (bulkIndex)</li>
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
 * <p><b>优先级:</b> Order(4) - 低于 IndexCoordinates/Entity/Class 提取器</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleElasticsearchRouteComponent
@Order(4)
public class IndexQueryExtractor implements IndexNameExtractor {

    @Override
    public String extract(Method method, Object[] args) {

        for (Object arg : args) {
            if (supports(arg)) {
                IndexQuery query = (IndexQuery) arg;
                // getIndexName() 在 Spring Data Elasticsearch 4.2+ 才有，4.1.x 不存在
                // 用反射兼容两个版本，4.1.x 下返回 null，由后续提取器处理
                try {
                    java.lang.reflect.Method getIndexName = IndexQuery.class.getMethod("getIndexName");
                    String indexName = (String) getIndexName.invoke(query);
                    if (indexName != null && !indexName.isEmpty()) {
                        log.trace("Extracted index name [{}] from IndexQuery", indexName);
                        return indexName;
                    }
                } catch (NoSuchMethodException e) {
                    // Spring Data Elasticsearch 4.1.x 不支持，忽略
                    log.trace("IndexQuery.getIndexName() not available in current Spring Data Elasticsearch version");
                } catch (Exception e) {
                    log.trace("Failed to extract index name from IndexQuery", e);
                }
            }
        }
        return null;
    }

    @Override
    public boolean supports(Object arg) {
        return arg instanceof IndexQuery;
    }
}
