package io.github.surezzzzzz.sdk.template.doc.support;

import io.github.surezzzzzz.sdk.template.doc.annotation.SimpleDocTemplateComponent;
import io.github.surezzzzzz.sdk.template.doc.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.template.doc.constant.SimpleDocTemplateConstant;
import io.github.surezzzzzz.sdk.template.doc.engine.TemplateEngine;
import io.github.surezzzzzz.sdk.template.doc.exception.TemplateRenderException;
import lombok.RequiredArgsConstructor;

import java.io.OutputStream;
import java.util.Map;

/**
 * Markdown Helper
 *
 * <p>面向业务的 Markdown 模板快捷入口，封装 Markdown 模板渲染为 MD/PDF 的常用场景。
 *
 * @author surezzzzzz
 */
@SimpleDocTemplateComponent
@RequiredArgsConstructor
public class MdHelper {

    private final TemplateEngine templateEngine;
    private final TemplateLocationHelper locationHelper;

    /**
     * 渲染 Markdown 模板，返回 Markdown 字节数组。
     *
     * @param templateLocation 模板路径
     * @param data 渲染数据
     * @return Markdown 字节数组
     */
    public byte[] render(String templateLocation, Map<String, Object> data) {
        requireMd(templateLocation);
        return templateEngine.renderToBytes(templateLocation, data);
    }

    /**
     * 渲染 Markdown 模板，写出到流。
     *
     * @param templateLocation 模板路径
     * @param data 渲染数据
     * @param outputStream 输出流
     */
    public void renderToStream(String templateLocation, Map<String, Object> data, OutputStream outputStream) {
        requireMd(templateLocation);
        templateEngine.renderToStream(templateLocation, data, outputStream);
    }

    /**
     * 渲染 Markdown 模板，写出到文件。
     *
     * @param templateLocation 模板路径
     * @param data 渲染数据
     * @param filePath 输出文件完整路径
     */
    public void renderToFile(String templateLocation, Map<String, Object> data, String filePath) {
        requireMd(templateLocation);
        templateEngine.renderToFile(templateLocation, data, filePath);
    }

    /**
     * 渲染 Markdown 模板，写出到文件。
     *
     * @param templateLocation 模板路径
     * @param data 渲染数据
     * @param dir 输出目录
     * @param fileName 输出文件名
     */
    public void renderToFile(String templateLocation, Map<String, Object> data, String dir, String fileName) {
        requireMd(templateLocation);
        templateEngine.renderToFile(templateLocation, data, dir, fileName);
    }

    /**
     * 渲染 Markdown 模板为 PDF 字节数组。
     *
     * @param templateLocation 模板路径
     * @param data 渲染数据
     * @return PDF 字节数组
     */
    public byte[] renderPdf(String templateLocation, Map<String, Object> data) {
        requireMd(templateLocation);
        return templateEngine.renderToPdf(templateLocation, data);
    }

    /**
     * 渲染 Markdown 模板为 PDF 并写出到流。
     *
     * @param templateLocation 模板路径
     * @param data 渲染数据
     * @param outputStream 输出流
     */
    public void renderPdfToStream(String templateLocation, Map<String, Object> data, OutputStream outputStream) {
        requireMd(templateLocation);
        try {
            outputStream.write(templateEngine.renderToPdf(templateLocation, data));
        } catch (java.io.IOException e) {
            throw TemplateRenderException.writeFailed(e);
        }
    }

    /**
     * 渲染 Markdown 模板为 PDF 并写出到文件。
     *
     * @param templateLocation 模板路径
     * @param data 渲染数据
     * @param filePath 输出文件完整路径
     */
    public void renderPdfToFile(String templateLocation, Map<String, Object> data, String filePath) {
        requireMd(templateLocation);
        try {
            java.nio.file.Files.write(java.nio.file.Paths.get(filePath), templateEngine.renderToPdf(templateLocation, data));
        } catch (java.io.IOException e) {
            throw TemplateRenderException.writeFailed(e);
        }
    }

    private void requireMd(String templateLocation) {
        if (!locationHelper.hasSuffix(templateLocation, SimpleDocTemplateConstant.SUFFIX_MD)) {
            String suffix = locationHelper.extractSuffix(locationHelper.resolveLocation(templateLocation));
            throw TemplateRenderException.formatMismatch(ErrorMessage.MD_HELPER_SUFFIX_ONLY, suffix);
        }
    }
}
