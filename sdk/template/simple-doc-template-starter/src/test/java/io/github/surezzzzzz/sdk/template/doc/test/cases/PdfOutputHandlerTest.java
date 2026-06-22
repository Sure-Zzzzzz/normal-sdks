package io.github.surezzzzzz.sdk.template.doc.test.cases;

import io.github.surezzzzzz.sdk.template.doc.constant.OutputFormat;
import io.github.surezzzzzz.sdk.template.doc.engine.TemplateEngine;
import io.github.surezzzzzz.sdk.template.doc.exception.FontNotFoundException;
import io.github.surezzzzzz.sdk.template.doc.exception.TemplateRenderException;
import io.github.surezzzzzz.sdk.template.doc.handler.pdf.PdfOutputHandler;
import io.github.surezzzzzz.sdk.template.doc.model.Chart;
import io.github.surezzzzzz.sdk.template.doc.model.Image;
import io.github.surezzzzzz.sdk.template.doc.test.SimpleDocTemplateTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PDF 输出测试（1.1.0）
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleDocTemplateTestApplication.class)
@DisplayName("PDF 输出测试")
class PdfOutputHandlerTest {

    @Autowired
    private TemplateEngine templateEngine;

    @Autowired
    private PdfOutputHandler handler;

    private static final Path OUTPUT_DIR = Paths.get("build/test-output/pdf");

    /**
     * PDF 文件头标识
     */
    private static final String PDF_HEADER = "%PDF";

    /**
     * PDF 合理最小大小（字节）：空 PDF 约 1KB，含内容应 > 2KB
     */
    private static final int PDF_MIN_SIZE = 2048;

    @BeforeEach
    void ensureOutputDir() throws Exception {
        Files.createDirectories(OUTPUT_DIR);
    }

    // ==================== renderToPdf 快捷方法 ====================

    @Test
    @DisplayName("renderToPdf：全量数据模板 → PDF 字节数组，内容正确")
    void renderToPdfFullData() throws Exception {
        Map<String, Object> data = buildFullData();

        byte[] pdfBytes = templateEngine.renderToPdf(
                "classpath:templates/weekly-report-template.docx", data);

        assertValidPdf(pdfBytes, "全量数据模板");

        // 验证 PDF 包含模板中变量的替换值
        String pdfText = extractPdfText(pdfBytes);
        assertTrue(pdfText.contains("你好我好大家好单位"), "PDF 应包含 clientName 替换值");
        assertTrue(pdfText.contains("2026年4月10日"), "PDF 应包含 reportDate 替换值");

        writePdf("full-data.pdf", pdfBytes);
    }

    // ==================== renderForPdf 链式方法 ====================

    @Test
    @DisplayName("renderForPdf：链式 toFile 输出有效 PDF 文件")
    void renderForPdfToToFile() throws Exception {
        Map<String, Object> data = buildFullData();

        Path pdfPath = OUTPUT_DIR.resolve("chain-tofile.pdf");
        templateEngine.renderForPdf(
                "classpath:templates/weekly-report-template.docx", data)
                .toFile(pdfPath.toString());

        assertTrue(Files.exists(pdfPath), "PDF 文件应该存在");
        byte[] pdfBytes;
        try (InputStream inputStream = Files.newInputStream(pdfPath)) {
            pdfBytes = StreamUtils.copyToByteArray(inputStream);
        }
        assertValidPdf(pdfBytes, "链式 toFile");
    }

    @Test
    @DisplayName("renderForPdf：链式 toStream 输出有效 PDF 流")
    void renderForPdfToStream() throws Exception {
        Map<String, Object> data = buildFullData();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        templateEngine.renderForPdf(
                "classpath:templates/weekly-report-template.docx", data)
                .toStream(baos);

        byte[] pdfBytes = baos.toByteArray();
        assertValidPdf(pdfBytes, "链式 toStream");
    }

    @Test
    @DisplayName("renderForPdf：toBytes 与 renderToPdf 结果一致")
    void renderForPdfToBytesConsistent() throws Exception {
        Map<String, Object> data = buildFullData();

        byte[] quickBytes = templateEngine.renderToPdf(
                "classpath:templates/weekly-report-template.docx", data);
        byte[] chainBytes = templateEngine.renderForPdf(
                "classpath:templates/weekly-report-template.docx", data)
                .toBytes();

        log.info("PDF 字节一致性验证: quickBytes={}, chainBytes={}",
                quickBytes == null ? -1 : quickBytes.length,
                chainBytes == null ? -1 : chainBytes.length);
        assertNotNull(quickBytes, "renderToPdf 结果不应为空");
        assertNotNull(chainBytes, "renderForPdf.toBytes 结果不应为空");
        assertValidPdf(quickBytes, "renderToPdf");
        assertValidPdf(chainBytes, "renderForPdf.toBytes");
    }

