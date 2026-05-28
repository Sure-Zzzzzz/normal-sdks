package io.github.surezzzzzz.sdk.template.doc.engine;

import io.github.surezzzzzz.sdk.template.doc.annotation.SimpleDocTemplateComponent;
import io.github.surezzzzzz.sdk.template.doc.constant.OutputFormat;
import io.github.surezzzzzz.sdk.template.doc.handler.OutputHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Output Handler Registry - 输出策略注册表
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleDocTemplateComponent
public class OutputHandlerRegistry {

    private final Map<String, OutputHandler> registry = new HashMap<>();

    @Autowired
    private List<OutputHandler> allHandlers;

    @PostConstruct
    void init() {
        for (OutputHandler handler : allHandlers) {
            register(handler);
        }
    }

    /**
     * 注册输出策略
     *
     * @param outputHandler 输出策略
     */
    public void register(OutputHandler outputHandler) {
        registry.put(outputHandler.supportedFormat().getCode(), outputHandler);
    }

    /**
     * 根据输出格式查找策略
     *
     * @param format 输出格式
     * @return 输出策略，未找到返回 null
     */
    public OutputHandler find(OutputFormat format) {
        return registry.get(format.getCode());
    }

    /**
     * 根据文件后缀查找策略（如 ".docx"），供 output() 无参自动推断使用
     *
     * @param suffix 文件后缀（含点，如 ".docx"）
     * @return 输出策略，未找到返回 null
     */
    public OutputHandler findBySuffix(String suffix) {
        if (suffix == null) {
            return null;
        }
        String code = suffix.startsWith(".") ? suffix.substring(1) : suffix;
        return registry.get(code.toLowerCase());
    }
}