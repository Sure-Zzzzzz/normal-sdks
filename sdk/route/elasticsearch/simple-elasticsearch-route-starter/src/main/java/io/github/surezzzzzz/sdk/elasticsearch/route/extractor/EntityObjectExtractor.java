package io.github.surezzzzzz.sdk.elasticsearch.route.extractor;

import io.github.surezzzzzz.sdk.elasticsearch.route.annotation.SimpleElasticsearchRouteComponent;
import io.github.surezzzzzz.sdk.elasticsearch.route.support.SpELResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.data.elasticsearch.annotations.Document;

import java.lang.reflect.Method;

/**
 * 实体对象提取器 (修复 save 方法路由 bug)
 *
 * <p>从带有 @Document 注解的实体对象中提取索引名称</p>
 * <p>支持 SpEL 表达式解析</p>
 *
 * <p><b>使用场景:</b> elasticsearchTemplate.save(entity)</p>
 *
 * @author surezzzzzz
 * @since 1.0.6
 */
@Slf4j
@SimpleElasticsearchRouteComponent
@Order(2)  // 优先级第二
public class EntityObjectExtractor implements IndexNameExtractor {

    @Override
    public String extract(Method method, Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }

        for (Object arg : args) {
            if (supports(arg)) {
                String indexName = extractIndexFromClass(arg.getClass());
                if (indexName != null) {
                    log.trace("Extracted index name [{}] from entity object [{}]",
                            indexName, arg.getClass().getSimpleName());
                    return indexName;
                }
            }
        }

        return null;
    }

    @Override
    public boolean supports(Object arg) {
        return arg != null && arg.getClass().isAnnotationPresent(Document.class);
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