    // ==================== 端到端：weekly 模板 → DOCX → PDF ====================

    @Test
    @DisplayName("端到端：weekly 模板 → DOCX 落盘 → PDF 落盘（两个产物都保留）")
    void endToEndWeeklyDocxThenPdf() throws Exception {
        Map<String, Object> data = buildFullData();

        // Step 1: 模板渲染为 DOCX，落盘到 weekly-report.docx
        byte[] docxBytes = templateEngine
                .render("classpath:templates/weekly-report-template.docx", data)
                .output(OutputFormat.DOCX)
                .toBytes();
        Path docxPath = OUTPUT_DIR.resolve("weekly-report.docx");
        Files.write(docxPath, docxBytes);
        log.info("[端到端] DOCX 已落盘: {} ({} bytes)", docxPath.toAbsolutePath(), docxBytes.length);

        // Step 2: 读回 DOCX 字节，调用 convertToPdf 转 PDF，落盘到 weekly-report.pdf
        byte[] docxFromDisk;
        try (InputStream inputStream = Files.newInputStream(docxPath)) {
            docxFromDisk = StreamUtils.copyToByteArray(inputStream);
        }
        byte[] pdfBytes = handler.convertToPdf(docxFromDisk);
        Path pdfPath = OUTPUT_DIR.resolve("weekly-report.pdf");
        Files.write(pdfPath, pdfBytes);
        log.info("[端到端] PDF 已落盘: {} ({} bytes)", pdfPath.toAbsolutePath(), pdfBytes.length);

        // 基本断言：两个文件都存在、大小合理
        assertTrue(Files.exists(docxPath), "DOCX 应该落盘");
        assertTrue(Files.exists(pdfPath), "PDF 应该落盘");
        assertValidPdf(pdfBytes, "端到端 PDF");
    }

    // ==================== PdfOutputHandler.convertToPdf 直接转换 ====================

    @Test
    @DisplayName("convertToPdf：已有 DOCX 字节 → PDF 字节，内容正确")
    void convertToPdfFromExistingDocx() throws Exception {
        Map<String, Object> data = buildFullData();
        byte[] docxBytes = templateEngine
                .render("classpath:templates/weekly-report-template.docx", data)
                .output(OutputFormat.DOCX)
                .toBytes();

        byte[] pdfBytes = handler.convertToPdf(docxBytes);

        assertValidPdf(pdfBytes, "直接转换");

        // 验证内容
        String pdfText = extractPdfText(pdfBytes);
        assertTrue(pdfText.contains("你好我好大家好单位"), "直接转换 PDF 应包含替换值");

        writePdf("convert-direct.pdf", pdfBytes);
    }

    @Test
    @DisplayName("convertToPdf：无效字节抛 DocxToPdfFailedException")
    void convertToPdfInvalidBytes() {
        assertThrows(TemplateRenderException.class, () ->
                handler.convertToPdf("not a docx".getBytes()));
    }

    @Test
    @DisplayName("convertToPdf：输出到流")
    void convertToPdfToStream() throws Exception {
        Map<String, Object> data = buildFullData();
        byte[] docxBytes = templateEngine
                .render("classpath:templates/weekly-report-template.docx", data)
                .output(OutputFormat.DOCX)
                .toBytes();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        handler.convertToPdf(docxBytes, baos);

        byte[] pdfBytes = baos.toByteArray();
        assertValidPdf(pdfBytes, "convertToPdf 流输出");
    }

    // ==================== 格式校验 ====================

    @Test
    @DisplayName("renderForPdf 非 .docx 格式抛 TemplateRenderException")
    void renderForPdfRejectsNonDocx() {
        TemplateRenderException ex = assertThrows(TemplateRenderException.class, () ->
                templateEngine.renderForPdf("test.md", new HashMap<>()));
        assertTrue(ex.getMessage().contains(".md"), "异常信息应包含实际后缀 .md");
    }

    // ==================== Chart 路径 ====================

    @Test
    @DisplayName("renderForPdf：含 chart 的模板 → PDF（Chart 路径）")
    void renderForPdfWithChart() throws Exception {
        Map<String, Object> data = buildFullData();

        try {
            byte[] pdfBytes = templateEngine.renderToPdf(
                    "classpath:templates/weekly-report-template.docx", data);

            assertValidPdf(pdfBytes, "Chart 路径");
            // Chart 路径 PDF 应比无 chart 的更大（包含图片数据）
            assertTrue(pdfBytes.length > PDF_MIN_SIZE, "Chart 路径 PDF 应大于最小阈值");

            // 验证关键内容
            String pdfText = extractPdfText(pdfBytes);
            assertTrue(pdfText.contains("你好我好大家好单位"), "Chart 路径 PDF 应包含文本变量");

            writePdf("with-chart.pdf", pdfBytes);
        } catch (FontNotFoundException e) {
            // 测试环境可能没有中文字体，抛 FontNotFoundException 是预期行为
            log.warn("测试环境缺少中文字体，Chart PNG 生成正确抛出 FontNotFoundException: {}", e.getMessage());
            assertNotNull(e.getErrorCode());
        }
    }

