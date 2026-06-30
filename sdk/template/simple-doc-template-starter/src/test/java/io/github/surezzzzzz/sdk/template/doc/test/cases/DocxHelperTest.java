package io.github.surezzzzzz.sdk.template.doc.test.cases;

import io.github.surezzzzzz.sdk.template.doc.engine.TemplateEngine;
import io.github.surezzzzzz.sdk.template.doc.exception.TemplateNotFoundException;
import io.github.surezzzzzz.sdk.template.doc.model.Image;
import io.github.surezzzzzz.sdk.template.doc.support.DocxHelper;
import io.github.surezzzzzz.sdk.template.doc.test.SimpleDocTemplateTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DocxHelper 测试（1.1.1）
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleDocTemplateTestApplication.class)
@DisplayName("DocxHelper 测试")
class DocxHelperTest {

    @Autowired
    private DocxHelper docxHelper;

    @Autowired
    private TemplateEngine templateEngine;

    private static final Path OUTPUT_DIR = Paths.get("build/test-output/helper");

    private Map<String, Object> fullData;

    @BeforeEach
    void setUp() throws Exception {
        Files.createDirectories(OUTPUT_DIR);
        fullData = buildFullData();
    }

    // ==================== DOCX 模板 → DOCX ====================

    @Test
    @DisplayName("render：DOCX 模板渲染返回有效 DOCX 字节数组")
    void renderReturnsDocxBytes() {
        byte[] docxBytes = docxHelper.render(
                "classpath:templates/weekly-report-template.docx", fullData);

        log.info("DOCX 字节验证，大小: {} bytes", docxBytes != null ? docxBytes.length : 0);
        assertNotNull(docxBytes);
        assertTrue(docxBytes.length > 5000, "DOCX 应大于 5KB");
        assertTrue(isZipFile(docxBytes), "DOCX 应为 ZIP 结构");
    }

