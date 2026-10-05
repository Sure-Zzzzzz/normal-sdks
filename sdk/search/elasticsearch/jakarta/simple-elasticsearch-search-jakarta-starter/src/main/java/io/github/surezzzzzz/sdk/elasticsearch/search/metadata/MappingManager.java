package io.github.surezzzzzz.sdk.elasticsearch.search.metadata;

import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.event.MappingRefreshEvent;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.FieldMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.ResolvedIndex;
import io.github.surezzzzzz.sdk.elasticsearch.search.support.SearchRestHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import java.util.*;

import static io.github.surezzzzzz.sdk.elasticsearch.search.constant.SearchProtocolConstant.*;
import static io.github.surezzzzzz.sdk.elasticsearch.search.support.SearchValidationHelper.object;
import static io.github.surezzzzzz.sdk.elasticsearch.search.support.SearchValidationHelper.protocol;

/**
 * 有界 mapping 快照缓存；刷新失败保留上次成功值，冲突字段明确标记。
 */
@Slf4j
@RequiredArgsConstructor
public class MappingManager {
    private final SearchRestHelper rest;
    private final SimpleElasticsearchSearchProperties properties;
    private final SearchIndexResolver indices;
    private final ApplicationEventPublisher publisher;
    private final Map<String, Map<String, FieldMetadata>> cache = new LinkedHashMap<>();

    /**
     * 缓存键包含 identifier、datasource 和实际目标，不把历史分片与当前分片混用。
     */
    public synchronized Map<String, FieldMetadata> fields(ResolvedIndex target) {
        String key = key(target);
        Map<String, FieldMetadata> snapshot = cache.get(key);
        if (snapshot != null && target.getConfig().isCacheMapping()) {
            log.debug("查询 mapping 缓存命中 datasource={} fields={}", target.getDatasource(), snapshot.size());
            return snapshot;
        }
        return refresh(target);
    }

    /**
     * 先加载成功再替换；失败不能把旧快照标记为新结果。
     */
    public synchronized Map<String, FieldMetadata> refresh(ResolvedIndex target) {
        try {
            Map<String, Object> mapping = rest.perform(target.getDatasource(), VALUE_GET, PATH_SEPARATOR + String.join(INDEX_SEPARATOR, target.getIndices()) + PATH_MAPPING,
                    Collections.singletonMap(VALUE_IGNORE_UNAVAILABLE, String.valueOf(properties.getQueryLimits().isIgnoreUnavailableIndices())), null);
            Map<String, FieldMetadata> fields = new TreeMap<>();
            for (Object value : mapping.values()) {
                Map<String, Object> body = object(object(value).get(VALUE_MAPPINGS));
                Object definitions = body.get(VALUE_PROPERTIES);
                if (definitions != null) parse(object(definitions), EMPTY, false, fields, 0);
            }
            Map<String, FieldMetadata> immutable = Collections.unmodifiableMap(fields);
            if (target.getConfig().isCacheMapping()) {
                cache.put(key(target), immutable);
                while (cache.size() > properties.getQueryLimits().getMappingCacheSize())
                    cache.remove(cache.keySet().iterator().next());
            }
            log.debug("查询 mapping 刷新完成 datasource={} fields={}", target.getDatasource(), fields.size());
            emit(new MappingRefreshEvent(this, target.getDatasource(), true, fields.size()));
            return immutable;
        } catch (RuntimeException error) {
            emit(new MappingRefreshEvent(this, target.getDatasource(), false, 0));
            log.debug("查询 mapping 刷新失败 datasource={} category={}", target.getDatasource(), error.getClass().getSimpleName());
            throw error;
        }
    }

    /**
     * 无需 lazy-load 的配置在启动期加载；失败直接暴露。
     */
    public void initialize() {
        for (SimpleElasticsearchSearchProperties.IndexConfig config : properties.getIndices())
            if (!config.isLazyLoad()) fields(indices.resolve(indices.identifier(config), null));
    }

    /**
     * 定时刷新独立处理每项配置，不让一个失败停止后续项。
     */
    public void refreshAll() {
        for (SimpleElasticsearchSearchProperties.IndexConfig config : properties.getIndices()) {
            try {
                refresh(indices.resolve(indices.identifier(config), null));
            } catch (RuntimeException error) {
                log.debug("查询 mapping 定时刷新项失败 category={}", error.getClass().getSimpleName());
            }
        }
    }

    private void parse(Map<String, Object> definitions, String prefix, boolean nested, Map<String, FieldMetadata> result, int depth) {
        if (depth > properties.getQueryLimits().getMaxDepth()) throw protocol();
        for (Map.Entry<String, Object> entry : definitions.entrySet()) {
            Map<String, Object> definition = object(entry.getValue());
            String name = prefix.isEmpty() ? entry.getKey() : prefix + DOT + entry.getKey();
            String type = definition.get(VALUE_TYPE) instanceof String ? (String) definition.get(VALUE_TYPE) : VALUE_OBJECT;
            boolean isNested = nested || VALUE_NESTED.equals(type);
            boolean docValues = !VALUE_TEXT.equals(type) && !VALUE_OBJECT.equals(type) && !VALUE_NESTED.equals(type)
                    && !Boolean.FALSE.equals(definition.get(VALUE_DOC_VALUES));
            String keyword = null;
            if (definition.get(VALUE_FIELDS) instanceof Map) {
                Map<String, Object> multi = object(definition.get(VALUE_FIELDS));
                for (String field : new TreeSet<>(multi.keySet())) {
                    Map<String, Object> sub = object(multi.get(field));
                    if (VALUE_KEYWORD.equals(sub.get(VALUE_TYPE)) && !Boolean.FALSE.equals(sub.get(VALUE_DOC_VALUES)) && keyword == null)
                        keyword = name + DOT + field;
                }
                parse(multi, name, isNested, result, depth + 1);
            }
            boolean indexed = !Boolean.FALSE.equals(definition.get(VALUE_INDEX));
            FieldMetadata field = new FieldMetadata(name, type, keyword, indexed, docValues, false, isNested, Collections.emptyList());
            FieldMetadata prior = result.get(name);
            if (prior != null && (!prior.getType().equals(field.getType()) || !Objects.equals(prior.getKeywordField(), field.getKeywordField())
                    || prior.isIndexed() != field.isIndexed() || prior.isDocValues() != field.isDocValues() || prior.isNested() != field.isNested() || prior.isConflict()))
                field = field.withConflict(true);
            result.put(name, field);
            if (definition.get(VALUE_PROPERTIES) != null)
                parse(object(definition.get(VALUE_PROPERTIES)), name, isNested, result, depth + 1);
        }
    }

    private String key(ResolvedIndex target) {
        return target.getIdentifier() + NEWLINE + target.getDatasource() + NEWLINE + String.join(INDEX_SEPARATOR, target.getIndices());
    }

    private void emit(MappingRefreshEvent event) {
        try {
            publisher.publishEvent(event);
        } catch (RuntimeException error) {
            log.debug("查询 mapping 监听器失败 category={}", error.getClass().getSimpleName());
        }
    }
}