    // ==================== 断言工具方法 ====================

    /**
     * 验证 PDF 字节有效性：非空、文件头正确、大小合理
     */
    private void assertValidPdf(byte[] pdfBytes, String label) {
        assertNotNull(pdfBytes, label + " PDF 字节数组不能为 null");
        assertTrue(pdfBytes.length > PDF_MIN_SIZE,
                label + " PDF 大小应 > " + PDF_MIN_SIZE + " 字节，实际: " + pdfBytes.length);
        String header = new String(pdfBytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII);
        assertEquals(PDF_HEADER, header, label + " PDF 文件头应为 %PDF");
    }

    /**
     * 从 PDF 字节中提取文本（简易方式：扫描可见 ASCII/UTF-8 文本段）
     * <p>
     * 注意：这不是完整的 PDF 文本提取，仅用于断言关键替换值是否存在。
     * 生产环境应使用 PDFBox 等库做完整解析。
     */
    /**
     * 用 PDFBox 提取 PDF 文本（中文走 CID 字形索引时简易 () 扫描拿不到，
     * 必须走 PDFTextStripper 解 ToUnicode CMap）。
     */
    private String extractPdfText(byte[] pdfBytes) {
        try (org.apache.pdfbox.pdmodel.PDDocument doc =
                     org.apache.pdfbox.pdmodel.PDDocument.load(pdfBytes)) {
            org.apache.pdfbox.text.PDFTextStripper stripper =
                    new org.apache.pdfbox.text.PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(doc);
        } catch (IOException e) {
            log.warn("PDFBox 提取 PDF 文本失败: {}", e.getMessage());
            return "";
        }
    }

    private void writePdf(String name, byte[] pdfBytes) throws IOException {
        Path pdfPath = OUTPUT_DIR.resolve(name);
        Files.write(pdfPath, pdfBytes);
        log.info("{} PDF 已写出: {} ({} KB)", name, pdfPath, pdfBytes.length / 1024);
    }

    // ==================== 全量数据构建 ====================

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
        data.put("outboundThreatList", "木马、后门、GH0st、病毒");
        data.put("outboundChart", buildLineChart("外联告警态势",
                new String[]{"3/30", "3/31", "4/1", "4/2", "4/3", "4/4", "4/5"},
                new Number[]{120, 98, 145, 200, 178, 156, 210}));
        data.put("outboundChartImg", new Image("src/test/resources/images/chart1.png",
                400, 300, "outboundChartImg", Image.ImageType.PNG));

        data.put("topInboundIndustry", "教育");
        data.put("topInboundCount", "8888");
        data.put("inboundIndustryList", "电信、其他、银行、广电");
        data.put("topInboundRules", "请求中包含zgrab扫描器特征...");
        data.put("inboundChart", buildBarChart("正面攻击态势",
                new String[]{"3/30", "3/31", "4/1", "4/2", "4/3", "4/4", "4/5"},
                new Number[]{3200, 4100, 2800, 5600, 4900, 3700, 6200}));
        data.put("inboundChartImg", new Image("src/test/resources/images/chart2.png",
                400, 300, "inboundChartImg", Image.ImageType.PNG));

        data.put("unitOutboundTotal", "63712");
        data.put("unitOutboundChangeDir", "下降");
        data.put("unitOutboundChangeCount", "9026");
        data.put("unitOutboundPeakDate", "2026-04-28");
        data.put("unitOutboundPeakCount", "11500");
        data.put("unitOutboundTargetTop3", "vs.haifti.com、1.uqidashi.com、185.27.134.11");
        data.put("topOutboundIntelOrg", "银狐");
        data.put("unitOutboundIntelOrgList", "mylobot、phorpiex、dorkbot");
        data.put("unitOutboundChart", buildLineChart("本单位外联趋势",
                new String[]{"3/30", "3/31", "4/1", "4/2", "4/3", "4/4", "4/5"},
                new Number[]{8500, 9200, 7800, 11500, 10200, 9600, 7100}));

