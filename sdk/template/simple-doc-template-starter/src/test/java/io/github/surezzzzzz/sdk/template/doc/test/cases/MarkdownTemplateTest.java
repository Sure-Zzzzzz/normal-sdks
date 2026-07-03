package io.github.surezzzzzz.sdk.template.doc.test.cases;

import io.github.surezzzzzz.sdk.template.doc.constant.OutputFormat;
import io.github.surezzzzzz.sdk.template.doc.engine.TemplateEngine;
import io.github.surezzzzzz.sdk.template.doc.exception.TemplateRenderException;
import io.github.surezzzzzz.sdk.template.doc.handler.pdf.PdfFontRegistrationPlan;
import io.github.surezzzzzz.sdk.template.doc.handler.pdf.PdfFontRegistry;
import io.github.surezzzzzz.sdk.template.doc.html.MarkdownXhtmlRenderer;
import io.github.surezzzzzz.sdk.template.doc.model.Image;
import io.github.surezzzzzz.sdk.template.doc.model.MarkdownPdfContext;
import io.github.surezzzzzz.sdk.template.doc.support.MdHelper;
import io.github.surezzzzzz.sdk.template.doc.test.SimpleDocTemplateTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Markdown Template Test
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleDocTemplateTestApplication.class)
@DisplayName("Markdown 模板测试")
class MarkdownTemplateTest {

    @Autowired
    private TemplateEngine templateEngine;

    @Autowired
    private MdHelper mdHelper;

    @Autowired
    private MarkdownXhtmlRenderer markdownXhtmlRenderer;

    @Autowired
    private PdfFontRegistry pdfFontRegistry;

    private static final Path MD_DIR = Paths.get("build/test-output/md");
    private static final Path PDF_DIR = Paths.get("build/test-output/pdf");

