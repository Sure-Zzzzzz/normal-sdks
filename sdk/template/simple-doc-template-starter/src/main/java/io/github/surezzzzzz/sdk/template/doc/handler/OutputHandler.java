package io.github.surezzzzzz.sdk.template.doc.handler;

import io.github.surezzzzzz.sdk.template.doc.constant.OutputFormat;
import io.github.surezzzzzz.sdk.template.doc.document.Document;

import java.io.OutputStream;

/**
 * OutputHandler Interface - 文档输出策略接口
 *
 * <p>每种输出格式对应一个 OutputHandler 实现，负责：
 * <ul>
 *   <li>接收 Document</li>
 *   <li>转换为目标格式</li>
 *   <li>写出到目标位置</li>
 * </ul>
 *
 * @author surezzzzzz
 */
public interface OutputHandler {

    /**
     * 写出文档到文件
     *
     * @param document 渲染产物
     * @param filePath 输出文件路径
     * @throws TemplateRenderException 写出异常
     */
    void writeToFile(Document document, String filePath);

    /**
     * 写出文档到流
     *
     * @param document    渲染产物
     * @param outputStream 输出流
     * @throws TemplateRenderException 写出异常
     */
    void writeToStream(Document document, OutputStream outputStream);

    /**
     * 获取字节数组
     *
     * @param document 渲染产物
     * @return 字节数组
     * @throws TemplateRenderException 写出异常
     */
    byte[] toBytes(Document document);

    /**
     * 该 Handler 对应的输出格式
     *
     * @return 输出格式枚举
     */
    OutputFormat supportedFormat();
}