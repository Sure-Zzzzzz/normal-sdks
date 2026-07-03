package io.github.surezzzzzz.sdk.template.doc.test.cases;

import io.github.surezzzzzz.sdk.template.doc.engine.TemplateEngine;
import io.github.surezzzzzz.sdk.template.doc.exception.TemplateRenderException;
import io.github.surezzzzzz.sdk.template.doc.handler.pdf.PdfFontRegistrationPlan;
import io.github.surezzzzzz.sdk.template.doc.handler.pdf.PdfFontRegistry;
import io.github.surezzzzzz.sdk.template.doc.html.MarkdownXhtmlRenderer;
import io.github.surezzzzzz.sdk.template.doc.model.Image;
import io.github.surezzzzzz.sdk.template.doc.model.MarkdownPdfContext;
import io.github.surezzzzzz.sdk.template.doc.test.SimpleDocTemplateTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Markdown 安全测试（1.2.0 P0）
 *
 * <p>覆盖 XHTML 转义、危险 scheme 拦截、MD PDF token 不泄露、远程图片拒绝。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleDocTemplateTestApplication.class)
@DisplayName("Markdown 安全测试")
class MarkdownSecurityTest {

    @Autowired
    private MarkdownXhtmlRenderer markdownXhtmlRenderer;

    @Autowired
    private PdfFontRegistry pdfFontRegistry;

    @Autowired
    private TemplateEngine templateEngine;

    @Test
    @DisplayName("原生 HTML 被转义，不泄露 script 标签")
    void rawHtmlEscaped() {
        String markdown = "行内 <script>alert(1)</script> 文本";
        String xhtml = toXhtml(markdown);
        log.info("raw HTML 转义结果:\n{}", xhtml);
        assertFalse(xhtml.contains("<script>"), "不应保留原始 <script> 标签");
        assertTrue(xhtml.contains("&lt;script&gt;"), "script 应被转义");
    }

    @Test
    @DisplayName("危险链接 scheme javascript 被拒绝")
    void dangerousLinkSchemeRejected() {
        String markdown = "[click](javascript:alert(1))";
        TemplateRenderException ex = assertThrows(TemplateRenderException.class, () -> toXhtml(markdown));
        log.info("javascript 链接拦截: {}", ex.getMessage());
    }

    @Test
    @DisplayName("原生图片 file 绝对 URI 被拒绝")
    void nativeImageFileSchemeRejected() {
        String markdown = "![x](file:///etc/passwd)";
        TemplateRenderException ex = assertThrows(TemplateRenderException.class, () -> toXhtml(markdown));
        log.info("file 图片拦截: {}", ex.getMessage());
    }

    @Test
    @DisplayName("混合大小写 scheme 仍被拒绝")
    void mixedCaseSchemeRejected() {
        String markdown = "[click](JaVaScRiPt:alert(1))";
        TemplateRenderException ex = assertThrows(TemplateRenderException.class, () -> toXhtml(markdown));
        log.info("混合大小写 scheme 拦截: {}", ex.getMessage());
    }

    @Test
    @DisplayName("远程图片 http 被拒绝")
    void remoteImageRejected() {
        String markdown = "![x](http://example.com/a.png)";
        TemplateRenderException ex = assertThrows(TemplateRenderException.class, () -> toXhtml(markdown));
        log.info("远程图片拦截: {}", ex.getMessage());
    }

    @Test
    @DisplayName("合法 data URI 图片正常通过")
    void validDataUriAccepted() {
        // 1x1 transparent PNG base64
        String dataUri = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";
        String markdown = "![x](" + dataUri + ")";
        String xhtml = toXhtml(markdown);
        log.info("data URI 图片 xhtml 片段长度: {}", xhtml.length());
        assertTrue(xhtml.contains("data:image/png;base64,"), "data URI 应被保留在 xhtml 中");
    }

    @Test
    @DisplayName("MD→PDF：内部 token 不泄露到 PDF 字节")
    void sentinelTokenNotLeakedInPdf() {
        byte[] pdfBytes = templateEngine.renderToPdf(
                "classpath:templates/markdown-report.md", buildDataWithLogo());
        String pdfText = new String(pdfBytes, StandardCharsets.ISO_8859_1);
        log.info("PDF 大小: {} bytes", pdfBytes.length);
        assertFalse(pdfText.contains("suredt-img://"), "PDF 不应包含内部图片 token");
    }

    private String toXhtml(String markdown) {
        PdfFontRegistrationPlan plan = pdfFontRegistry.createPlan();
        MarkdownPdfContext context = new MarkdownPdfContext(
                "classpath:templates/markdown-report.md",
                "classpath:templates/markdown-report.md",
                "classpath:templates/",
                java.util.Collections.emptyList(),
                false
        );
        return markdownXhtmlRenderer.toXhtml(markdown, plan.getCssFontFamily(), context);
    }

    private Map<String, Object> buildDataWithLogo() {
        Map<String, Object> data = new HashMap<>();
        data.put("title", "周报标题");
        data.put("reportDate", "2026-07-01");
        data.put("hasRisk", true);
        data.put("codeValue", "value");
        data.put("logo", new Image("classpath:images/chart1.png", 120, 80, "logo"));

        List<Map<String, Object>> riskList = new ArrayList<>();
        Map<String, Object> risk = new HashMap<>();
        risk.put("name", "风险A");
        risk.put("status", "已处置");
        riskList.add(risk);
        data.put("riskList", riskList);
        return data;
    }
}
