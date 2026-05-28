package io.github.surezzzzzz.sdk.template.doc.processor.condition;

import io.github.surezzzzzz.sdk.template.doc.annotation.SimpleDocTemplateComponent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Condition Handler Registry - 条件处理策略注册表
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleDocTemplateComponent
public class ConditionHandlerRegistry {

    private final Map<String, ConditionHandler> registry = new HashMap<>();

    @Autowired
    private List<ConditionHandler> allHandlers;

    @PostConstruct
    void init() {
        for (ConditionHandler handler : allHandlers) {
            register(handler);
        }
    }

    /**
     * 注册条件处理策略
     *
     * @param handler 条件处理策略
     */
    public void register(ConditionHandler handler) {
        registry.put(handler.supportedSuffix(), handler);
    }

    /**
     * 根据文件后缀查找条件处理策略
     *
     * @param suffix 文件后缀，如 ".docx"
     * @return 条件处理策略，未找到返回 null
     */
    public ConditionHandler find(String suffix) {
        return registry.get(suffix);
    }
}