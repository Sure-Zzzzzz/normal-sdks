package io.github.surezzzzzz.sdk.template.doc.condition;

import java.util.Map;

/**
 * Condition Handler - 条件处理策略接口
 *
 * <p>不同文档格式各自实现，定义如何根据条件值处理模板中的条件块内容。
 *
 * @author surezzzzzz
 */
public interface ConditionHandler {

    /**
     * 处理模板中的条件块
     *
     * @param templateBytes 模板原始字节
     * @param data          渲染数据（包含条件值）
     * @return 处理后的模板字节
     */
    byte[] process(byte[] templateBytes, Map<String, Object> data);

    /**
     * 该 Handler 支持的文件后缀
     *
     * @return 后缀，如 ".docx"
     */
    String supportedSuffix();
}