    @Test
    @DisplayName("生成输出文件供人工查看")
    void generateOutputFilesForInspection() throws Exception {
        Files.createDirectories(MD_DIR);
        Files.createDirectories(PDF_DIR);
        Files.createDirectories(MD_DIR.resolve("images"));

        Path sourceImage = Paths.get("src/test/resources/images/chart1.png");
        Path targetImage = MD_DIR.resolve("images/chart1.png");
        Files.copy(sourceImage, targetImage, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        Map<String, Object> mdData = buildDataWithRelativeImage();
        byte[] mdBytes = mdHelper.render("classpath:templates/markdown-report.md", mdData);
        String markdown = new String(mdBytes, StandardCharsets.UTF_8);
        Path mdFile = MD_DIR.resolve("markdown-report-output.md");
        Files.write(mdFile, mdBytes);
        log.info("MD 输出: {} ({} bytes)", mdFile.toAbsolutePath(), mdBytes.length);

        Path pdfFile = PDF_DIR.resolve("markdown-report-output.pdf");
        mdHelper.renderPdfToFile("classpath:templates/markdown-report.md", buildData(), pdfFile.toString());
        byte[] pdfBytes = Files.readAllBytes(pdfFile);
        log.info("MD→PDF 输出: {} ({} bytes)", pdfFile.toAbsolutePath(), pdfBytes.length);

        assertTrue(Files.exists(mdFile), "MD 输出文件应存在");
        assertTrue(markdown.contains("# 周报标题"), "MD 输出应包含标题");
        assertTrue(markdown.contains("![logo](images/chart1.png)"), "MD 输出应保留相对图片路径");
        assertValidPdf(pdfBytes, "MdHelper 文件输出");
    }

    private Map<String, Object> buildDataWithRelativeImage() {
        Map<String, Object> data = buildData();
        data.put("logo", new Image("images/chart1.png", 120, 80, "logo"));
        return data;
    }

    @Test
    @DisplayName("renderToBytes：MD 模板输出 Markdown")
    void renderMdToBytesShortcut() {
        byte[] bytes = templateEngine.renderToBytes("classpath:templates/markdown-report.md", buildData());
        String markdown = new String(bytes, StandardCharsets.UTF_8);

        log.info("Markdown 输出:\n{}", markdown);
        assertTrue(markdown.contains("# 周报标题"), "标题应替换");
        assertTrue(markdown.contains("风险A"), "循环应展开");
        assertTrue(markdown.contains("代码块变量：value"), "code block 内变量应替换");
    }

    @Test
    @DisplayName("output：MD 自动推断和显式输出")
    void renderMdOutputAutoAndExplicitFormat() {
        byte[] autoBytes = templateEngine.render("classpath:templates/markdown-report.md", buildData())
                .output()
                .toBytes();
        byte[] explicitBytes = templateEngine.render("classpath:templates/markdown-report.md", buildData())
                .output(OutputFormat.MD)
                .toBytes();

        log.info("自动输出大小: {}, 显式输出大小: {}", autoBytes.length, explicitBytes.length);
        assertArrayEquals(autoBytes, explicitBytes, "自动推断 MD 与显式 MD 输出应一致");
    }

    @Test
    @DisplayName("output PDF：链式 PDF 输出给出明确异常")
    void renderMdThenOutputPdfThrowsHelpfulException() {
        TemplateRenderException exception = assertThrows(TemplateRenderException.class,
                () -> templateEngine.render("classpath:templates/markdown-report.md", buildData())
                        .output(OutputFormat.PDF)
                        .toBytes());

        log.info("PDF 链式输出异常: {}", exception.getMessage());
        assertTrue(exception.getMessage().contains("TemplateEngine.renderToPdf"), "异常应包含推荐入口");
    }

    @Test
    @DisplayName("renderForPdf：拒绝 Markdown 模板")
    void renderForPdfRejectsMdTemplate() {
        TemplateRenderException exception = assertThrows(TemplateRenderException.class,
                () -> templateEngine.renderForPdf("classpath:templates/markdown-report.md", buildData()));

        log.info("renderForPdf 拒绝 MD: {}", exception.getMessage());
        assertTrue(exception.getMessage().contains(".docx"), "应提示 DOCX 后缀");
    }

    @Test
    @DisplayName("MdHelper：渲染 Markdown")
    void mdHelperRenderReturnsMdBytes() {
        byte[] bytes = mdHelper.render("classpath:templates/markdown-report.md", buildData());
        String markdown = new String(bytes, StandardCharsets.UTF_8);

        log.info("MdHelper 输出大小: {}", bytes.length);
        assertTrue(markdown.contains("周报标题"), "MdHelper 应输出 Markdown");
    }

    @Test
    @DisplayName("MdHelper：拒绝 DOCX 模板")
    void mdHelperRejectsDocxTemplate() {
        TemplateRenderException exception = assertThrows(TemplateRenderException.class,
                () -> mdHelper.render("classpath:templates/weekly-report-template.docx", buildData()));

        log.info("MdHelper 拒绝 DOCX: {}", exception.getMessage());
        assertTrue(exception.getMessage().contains("MdHelper"), "应提示 MdHelper 边界");
    }

    @Test
    @DisplayName("renderToPdf：MD 模板输出 PDF")
    void renderToPdfSupportsMdTemplate() {
        byte[] pdfBytes = templateEngine.renderToPdf("classpath:templates/markdown-report.md", buildData());

        String pdfText = extractPdfText(pdfBytes);
        log.info("MD PDF 输出大小: {}, 文本:\n{}", pdfBytes.length, pdfText);
        assertValidPdf(pdfBytes, "MD 模板");
        assertTrue(pdfText.contains("周报标题"), "PDF 应包含标题");
        assertTrue(pdfText.contains("风险A"), "PDF 应包含循环数据");
        assertTrue(pdfText.contains("代码块变量：value"), "PDF 应包含代码块变量");
    }

    @Test
    @DisplayName("Markdown PDF：代码块使用注册字体链避免中文渲染为井号")
    void markdownPdfCodeBlockUsesRegisteredFontFamily() {
        String markdown = "```\n代码块变量：value\n```";
        PdfFontRegistrationPlan fontPlan = pdfFontRegistry.createPlan();
        MarkdownPdfContext context = new MarkdownPdfContext(
                "classpath:templates/markdown-report.md",
                "classpath:templates/markdown-report.md",
                "classpath:templates/",
                Collections.emptyList(),
                false
        );

        String xhtml = markdownXhtmlRenderer.toXhtml(markdown, fontPlan.getCssFontFamily(), context);

        log.info("Markdown code block XHTML:\n{}", xhtml);
        assertTrue(xhtml.contains("代码块变量：value"), "代码块文本应保留中文和变量值");
        assertTrue(xhtml.contains("pre{background:#f6f8fa;padding:8px;white-space:pre-wrap;font-family:"),
                "pre 应显式使用 PDF 字体链");
        assertTrue(xhtml.contains("code{font-family:"), "code 应显式使用 PDF 字体链");
        assertFalse(xhtml.contains("code{font-family:monospace;}"), "code 不应只使用 monospace");
    }

    private void assertValidPdf(byte[] pdfBytes, String label) {
        assertNotNull(pdfBytes, label + " PDF 字节不应为空");
        assertTrue(pdfBytes.length > 2048, label + " PDF 应大于 2KB");
        String header = new String(pdfBytes, 0, 4, StandardCharsets.US_ASCII);
        assertEquals("%PDF", header, label + " PDF 文件头应为 %PDF");
    }

    private String extractPdfText(byte[] pdfBytes) {
        try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.pdmodel.PDDocument.load(pdfBytes)) {
            org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(doc);
        } catch (java.io.IOException e) {
            throw new AssertionError("PDF 文本提取失败", e);
        }
    }

    private Map<String, Object> buildData() {
        Map<String, Object> data = new HashMap<>();
        data.put("title", "周报标题");
        data.put("reportDate", "2026-07-01");
        data.put("hasRisk", true);
        data.put("codeValue", "value");
        data.put("logo", new Image("classpath:images/chart1.png", 120, 80, "logo"));

        List<Map<String, Object>> riskList = new ArrayList<>();
        Map<String, Object> risk = new HashMap<>();
        risk.put("name", "风险A");
        risk.put("status", "已处置|复核");
        riskList.add(risk);
        data.put("riskList", riskList);
        return data;
    }
}
