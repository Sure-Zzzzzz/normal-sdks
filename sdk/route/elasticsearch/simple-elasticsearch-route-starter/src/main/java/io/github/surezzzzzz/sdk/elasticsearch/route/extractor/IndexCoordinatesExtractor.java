package io.github.surezzzzzz.sdk.elasticsearch.route.extractor;

import io.github.surezzzzzz.sdk.elasticsearch.route.annotation.SimpleElasticsearchRouteComponent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;

import java.lang.reflect.Method;

/**
 * IndexCoordinates 类型参数提取器
 *
 * <p>从 IndexCoordinates 类型的参数中提取索引名称(优先级最高,最准确)</p>
 *
 * <p><b>版本兼容性:</b> IndexCoordinates 是 Spring Data Elasticsearch 4.0+ 才引入的类，
 * 在 3.2.x (Spring Boot 2.2.x) 中不存在。本提取器使用反射检测类是否存在，
 * 在 3.2.x 环境下自动降级为不支持(返回 false)，不会导致 NoClassDefFoundError。</p>
 *
 * @author surezzzzzz
 * @since 1.0.6
 */
@Slf4j
@SimpleElasticsearchRouteComponent
@Order(1)  // 优先级最高
public class IndexCoordinatesExtractor implements IndexNameExtractor {

    /**
     * IndexCoordinates 类引用（可能为 null，表示当前运行环境不支持）
     */
    private static final Class<?> INDEX_COORDINATES_CLASS;

    /**
     * IndexCoordinates#getIndexName() 方法引用（可能为 null）
     */
    private static final Method GET_INDEX_NAME_METHOD;

    static {
        Class<?> clazz = null;
        Method method = null;
        try {
            clazz = Class.forName("org.springframework.data.elasticsearch.core.mapping.IndexCoordinates");
            method = clazz.getMethod("getIndexName");
            log.debug("IndexCoordinates detected - Spring Data Elasticsearch 4.0+ runtime");
        } catch (ClassNotFoundException e) {
            log.info("IndexCoordinates not available - Spring Data Elasticsearch 3.x runtime, " +
                    "IndexCoordinatesExtractor will be disabled");
        } catch (NoSuchMethodException e) {
            log.warn("IndexCoordinates found but getIndexName() method missing - unexpected Spring Data version", e);
        }
        INDEX_COORDINATES_CLASS = clazz;
        GET_INDEX_NAME_METHOD = method;
    }

    @Override
    public String extract(Method method, Object[] args) {
        if (INDEX_COORDINATES_CLASS == null || GET_INDEX_NAME_METHOD == null) {
            return null;
        }

        if (args == null || args.length == 0) {
            return null;
        }

        for (Object arg : args) {
            if (supports(arg)) {
                try {
                    String indexName = (String) GET_INDEX_NAME_METHOD.invoke(arg);
                    log.trace("Extracted index name [{}] from IndexCoordinates", indexName);
                    return indexName;
                } catch (Exception e) {
                    log.trace("Failed to extract index name from IndexCoordinates via reflection", e);
                }
            }
        }

        return null;
    }

    @Override
    public boolean supports(Object arg) {
        return INDEX_COORDINATES_CLASS != null && INDEX_COORDINATES_CLASS.isInstance(arg);
    }
}
