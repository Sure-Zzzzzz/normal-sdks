package io.github.surezzzzzz.sdk.elasticsearch.route.extractor;

import io.github.surezzzzzz.sdk.elasticsearch.route.annotation.SimpleElasticsearchRouteComponent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * IndexCoordinates 类型参数提取器
 *
 * <p>从 IndexCoordinates 类型的参数中提取索引名称（优先级最高、最准确）。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleElasticsearchRouteComponent
@Order(1)  // 优先级最高
public class IndexCoordinatesExtractor implements IndexNameExtractor {

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
        if (!indexNames.isEmpty()) {
            log.trace("从 IndexCoordinates 提取索引名成功，indices={}", indexNames);
        }
        return new ArrayList<>(indexNames);
    }

    @Override
    public boolean supports(Object arg) {
        return arg instanceof IndexCoordinates;
    }

    /**
     * 递归读取 IndexCoordinates 或其集合形态。Spring Data 的 multiSearch 可传
     * List&lt;IndexCoordinates&gt;，必须与单个参数采用同一条跨数据源校验链。
     */
    private void collectIndexNames(Object value, Set<String> indexNames) {
        if (value == null) {
            return;
        }
        if (supports(value)) {
            String[] values = ((IndexCoordinates) value).getIndexNames();
            if (values != null) {
                for (String indexName : values) {
                    if (indexName != null && !indexName.trim().isEmpty()) {
                        indexNames.add(indexName);
                    }
                }
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
