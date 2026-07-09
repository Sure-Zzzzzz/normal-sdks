package io.github.surezzzzzz.sdk.elasticsearch.search.expression.visitor;

import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.MappingManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 表达式 Visitor 注册表
 * 启动时按索引配置预建 ExpressionToQueryConditionVisitor 实例，运行时只读，并发安全
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleElasticsearchSearchComponent
@RequiredArgsConstructor
public class ExpressionVisitorRegistry {

    /**
     * 无字段映射的默认 visitor，用于未配置 field-mapping 的索引
     */
    private static final ExpressionToQueryConditionVisitor DEFAULT_VISITOR =
            new ExpressionToQueryConditionVisitor(null);

    private final SimpleElasticsearchSearchProperties properties;
    private final MappingManager mappingManager;

    /**
     * key：索引别名（有 alias 用 alias，否则用 name）
     * value：对应的 visitor 实例
     */
    private final Map<String, ExpressionToQueryConditionVisitor> registry = new HashMap<>();

    /**
     * key：索引别名
     * value：ES 字段名 → 中文标签列表
     */
    private final Map<String, Map<String, List<String>>> labelMapRegistry = new HashMap<>();

    @PostConstruct
    public void init() {
        for (SimpleElasticsearchSearchProperties.IndexConfig config : properties.getIndices()) {
            String key = StringUtils.hasText(config.getAlias()) ? config.getAlias() : config.getName();
            Map<String, List<String>> fieldMapping = config.getFieldMapping();
            if (fieldMapping != null && !fieldMapping.isEmpty()) {
                // 展开中文标签列表：中文 → ES 字段名（供 Visitor 翻译表达式）
                Map<String, String> reverseMapping = new HashMap<>();
                for (Map.Entry<String, List<String>> entry : fieldMapping.entrySet()) {
                    String esField = entry.getKey();
                    for (String label : entry.getValue()) {
                        reverseMapping.put(label, esField);
                    }
                }
                registry.put(key, new ExpressionToQueryConditionVisitor(
                        Collections.unmodifiableMap(reverseMapping)));

                // labelMap：ES 字段名 → 中文标签列表（供 hints 展示）
                labelMapRegistry.put(key, Collections.unmodifiableMap(fieldMapping));

                log.debug("Registered expression visitor: index={}, esFields={}, labels={}",
                        key, fieldMapping.size(), reverseMapping.size());
            }
        }
        log.info("ExpressionVisitorRegistry initialized with {} index mappings", registry.size());
    }

    /**
     * 根据索引别名获取对应的 visitor
     * 未配置 field-mapping 的索引返回默认 visitor（字段名原样透传）
     *
     * @param index 索引别名
     * @return visitor 实例
     */
    public ExpressionToQueryConditionVisitor resolve(String index) {
        if (!StringUtils.hasText(index)) {
            return DEFAULT_VISITOR;
        }
        String key = mappingManager.resolveConfigIdentifierOrSelf(index);
        return registry.getOrDefault(key, DEFAULT_VISITOR);
    }

    /**
     * 根据索引别名获取字段 label 映射（ES 字段名 → 中文标签列表）
     * 未配置 field-mapping 的索引返回空 Map
     *
     * @param index 索引别名
     * @return ES 字段名 → 中文标签列表的映射
     */
    public Map<String, List<String>> resolveLabelMap(String index) {
        if (!StringUtils.hasText(index)) {
            return Collections.emptyMap();
        }
        String key = mappingManager.resolveConfigIdentifierOrSelf(index);
        return labelMapRegistry.getOrDefault(key, Collections.emptyMap());
    }
}
