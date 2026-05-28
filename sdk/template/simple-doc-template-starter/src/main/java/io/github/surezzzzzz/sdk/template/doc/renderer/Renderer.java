package io.github.surezzzzzz.sdk.template.doc.renderer;

import io.github.surezzzzzz.sdk.template.doc.document.Document;

import java.util.Map;

/**
 * Renderer Interface - 模板渲染策略接口
 *
 * <p>每种模板格式对应一个 Renderer 实现，负责：
 * <ul>
 *   <li>接收预处理后的模板字节和数据</li>
 *   <li>执行引擎层渲染（文本替换、图片插入、表格循环）</li>
 *   <li>返回渲染产物 Document</li>
 * </ul>
 *
 * <p>Renderer 不感知 SDK 逻辑控制（条件块、循环行等），只做数据渲染。
 *
 * @author surezzzzzz
 */
public interface Renderer {

    /**
     * 渲染模板
     *
     * @param templateBytes 预处理后的模板字节（条件块已由 SDK 层处理完毕）
     * @param data          渲染数据
     * @return 渲染产物
     */
    Document render(byte[] templateBytes, Map<String, Object> data);

    /**
     * 获取该 Renderer 支持的文件后缀
     *
     * @return 支持的后缀，如 ".docx"
     */
    String supportedSuffix();
}