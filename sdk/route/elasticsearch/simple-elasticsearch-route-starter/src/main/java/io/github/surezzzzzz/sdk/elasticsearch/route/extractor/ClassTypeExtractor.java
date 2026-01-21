package io.github.surezzzzzz.sdk.elasticsearch.route.extractor;

import io.github.surezzzzzz.sdk.elasticsearch.route.annotation.SimpleElasticsearchRouteComponent;
import io.github.surezzzzzz.sdk.elasticsearch.route.support.SpELResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.data.elasticsearch.annotations.Document;

import java.lang.reflect.Method;

/**
 * Class 类型参数提取器
 *
 * <p>从 Class 类型的参数中提取索引名称(通过 @Document 注解)</p>
 * <p>支持 SpEL 表达式解析</p>
 *
 * <p><b>使用场景:</b> elasticsearchTemplate.indexOps(EntityClass.class)</p>
 *
 * @author surezzzzzz
 * @since 1.0.6
 */
@Slf4j
@SimpleElasticsearchRouteComponent
@Order(3)  // 优先级第三
public class ClassTypeExtractor implements IndexNameExtractor {

    @Override
    public String extract(Method method, Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }

        for (Object arg : args) {
            if (supports(arg)) {
                String indexName = extractIndexFromClass((Class<?>) arg);
                if (indexName != null) {
                    log.trace("Extracted index name [{}] from Class annotation", indexName);
                    return indexName;
                }
            }
        }

        return null;
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

            // 解析 SpEL 表达式
            if (SpELResolver.isSpEL(indexName)) {
                String resolved = SpELResolver.resolve(indexName);
                log.trace("Resolved SpEL index name from [{}] to [{}]", indexName, resolved);
                return resolved;
            }

            return indexName;
        }
        return null;
    }
}
