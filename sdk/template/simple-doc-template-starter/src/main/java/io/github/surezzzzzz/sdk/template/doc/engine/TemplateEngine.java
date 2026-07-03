package io.github.surezzzzzz.sdk.template.doc.engine;

import io.github.surezzzzzz.sdk.template.doc.annotation.SimpleDocTemplateComponent;
import io.github.surezzzzzz.sdk.template.doc.condition.ConditionProcessor;
import io.github.surezzzzzz.sdk.template.doc.constant.SimpleDocTemplateConstant;
import io.github.surezzzzzz.sdk.template.doc.document.Document;
import io.github.surezzzzzz.sdk.template.doc.document.MdDocument;
import io.github.surezzzzzz.sdk.template.doc.exception.TemplateRenderException;
import io.github.surezzzzzz.sdk.template.doc.handler.OutputHandlerRegistry;
import io.github.surezzzzzz.sdk.template.doc.handler.pdf.HtmlPdfRenderEngine;
import io.github.surezzzzzz.sdk.template.doc.handler.pdf.PdfFontRegistrationPlan;
import io.github.surezzzzzz.sdk.template.doc.handler.pdf.PdfFontRegistry;
import io.github.surezzzzzz.sdk.template.doc.html.MarkdownXhtmlRenderer;
import io.github.surezzzzzz.sdk.template.doc.model.MarkdownPdfContext;
import io.github.surezzzzzz.sdk.template.doc.renderer.Renderer;
import io.github.surezzzzzz.sdk.template.doc.renderer.RendererRegistry;
import io.github.surezzzzzz.sdk.template.doc.renderer.word.WordRenderer;
import io.github.surezzzzzz.sdk.template.doc.result.PdfRenderResult;
import io.github.surezzzzzz.sdk.template.doc.result.TemplateRenderResult;
import io.github.surezzzzzz.sdk.template.doc.support.TemplateLocationHelper;
import io.github.surezzzzzz.sdk.template.doc.support.TemplateResourceHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.OutputStream;
import java.util.Map;

