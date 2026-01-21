package io.github.surezzzzzz.sdk.elasticsearch.route.extractor;

import io.github.surezzzzzz.sdk.elasticsearch.route.annotation.SimpleElasticsearchRouteComponent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;

import java.lang.reflect.Method;

/**
 * IndexCoordinates 类型参数提取器
 *
 * <p>从 IndexCoordinates 类型的参数中提取索引名称(优先级最高,最准确)</p>
 *
 * @author surezzzzzz
 * @since 1.0.6
 */
@Slf4j
@SimpleElasticsearchRouteComponent
@Order(1)  // 优先级最高
public class IndexCoordinatesExtractor implements IndexNameExtractor {

    @Override
    public String extract(Method method, Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }

        for (Object arg : args) {
            if (supports(arg)) {
                String indexName = ((IndexCoordinates) arg).getIndexName();
                log.trace("Extracted index name [{}] from IndexCoordinates", indexName);
                return indexName;
            }
        }

        return null;
    }

    @Override
    public boolean supports(Object arg) {
        return arg instanceof IndexCoordinates;
    }
}
