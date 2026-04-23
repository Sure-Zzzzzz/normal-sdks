package io.github.surezzzzzz.sdk.elasticsearch.search.expression.visitor;

import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.HashMap;
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

    /**
     * key：索引别名（有 alias 用 alias，否则用 name）
     * value：对应的 visitor 实例
     */
    private final Map<String, ExpressionToQueryConditionVisitor> registry = new HashMap<>();

    @PostConstruct
    public void init() {
        for (SimpleElasticsearchSearchProperties.IndexConfig config : properties.getIndices()) {
            String key = StringUtils.hasText(config.getAlias()) ? config.getAlias() : config.getName();
            Map<String, String> fieldMapping = config.getFieldMapping();
            if (fieldMapping != null && !fieldMapping.isEmpty()) {
                registry.put(key, new ExpressionToQueryConditionVisitor(
                        Collections.unmodifiableMap(fieldMapping)));
                log.debug("Registered expression visitor: index={}, mappings={}", key, fieldMapping.size());
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
        return registry.getOrDefault(index, DEFAULT_VISITOR);
    }
}
