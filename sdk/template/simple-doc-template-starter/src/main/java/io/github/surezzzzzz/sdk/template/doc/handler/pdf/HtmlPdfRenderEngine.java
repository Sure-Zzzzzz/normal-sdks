package io.github.surezzzzzz.sdk.template.doc.handler.pdf;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import io.github.surezzzzzz.sdk.template.doc.annotation.SimpleDocTemplateComponent;
import io.github.surezzzzzz.sdk.template.doc.exception.TemplateRenderException;
import lombok.RequiredArgsConstructor;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

/**
 * HTML PDF Render Engine
 *
 * <p>将 XHTML-like HTML 渲染为 PDF。
 *
 * @author surezzzzzz
 */
@SimpleDocTemplateComponent
@RequiredArgsConstructor
public class HtmlPdfRenderEngine {

    private final PdfFontRegistry pdfFontRegistry;

    /**
     * 将 XHTML-like HTML 渲染为 PDF 字节数组。
     *
     * @param xhtml XHTML-like HTML
     * @param baseUri 基础 URI
     * @param fontPlan 字体计划
     * @return PDF 字节数组
     */
    public byte[] render(String xhtml, String baseUri, PdfFontRegistrationPlan fontPlan) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        render(xhtml, baseUri, fontPlan, outputStream);
        return outputStream.toByteArray();
    }

    /**
     * 将 XHTML-like HTML 渲染为 PDF 并写出到流。
     *
     * @param xhtml XHTML-like HTML
     * @param baseUri 基础 URI
     * @param fontPlan 字体计划
     * @param outputStream 输出流
     */
    public void render(String xhtml, String baseUri, PdfFontRegistrationPlan fontPlan, OutputStream outputStream) {
        try {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            pdfFontRegistry.registerFonts(builder, fontPlan);
            builder.withHtmlContent(xhtml, baseUri);
            builder.toStream(outputStream);
            builder.run();
        } catch (Exception e) {
            throw TemplateRenderException.htmlToPdfFailed(e.getMessage(), e);
        }
    }
}
