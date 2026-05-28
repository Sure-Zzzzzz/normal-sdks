package io.github.surezzzzzz.sdk.template.doc.processor.condition;

import io.github.surezzzzzz.sdk.template.doc.annotation.SimpleDocTemplateComponent;
import lombok.RequiredArgsConstructor;

import java.util.Map;

/**
 * Condition Processor - SDK 条件块处理器
 *
 * <p>统一处理 [suredt.start:end] 条件块标记，与文档格式无关。
 * 通用逻辑（匹配标记、判断条件值）由本处理器完成，
 * 格式相关的删除操作委托给 ConditionHandler。
 *
 * <p>资源加载由 TemplateEngine 负责，本处理器只处理字节。
 * 后缀由调用方传入，避免重复提取。
 *
 * @author surezzzzzz
 */
@SimpleDocTemplateComponent
@RequiredArgsConstructor
public class ConditionProcessor {

    private final ConditionHandlerRegistry handlerRegistry;

    /**
     * 处理模板：根据条件值删除或保留条件块内容
     *
     * @param templateBytes 模板原始字节（由 TemplateEngine 加载）
     * @param suffix        文件后缀（如 ".docx"，由 TemplateEngine 提取）
     * @param data          渲染数据
     * @return 处理后的模板字节
     */
    public byte[] process(byte[] templateBytes, String suffix, Map<String, Object> data) {
        ConditionHandler handler = handlerRegistry.find(suffix);
        if (handler == null) {
            // 无对应 ConditionHandler → 不处理条件块，原样返回
            return templateBytes;
        }
        return handler.process(templateBytes, data);
    }
}