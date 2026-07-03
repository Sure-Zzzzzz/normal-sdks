package io.github.surezzzzzz.sdk.template.doc.test.cases;

import io.github.surezzzzzz.sdk.template.doc.engine.TemplateEngine;
import io.github.surezzzzzz.sdk.template.doc.exception.DocxToPdfFailedException;
import io.github.surezzzzzz.sdk.template.doc.model.Image;
import io.github.surezzzzzz.sdk.template.doc.support.PdfConvertHelper;
import io.github.surezzzzzz.sdk.template.doc.test.SimpleDocTemplateTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PdfConvertHelper 测试（1.1.1）
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleDocTemplateTestApplication.class)
@DisplayName("PdfConvertHelper 测试")
class PdfConvertHelperTest {

    @Autowired
    private PdfConvertHelper pdfConvertHelper;

    @Autowired
    private TemplateEngine templateEngine;

    private static final Path DOCX_DIR = Paths.get("build/test-output/docx");
    private static final Path PDF_DIR = Paths.get("build/test-output/pdf");

    private byte[] docxBytes;

    @BeforeEach
    void setUp() throws Exception {
        Files.createDirectories(DOCX_DIR);
        Files.createDirectories(PDF_DIR);
        docxBytes = buildDocxBytes();
    }

    // ==================== DOCX bytes → PDF bytes ====================