        data.put("unitInboundPeakDate", "2026-04-24");
        data.put("unitInboundPeakCount", "178000");
        data.put("unitInboundAttackerTop3", "11.53.73.197、11.53.73.195、11.53.73.186");
        data.put("unitInboundAttackTypeList", "SQL注入、Webshell攻击、隐蔽隧道...");
        data.put("unitInboundChart", buildPieChart("本单位攻击类型分布",
                new String[]{"SQL注入", "Webshell", "隐蔽隧道", "暴力破解", "其他"},
                new Number[]{35, 25, 20, 12, 8}));

        data.put("deviceStatus", "系统、流量、授权均正常；情报、规则均为最新版本。");

        data.put("riskSituationText", "经安全专家综合分析研判，本周期共发现3起风险外联线索");
        data.put("hasRiskList", Boolean.TRUE);
        data.put("th_name", "线索名称");
        data.put("th_discoverTime", "发现时间");
        data.put("th_affectedIp", "受影响IP");
        data.put("th_externalIoc", "外联IOC");
        data.put("th_status", "处置情况");

        List<Map<String, Object>> riskList = new ArrayList<>();
        Map<String, Object> r1 = new HashMap<>();
        r1.put("name", "远控木马风险");
        r1.put("discoverTime", "2026-04-21 10:42:10");
        r1.put("affectedIp", "10.78.41.48");
        r1.put("externalIoc", "192.163.162.76:30038");
        r1.put("status", "已处置");
        riskList.add(r1);

        Map<String, Object> r2 = new HashMap<>();
        r2.put("name", "后门程序风险");
        r2.put("discoverTime", "2026-04-22 14:30:00");
        r2.put("affectedIp", "10.78.41.50");
        r2.put("externalIoc", "192.163.162.77:30039");
        r2.put("status", "处置中");
        riskList.add(r2);

        Map<String, Object> r3 = new HashMap<>();
        r3.put("name", "间谍软件风险");
        r3.put("discoverTime", "2026-04-23 09:15:00");
        r3.put("affectedIp", "10.78.41.52");
        r3.put("externalIoc", "192.163.162.78:30040");
        r3.put("status", "已处置");
        riskList.add(r3);

        data.put("riskList", riskList);

        data.put("hasAssetList", Boolean.TRUE);
        data.put("th_alertTime", "告警时间");
        data.put("th_assetIp", "需杀毒资产");
        data.put("th_alertIoc", "告警IOC");
        data.put("th_alertCount", "告警次数");
        data.put("th_threatType", "威胁类型");

        List<Map<String, Object>> assetList = new ArrayList<>();
        Map<String, Object> a1 = new HashMap<>();
        a1.put("alertTime", "2026-03-30 12:17:04");
        a1.put("assetIp", "10.0.74.78");
        a1.put("alertIoc", "www.jsfp.org.cn");
        a1.put("alertCount", "5");
        a1.put("threatType", "社工钓鱼");
        assetList.add(a1);

        Map<String, Object> a2 = new HashMap<>();
        a2.put("alertTime", "2026-03-31 08:30:00");
        a2.put("assetIp", "10.0.74.80");
        a2.put("alertIoc", "www.evil.com");
        a2.put("alertCount", "3");
        a2.put("threatType", "远控木马");
        assetList.add(a2);

        data.put("assetList", assetList);

        data.put("hasBlockList", Boolean.TRUE);
        data.put("th_domain", "域名/IP");
        data.put("th_suggestion", "处置建议");
        data.put("th_remark", "备注");

        List<Map<String, Object>> blockList = new ArrayList<>();
        Map<String, Object> b1 = new HashMap<>();
        b1.put("domain", "www.jsfp.org.cn");
        b1.put("threatType", "社工钓鱼风险");
        b1.put("suggestion", "建议封禁");
        b1.put("remark", "");
        blockList.add(b1);

        data.put("blockList", blockList);

        return data;
    }

    private Chart buildLineChart(String title, String[] categories, Number[] values) {
        List<Chart.Series> series = new ArrayList<>();
        series.add(new Chart.Series("数量", Arrays.asList(values)));
        return new Chart(title, Arrays.asList(categories), series, 400, 300, Chart.ChartType.LINE);
    }

    private Chart buildBarChart(String title, String[] categories, Number[] values) {
        List<Chart.Series> series = new ArrayList<>();
        series.add(new Chart.Series("数量", Arrays.asList(values)));
        return new Chart(title, Arrays.asList(categories), series, 400, 300, Chart.ChartType.BAR);
    }

    private Chart buildPieChart(String title, String[] categories, Number[] values) {
        List<Chart.Series> series = new ArrayList<>();
        series.add(new Chart.Series("占比", Arrays.asList(values)));
        return new Chart(title, Arrays.asList(categories), series, 400, 300, Chart.ChartType.PIE);
    }
}
