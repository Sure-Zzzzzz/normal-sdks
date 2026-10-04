package io.github.surezzzzzz.sdk.elasticsearch.route.extractor;

import io.github.surezzzzzz.sdk.elasticsearch.route.annotation.SimpleElasticsearchRouteComponent;
import io.github.surezzzzzz.sdk.elasticsearch.route.support.SpELHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.data.elasticsearch.annotations.Document;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Class 类型参数提取器
 *
 * <p>从 Class 类型的参数中提取索引名称(通过 @Document 注解)</p>
 * <p>支持 SpEL 表达式解析</p>
 *
 * <p><b>使用场景:</b> elasticsearchTemplate.indexOps(EntityClass.class)</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleElasticsearchRouteComponent
@Order(4)
public class ClassTypeExtractor implements IndexNameExtractor {

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
        return arg instanceof Class;
    }

    /**
     * 从 Class 的 @Document 注解中提取索引名称
     * 支持 SpEL 表达式解析
     */
    private String extractIndexFromClass(Class<?> clazz) {
        Document doc = clazz.getAnnotation(Document.class);
        if (doc != null) {
            String indexName = doc.indexName();

            if (SpELHelper.isSpEL(indexName)) {
                String resolved = SpELHelper.resolve(indexName);
                log.trace("Class 注解索引名 SpEL 解析完成，expression=[{}]，index=[{}]", indexName, resolved);
                return resolved;
            }

            return indexName;
        }
        return null;
    }

    private void collectIndexNames(Object value, Set<String> indexNames) {
        if (value == null) {
            return;
        }
        if (supports(value)) {
            String indexName = extractIndexFromClass((Class<?>) value);
            if (indexName != null && !indexName.trim().isEmpty()) {
                indexNames.add(indexName);
                log.trace("从 Class 注解提取索引名成功，index=[{}]", indexName);
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
