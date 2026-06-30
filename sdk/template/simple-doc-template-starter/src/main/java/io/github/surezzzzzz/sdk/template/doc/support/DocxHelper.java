package io.github.surezzzzzz.sdk.template.doc.support;

import io.github.surezzzzzz.sdk.template.doc.annotation.SimpleDocTemplateComponent;
import io.github.surezzzzzz.sdk.template.doc.engine.TemplateEngine;
import lombok.RequiredArgsConstructor;

import java.io.OutputStream;
import java.util.Map;

/**
 * DOCX Helper
 *
 * <p>面向业务的 DOCX 模板快捷入口，封装 DOCX 模板渲染为 DOCX/PDF 的常用场景。
 *
 * @author surezzzzzz
 */
@SimpleDocTemplateComponent
@RequiredArgsConstructor
public class DocxHelper {

    private final TemplateEngine templateEngine;

    /**
     * 渲染 DOCX 模板，返回 DOCX 字节数组
     *
     * @param templateLocation 模板路径
     * @param data             渲染数据
     * @return DOCX 字节数组
     */
    public byte[] render(String templateLocation, Map<String, Object> data) {
        return templateEngine.renderToBytes(templateLocation, data);
    }

    /**
     * 渲染 DOCX 模板，写出 DOCX 到流
     *
     * @param templateLocation 模板路径
     * @param data             渲染数据
     * @param outputStream     输出流
     */
    public void renderToStream(String templateLocation, Map<String, Object> data, OutputStream outputStream) {
        templateEngine.renderToStream(templateLocation, data, outputStream);
    }

    /**
     * 渲染 DOCX 模板，写出 DOCX 到文件
     *
     * @param templateLocation 模板路径
     * @param data             渲染数据
     * @param filePath         输出文件完整路径
     */
    public void renderToFile(String templateLocation, Map<String, Object> data, String filePath) {
        templateEngine.renderToFile(templateLocation, data, filePath);
    }

    /**
     * 渲染 DOCX 模板，写出 DOCX 到文件
     *
     * @param templateLocation 模板路径
     * @param data             渲染数据
     * @param dir              输出目录
     * @param fileName         输出文件名
     */
    public void renderToFile(String templateLocation, Map<String, Object> data, String dir, String fileName) {
        templateEngine.renderToFile(templateLocation, data, dir, fileName);
    }

    /**
     * 渲染 DOCX 模板，返回 PDF 字节数组
     *
     * @param templateLocation 模板路径（仅支持 .docx）
     * @param data             渲染数据
     * @return PDF 字节数组
     */
    public byte[] renderPdf(String templateLocation, Map<String, Object> data) {
        return templateEngine.renderForPdf(templateLocation, data).toBytes();
    }

    /**
     * 渲染 DOCX 模板，写出 PDF 到流
     *
     * @param templateLocation 模板路径（仅支持 .docx）
     * @param data             渲染数据
     * @param outputStream     输出流
     */
    public void renderPdfToStream(String templateLocation, Map<String, Object> data, OutputStream outputStream) {
        templateEngine.renderForPdf(templateLocation, data).toStream(outputStream);
    }

    /**
     * 渲染 DOCX 模板，写出 PDF 到文件
     *
     * @param templateLocation 模板路径（仅支持 .docx）
     * @param data             渲染数据
     * @param filePath         输出文件完整路径
     */
    public void renderPdfToFile(String templateLocation, Map<String, Object> data, String filePath) {
        templateEngine.renderForPdf(templateLocation, data).toFile(filePath);
    }
}
