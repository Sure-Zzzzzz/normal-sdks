package io.github.surezzzzzz.sdk.elasticsearch.route.extractor;

import io.github.surezzzzzz.sdk.elasticsearch.route.annotation.SimpleElasticsearchRouteComponent;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.SimpleElasticsearchRouteConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;

import java.lang.reflect.Method;

/**
 * IndexCoordinates 类型参数提取器
 *
 * <p>从 IndexCoordinates 类型的参数中提取索引名称(优先级最高,最准确)</p>
 *
 * <p><b>版本兼容性:</b> IndexCoordinates 是 Spring Data Elasticsearch 4.x API，
 * Spring Data Elasticsearch 3.x 环境不存在该类。本提取器通过反射检测，
 * 类不存在时直接跳过，不触发 NoClassDefFoundError。</p>
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
            clazz = Class.forName(SimpleElasticsearchRouteConstant.CLASS_INDEX_COORDINATES);
            method = clazz.getMethod(SimpleElasticsearchRouteConstant.METHOD_GET_INDEX_NAME);
            log.debug("检测到 IndexCoordinates API，启用 IndexCoordinates 参数索引名提取");
        } catch (ClassNotFoundException e) {
            log.info("当前 Spring Data Elasticsearch 版本未提供 IndexCoordinates API，跳过 IndexCoordinates 参数提取");
        } catch (NoSuchMethodException e) {
            log.warn("IndexCoordinates API 未提供 getIndexName() 方法，跳过 IndexCoordinates 参数提取", e);
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
                    log.trace("从 IndexCoordinates 提取索引名成功，index=[{}]", indexName);
                    return indexName;
                } catch (Exception e) {
                    log.trace("通过反射从 IndexCoordinates 提取索引名失败", e);
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