    @Test
    @DisplayName("fromDocx：byte[] → byte[] 返回有效 PDF")
    void fromDocxBytesReturnsPdf() {
        byte[] pdfBytes = pdfConvertHelper.fromDocx(docxBytes);

        log.info("fromDocx byte[] → byte[] 验证，大小: {} bytes", pdfBytes != null ? pdfBytes.length : 0);
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 2048, "PDF 应大于 2KB");
        assertTrue(isPdfFile(pdfBytes), "PDF 文件头应为 %PDF");
    }

    // ==================== DOCX InputStream → PDF bytes ====================

    @Test
    @DisplayName("fromDocx：InputStream → byte[] 返回有效 PDF")
    void fromDocxInputStreamReturnsPdf() throws Exception {
        try (InputStream is = new ByteArrayInputStream(docxBytes)) {
            byte[] pdfBytes = pdfConvertHelper.fromDocx(is);

            log.info("fromDocx InputStream → byte[] 验证，大小: {} bytes", pdfBytes != null ? pdfBytes.length : 0);
            assertNotNull(pdfBytes);
            assertTrue(pdfBytes.length > 2048);
            assertTrue(isPdfFile(pdfBytes));
        }
    }

    // ==================== DOCX Path → PDF bytes ====================

    @Test
    @DisplayName("fromDocx：Path → byte[] 返回有效 PDF")
    void fromDocxPathReturnsPdf() throws Exception {
        // 先写出 DOCX 文件
        Path docxPath = DOCX_DIR.resolve("pdf-convert-test.docx");
        Files.write(docxPath, docxBytes);

        byte[] pdfBytes = pdfConvertHelper.fromDocx(docxPath);

        log.info("fromDocx Path → byte[] 验证，大小: {} bytes", pdfBytes != null ? pdfBytes.length : 0);
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 2048);
        assertTrue(isPdfFile(pdfBytes));
    }

    // ==================== DOCX File → PDF bytes ====================

    @Test
    @DisplayName("fromDocx：File → byte[] 返回有效 PDF")
    void fromDocxFileReturnsPdf() throws Exception {
        Path docxPath = DOCX_DIR.resolve("pdf-convert-file-test.docx");
        Files.write(docxPath, docxBytes);

        byte[] pdfBytes = pdfConvertHelper.fromDocx(docxPath.toFile());

        log.info("fromDocx File → byte[] 验证，大小: {} bytes", pdfBytes != null ? pdfBytes.length : 0);
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 2048);
        assertTrue(isPdfFile(pdfBytes));
    }

    // ==================== DOCX bytes → PDF OutputStream ====================

    @Test
    @DisplayName("fromDocx：byte[] → OutputStream 写出有效 PDF")
    void fromDocxBytesToStream() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        pdfConvertHelper.fromDocx(docxBytes, baos);

        byte[] pdfBytes = baos.toByteArray();
        log.info("fromDocx byte[] → OutputStream 验证，大小: {} bytes", pdfBytes.length);
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 2048);
        assertTrue(isPdfFile(pdfBytes));
    }

    // ==================== DOCX InputStream → PDF OutputStream ====================

    @Test
    @DisplayName("fromDocx：InputStream → OutputStream 写出有效 PDF")
    void fromDocxStreamToStream() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (InputStream is = new ByteArrayInputStream(docxBytes)) {
            pdfConvertHelper.fromDocx(is, baos);
        }

        byte[] pdfBytes = baos.toByteArray();
        log.info("fromDocx InputStream → OutputStream 验证，大小: {} bytes", pdfBytes.length);
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 2048);
        assertTrue(isPdfFile(pdfBytes));
    }

    // ==================== DOCX Path → PDF Path ====================

    @Test
    @DisplayName("fromDocx：Path → Path 生成有效 PDF 文件")
    void fromDocxPathToPath() throws Exception {
        Path docxPath = DOCX_DIR.resolve("source-for-pdf-path.docx");
        Path pdfPath = PDF_DIR.resolve("output-from-path-to-path.pdf");
        Files.write(docxPath, docxBytes);

        pdfConvertHelper.fromDocx(docxPath, pdfPath);

        assertTrue(Files.exists(pdfPath), "PDF 文件应存在");
        byte[] pdfBytes = Files.readAllBytes(pdfPath);
        log.info("fromDocx Path → Path 验证，大小: {} bytes", pdfBytes.length);
        assertTrue(pdfBytes.length > 2048);
        assertTrue(isPdfFile(pdfBytes));
    }

    // ==================== DOCX File → PDF File ====================

    @Test
    @DisplayName("fromDocx：File → File 生成有效 PDF 文件")
    void fromDocxFileToFile() throws Exception {
        Path docxPath = DOCX_DIR.resolve("source-for-pdf-file.docx");
        Path pdfPath = PDF_DIR.resolve("output-from-file-to-file.pdf");
        Files.write(docxPath, docxBytes);

        pdfConvertHelper.fromDocx(docxPath.toFile(), pdfPath.toFile());

        assertTrue(Files.exists(pdfPath), "PDF 文件应存在");
        byte[] pdfBytes = Files.readAllBytes(pdfPath);
        log.info("fromDocx File → File 验证，大小: {} bytes", pdfBytes.length);
        assertTrue(pdfBytes.length > 2048);
        assertTrue(isPdfFile(pdfBytes));
    }

    // ==================== 异常场景 ====================

    // ==================== 异常场景 ====================

    @Test
    @DisplayName("fromDocx：无效字节抛 DocxToPdfFailedException")
    void fromDocxInvalidBytes() {
        byte[] invalidBytes = "not a docx".getBytes();

        DocxToPdfFailedException ex = assertThrows(
                DocxToPdfFailedException.class,
                () -> pdfConvertHelper.fromDocx(invalidBytes)
        );
        log.info("fromDocx 无效字节异常: errorCode={}, message={}",
                ex.getErrorCode(), ex.getMessage());
        assertNotNull(ex.getErrorCode());
    }

    @Test
    @DisplayName("fromDocx：Path 不存在抛 DocxToPdfFailedException")
    void fromDocxNonexistentPathThrowsException() {
        Path nonexistentPath = DOCX_DIR.resolve("nonexistent-file-12345.docx");

        DocxToPdfFailedException ex = assertThrows(
                DocxToPdfFailedException.class,
                () -> pdfConvertHelper.fromDocx(nonexistentPath)
        );
        log.info("fromDocx Path 不存在异常: errorCode={}, message={}",
                ex.getErrorCode(), ex.getMessage());
        assertNotNull(ex.getErrorCode());
    }

    @Test
    @DisplayName("fromDocx：File 不存在抛 DocxToPdfFailedException")
    void fromDocxNonexistentFileThrowsException() {
        File nonexistentFile = DOCX_DIR.resolve("nonexistent-file-67890.docx").toFile();

        DocxToPdfFailedException ex = assertThrows(
                DocxToPdfFailedException.class,
                () -> pdfConvertHelper.fromDocx(nonexistentFile)
        );
        log.info("fromDocx File 不存在异常: errorCode={}, message={}",
                ex.getErrorCode(), ex.getMessage());
        assertNotNull(ex.getErrorCode());
    }

    // ==================== 工具方法 ====================

    private boolean isPdfFile(byte[] bytes) {
        if (bytes == null || bytes.length < 4) return false;
        return bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F';
    }

    private byte[] buildDocxBytes() {
        Map<String, Object> data = buildTestData();
        return templateEngine.renderToBytes(
                "classpath:templates/weekly-report-template.docx", data);
    }

    private Map<String, Object> buildTestData() {
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
        data.put("logoImg", new Image("src/test/resources/images/chart1.png", 200, 80));

        List<Map<String, Object>> riskList = new ArrayList<>();
        Map<String, Object> r1 = new HashMap<>();
        r1.put("name", "远控木马风险");
        r1.put("status", "已处置");
        riskList.add(r1);
        data.put("riskList", riskList);

        List<Map<String, Object>> assetList = new ArrayList<>();
        Map<String, Object> a1 = new HashMap<>();
        a1.put("ip", "10.0.74.78");
        a1.put("ioc", "www.jsfp.org.cn");
        assetList.add(a1);
        data.put("assetList", assetList);

        data.put("th_name", "线索名称");
        data.put("th_status", "处置情况");

        return data;
    }
}