    @Test
    @DisplayName("render：DOCX 字节包含替换后的文本内容")
    void renderContainsReplacedText() throws Exception {
        byte[] docxBytes = docxHelper.render(
                "classpath:templates/weekly-report-template.docx", fullData);

        assertNotNull(docxBytes);
        // DOCX 是 ZIP，用 XWPFDocument 读取并提取文本
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            StringBuilder sb = new StringBuilder();
            doc.getParagraphs().forEach(p -> sb.append(p.getText()).append("\n"));
            String allText = sb.toString();
            assertTrue(allText.contains("你好我好大家好单位"),
                    "DOCX 应包含替换后的 clientName");
            assertTrue(allText.contains("2026年4月10日"),
                    "DOCX 应包含替换后的 reportDate");
        }
    }

    @Test
    @DisplayName("renderToStream：DOCX 模板渲染写出到流")
    void renderToStreamWritesDocx() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        docxHelper.renderToStream(
                "classpath:templates/weekly-report-template.docx", fullData, baos);

        byte[] docxBytes = baos.toByteArray();
        log.info("renderToStream 验证，大小: {} bytes", docxBytes.length);
        assertNotNull(docxBytes);
        assertTrue(docxBytes.length > 5000);
        assertTrue(isZipFile(docxBytes));
    }

    @Test
    @DisplayName("renderToFile：DOCX 模板渲染写出到文件")
    void renderToFileWritesDocx() throws Exception {
        Path outputPath = OUTPUT_DIR.resolve("docx-helper-output.docx");

        docxHelper.renderToFile(
                "classpath:templates/weekly-report-template.docx", fullData, outputPath.toString());

        assertTrue(Files.exists(outputPath), "输出文件应存在");
        byte[] docxBytes = Files.readAllBytes(outputPath);
        log.info("renderToFile 验证，大小: {} bytes", docxBytes.length);
        assertTrue(docxBytes.length > 5000);
    }

    // ==================== DOCX 模板 → PDF ====================

    @Test
    @DisplayName("renderPdf：DOCX 模板渲染返回有效 PDF 字节数组")
    void renderPdfReturnsPdfBytes() {
        byte[] pdfBytes = docxHelper.renderPdf(
                "classpath:templates/weekly-report-template.docx", fullData);

        log.info("PDF 字节验证，大小: {} bytes", pdfBytes != null ? pdfBytes.length : 0);
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 2048, "PDF 应大于 2KB");
        assertTrue(isPdfFile(pdfBytes), "PDF 文件头应为 %PDF");
    }

    @Test
    @DisplayName("renderPdfToFile：DOCX 模板渲染写出 PDF 文件")
    void renderPdfToFileWritesPdf() throws Exception {
        Path pdfPath = OUTPUT_DIR.resolve("docx-helper-pdf-output.pdf");

        docxHelper.renderPdfToFile(
                "classpath:templates/weekly-report-template.docx", fullData, pdfPath.toString());

        assertTrue(Files.exists(pdfPath), "PDF 文件应存在");
        byte[] pdfBytes = Files.readAllBytes(pdfPath);
        log.info("renderPdfToFile 验证，大小: {} bytes", pdfBytes.length);
        assertTrue(pdfBytes.length > 2048);
        assertTrue(isPdfFile(pdfBytes));
    }

    @Test
    @DisplayName("renderPdfToStream：DOCX 模板渲染写出 PDF 到流")
    void renderPdfToStreamWritesPdf() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        docxHelper.renderPdfToStream(
                "classpath:templates/weekly-report-template.docx", fullData, baos);

        byte[] pdfBytes = baos.toByteArray();
        log.info("renderPdfToStream 验证，大小: {} bytes", pdfBytes.length);
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 2048);
        assertTrue(isPdfFile(pdfBytes));
    }

    // ==================== 异常场景 ====================

    @Test
    @DisplayName("render：模板不存在抛 TemplateNotFoundException")
    void renderTemplateNotFoundThrowsException() {
        TemplateNotFoundException ex = assertThrows(
                TemplateNotFoundException.class,
                () -> docxHelper.render("classpath:templates/not-exist-12345.docx", fullData)
        );
        log.info("render 模板不存在异常: errorCode={}, message={}",
                ex.getErrorCode(), ex.getMessage());
        assertNotNull(ex.getErrorCode());
    }

    @Test
    @DisplayName("renderPdf：模板不存在抛 TemplateNotFoundException")
    void renderPdfTemplateNotFoundThrowsException() {
        TemplateNotFoundException ex = assertThrows(
                TemplateNotFoundException.class,
                () -> docxHelper.renderPdf("classpath:templates/not-exist-67890.docx", fullData)
        );
        log.info("renderPdf 模板不存在异常: errorCode={}, message={}",
                ex.getErrorCode(), ex.getMessage());
        assertNotNull(ex.getErrorCode());
    }

    // ==================== 工具方法 ====================

    private boolean isZipFile(byte[] bytes) {
        if (bytes == null || bytes.length < 4) return false;
        return bytes[0] == 0x50 && bytes[1] == 0x4B; // PK
    }

    private boolean isPdfFile(byte[] bytes) {
        if (bytes == null || bytes.length < 4) return false;
        return bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F';
    }

    // ==================== 测试数据（复用 TemplateEngineRenderTest） ====================

    private Map<String, Object> buildFullData() {
        Map<String, Object> data = new HashMap<>();
        data.put("clientName", "你好我好大家好单位");
        data.put("reportDate", "2026年4月10日");
        data.put("periodStart", "2026年3月30日00时00分00秒");
        data.put("periodEnd", "2026年4月05日23时59分59秒");
        data.put("topOutboundIndustry", "教育");
        data.put("topOutboundCount", "12345");
        data.put("outboundIndustryList", "电信、经营性公众互联网、能源");
        data.put("topOutboundThreat", "银狐");
        data.put("topInboundCount", "8888");
        data.put("unitOutboundTotal", "63712");
        data.put("unitOutboundChangeDir", "下降");
        data.put("unitInboundPeakCount", "178000");

        // 图片
        data.put("logoImg", new Image("src/test/resources/images/chart1.png", 200, 80));

        // 列表
        List<Map<String, Object>> riskList = new ArrayList<>();
        Map<String, Object> r1 = new HashMap<>();
        r1.put("name", "远控木马风险");
        r1.put("status", "已处置");
        r1.put("externalIoc", "192.163.162.76:30038");
        riskList.add(r1);
        Map<String, Object> r2 = new HashMap<>();
        r2.put("name", "后门程序风险");
        r2.put("status", "处置中");
        riskList.add(r2);
        Map<String, Object> r3 = new HashMap<>();
        r3.put("name", "间谍软件风险");
        r3.put("status", "待处置");
        riskList.add(r3);
        data.put("riskList", riskList);

        List<Map<String, Object>> assetList = new ArrayList<>();
        Map<String, Object> a1 = new HashMap<>();
        a1.put("ip", "10.0.74.78");
        a1.put("ioc", "www.jsfp.org.cn");
        assetList.add(a1);
        Map<String, Object> a2 = new HashMap<>();
        a2.put("ip", "10.0.74.80");
        a2.put("ioc", "www.evil.com");
        assetList.add(a2);
        data.put("assetList", assetList);

        data.put("th_name", "线索名称");
        data.put("th_status", "处置情况");

        return data;
    }
}
