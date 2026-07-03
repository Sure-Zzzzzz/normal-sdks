package io.github.surezzzzzz.sdk.template.doc.condition;

import io.github.surezzzzzz.sdk.template.doc.annotation.SimpleDocTemplateComponent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Condition Handler Registry - 条件处理策略注册表
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleDocTemplateComponent
@RequiredArgsConstructor
public class ConditionHandlerRegistry {

    private final Map<String, ConditionHandler> registry = new HashMap<>();
    private final List<ConditionHandler> allHandlers;

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
        if (handler == null) {
            return;
        }
        String key = normalize(handler.supportedSuffix());
        ConditionHandler previous = registry.put(key, handler);
        if (previous != null) {
            log.warn("ConditionHandler duplicate registration: {}, previous={}, current={}", key,
                    previous.getClass().getName(), handler.getClass().getName());
        }
    }

    /**
     * 根据文件后缀查找条件处理策略
     *
     * @param suffix 文件后缀，如 ".docx"
     * @return 条件处理策略，未找到返回 null
     */
    public ConditionHandler find(String suffix) {
        return registry.get(normalize(suffix));
    }

    private String normalize(String suffix) {
        return suffix == null ? null : suffix.trim().toLowerCase(Locale.ROOT);
    }
}