/**
 * Template Engine - 模板引擎统一入口（Facade 模式）
 *
 * <p>只负责编排，不含业务逻辑：
 * <ul>
 *   <li>阶段一：SDK 层预处理（ConditionProcessor 处理条件块）</li>
 *   <li>阶段二：引擎层渲染（Renderer 执行数据渲染）</li>
 *   <li>阶段三：链式输出（OutputHandler 写出）</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>
 * // 链式 API（灵活）
 * templateEngine.render("classpath:templates/report.docx", data)
 *     .output()
 *     .toFile("/output", "report.docx");
 *
 * // 快捷方法（简洁）
 * byte[] bytes = templateEngine.renderToBytes("classpath:templates/report.docx", data);
 * templateEngine.renderToFile("classpath:templates/report.docx", data, "/output/report.docx");
 * </pre>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleDocTemplateComponent
@RequiredArgsConstructor
public class TemplateEngine {

    private final TemplateResourceHelper resourceHelper;
    private final TemplateLocationHelper locationHelper;
    private final RendererRegistry rendererRegistry;
    private final OutputHandlerRegistry outputHandlerRegistry;
    private final ConditionProcessor conditionProcessor;
    private final PdfFontRegistry pdfFontRegistry;
    private final MarkdownXhtmlRenderer markdownXhtmlRenderer;
    private final HtmlPdfRenderEngine htmlPdfRenderEngine;

    /**
     * 渲染模板，返回链式结果（核心入口）
     *
     * @param templateLocation 模板路径。包含协议前缀（classpath:、file:）时直接使用；
     *                         否则拼接配置的 templateLocation 根路径
     * @param data             渲染数据
     * @return 渲染结果，可链式调用 output().toFile() / toStream() / toBytes()
     */
    public TemplateRenderResult render(String templateLocation, Map<String, Object> data) {
        String resolved = locationHelper.resolveLocation(templateLocation);
        String suffix = locationHelper.extractSuffix(resolved);

        Renderer renderer = rendererRegistry.find(suffix);
        if (renderer == null) {
            throw io.github.surezzzzzz.sdk.template.doc.exception.TemplateNotFoundException.rendererNotFound(suffix);
        }

        byte[] rawBytes = resourceHelper.loadResourceBytes(resolved);
        byte[] processedBytes = conditionProcessor.process(rawBytes, suffix, data);

        Document document = renderer.render(processedBytes, data);
        return new TemplateRenderResult(document, outputHandlerRegistry, suffix);
    }

    /**
     * 渲染模板并直接返回字节数组（快捷方法）
     *
     * @param templateLocation 模板路径
     * @param data             渲染数据
     * @return 渲染结果字节数组
     */
    public byte[] renderToBytes(String templateLocation, Map<String, Object> data) {
        return render(templateLocation, data).output().toBytes();
    }

    /**
     * 渲染模板并直接输出到文件（快捷方法，完整路径）
     *
     * @param templateLocation 模板路径
     * @param data             渲染数据
     * @param filePath         输出文件完整路径，如 "/output/report.docx"
     */
    public void renderToFile(String templateLocation, Map<String, Object> data, String filePath) {
        render(templateLocation, data).output().toFile(filePath);
    }

    /**
     * 渲染模板并直接输出到文件（快捷方法，目录 + 文件名分开）
     *
     * @param templateLocation 模板路径
     * @param data             渲染数据
     * @param dir              输出目录，如 "/output"
     * @param fileName         文件名，如 "report.docx"
     */
    public void renderToFile(String templateLocation, Map<String, Object> data, String dir, String fileName) {
        render(templateLocation, data).output().toFile(dir, fileName);
    }

    /**
     * 渲染模板并直接写出到流（快捷方法）
     *
     * @param templateLocation 模板路径
     * @param data             渲染数据
     * @param outputStream     目标输出流
     */
    public void renderToStream(String templateLocation, Map<String, Object> data, OutputStream outputStream) {
        render(templateLocation, data).output().toStream(outputStream);
    }

    // ===== PDF 输出（1.1.0）=====

    /**
     * PDF 快捷方法：渲染模板并直接返回 PDF 字节数组
     *
     * @param templateLocation 模板路径（支持 .docx / .md）
     * @param data             渲染数据
     * @return PDF 字节数组
     */
    public byte[] renderToPdf(String templateLocation, Map<String, Object> data) {
        String resolved = locationHelper.resolveLocation(templateLocation);
        String suffix = locationHelper.extractSuffix(resolved);
        if (SimpleDocTemplateConstant.SUFFIX_DOCX.equalsIgnoreCase(suffix)) {
            return renderForPdf(templateLocation, data).toBytes();
        }
        if (SimpleDocTemplateConstant.SUFFIX_MD.equalsIgnoreCase(suffix)) {
            return renderMdToPdf(templateLocation, resolved, data);
        }
        throw TemplateRenderException.formatMismatch(SimpleDocTemplateConstant.PDF_TEMPLATE_SUFFIX_EXPECTED, suffix);
    }

    /**
     * PDF 专用渲染入口：生成带 chart PNG 的 PdfRenderResult
     * <p>
     * 含 chart 时自动走 Chart 路径（chart → PNG），不含 chart 时走普通路径。
     *
     * @param templateLocation 模板路径（仅支持 .docx）
     * @param data             渲染数据
     * @return PdfRenderResult，可调用 toFile / toStream / toBytes 输出 PDF
     */
    public PdfRenderResult renderForPdf(String templateLocation, Map<String, Object> data) {
        String resolved = locationHelper.resolveLocation(templateLocation);
        String suffix = locationHelper.extractSuffix(resolved);
        if (!SimpleDocTemplateConstant.SUFFIX_DOCX.equals(suffix)) {
            throw TemplateRenderException.formatMismatch(SimpleDocTemplateConstant.SUFFIX_DOCX, suffix);
        }

        byte[] rawBytes = resourceHelper.loadResourceBytes(resolved);
        byte[] processedBytes = conditionProcessor.process(rawBytes, suffix, data);

        Renderer renderer = rendererRegistry.find(suffix);
        if (renderer == null) {
            throw io.github.surezzzzzz.sdk.template.doc.exception.TemplateNotFoundException.rendererNotFound(suffix);
        }
        if (!(renderer instanceof WordRenderer)) {
            throw TemplateRenderException.rendererTypeMismatch(SimpleDocTemplateConstant.RENDERER_WORD_CLASS_NAME, renderer.getClass().getName());
        }
        return ((WordRenderer) renderer).renderForPdf(processedBytes, data);
    }

    private byte[] renderMdToPdf(String templateLocation, String resolved, Map<String, Object> data) {
        try {
            TemplateRenderResult result = render(templateLocation, data);
            if (!(result.getDocument() instanceof MdDocument)) {
                throw TemplateRenderException.formatMismatch(SimpleDocTemplateConstant.FORMAT_MD, result.getDocument().getFormat());
            }
            MdDocument document = (MdDocument) result.getDocument();
            String markdown = new String(document.getInternalMdBytes(), java.nio.charset.StandardCharsets.UTF_8);
            MarkdownPdfContext context = new MarkdownPdfContext(
                    templateLocation,
                    resolved,
                    buildBaseUri(resolved),
                    document.getImageReferences(),
                    false
            );
            PdfFontRegistrationPlan fontPlan = pdfFontRegistry.createPlan();
            String xhtml = markdownXhtmlRenderer.toXhtml(markdown, fontPlan.getCssFontFamily(), context);
            return htmlPdfRenderEngine.render(xhtml, context.getBaseUri(), fontPlan);
        } catch (TemplateRenderException e) {
            throw e;
        } catch (Exception e) {
            throw TemplateRenderException.markdownToPdfFailed(e.getMessage(), e);
        }
    }

    private String buildBaseUri(String resolved) {
        if (resolved == null || resolved.isEmpty()) {
            return null;
        }
        String classpathPrefix = SimpleDocTemplateConstant.URL_SCHEME_CLASSPATH_PREFIX;
        if (resolved.startsWith(classpathPrefix)) {
            String path = resolved.substring(classpathPrefix.length());
            int slash = path.lastIndexOf('/');
            return slash >= 0 ? classpathPrefix + path.substring(0, slash + 1) : classpathPrefix;
        }
        try {
            java.nio.file.Path path = java.nio.file.Paths.get(resolved);
            java.nio.file.Path parent = path.toAbsolutePath().getParent();
            return parent == null ? null : parent.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
