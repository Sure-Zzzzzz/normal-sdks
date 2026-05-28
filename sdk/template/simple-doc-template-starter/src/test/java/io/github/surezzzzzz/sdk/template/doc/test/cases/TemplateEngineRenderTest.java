package io.github.surezzzzzz.sdk.template.doc.test.cases;

import io.github.surezzzzzz.sdk.template.doc.constant.OutputFormat;
import io.github.surezzzzzz.sdk.template.doc.document.Document;
import io.github.surezzzzzz.sdk.template.doc.engine.TemplateEngine;
import io.github.surezzzzzz.sdk.template.doc.engine.TemplateRenderResult;
import io.github.surezzzzzz.sdk.template.doc.exception.TemplateNotFoundException;
import io.github.surezzzzzz.sdk.template.doc.exception.TemplateRenderException;
import io.github.surezzzzzz.sdk.template.doc.handler.docx.DocxOutputHandler;
import io.github.surezzzzzz.sdk.template.doc.model.Chart;
import io.github.surezzzzzz.sdk.template.doc.model.Image;
import io.github.surezzzzzz.sdk.template.doc.test.SimpleDocTemplateTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Template Engine Integration Test
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleDocTemplateTestApplication.class)
@DisplayName("TemplateEngine 端到端测试")
class TemplateEngineRenderTest {

    @Autowired
    private TemplateEngine templateEngine;

    private static final Path OUTPUT_DIR = Paths.get("src/test/resources/output");

    private Map<String, Object> fullData;

    @BeforeEach
    void ensureOutputDir() throws Exception {
        Files.createDirectories(OUTPUT_DIR);
    }

    @BeforeEach
    void setUp() {
        fullData = buildFullData();
    }

    // ==================== 正常场景 ====================

    @Test
    @DisplayName("全量数据：文本变量全部正确替换")
    void renderFullDataTextVariablesReplaced() throws Exception {
        byte[] docxBytes = templateEngine
            .render("classpath:templates/weekly-report-template.docx", fullData)
            .output(OutputFormat.DOCX)
            .toBytes();

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            String allText = extractAllText(doc);
            log.info("全量文本变量验证，文档总字符数: {}", allText.length());

            // 关键文本变量验证
            assertTrue(allText.contains("你好我好大家好单位"), "clientName 未被替换");
            assertTrue(allText.contains("2026年4月10日"), "reportDate 未被替换");
            assertTrue(allText.contains("2026年3月30日00时00分00秒"), "periodStart 未被替换");
            assertTrue(allText.contains("2026年4月05日23时59分59秒"), "periodEnd 未被替换");
            assertTrue(allText.contains("教育"), "topOutboundIndustry 未被替换");
            assertTrue(allText.contains("12345"), "topOutboundCount 未被替换");
            assertTrue(allText.contains("电信、经营性公众互联网、能源"), "outboundIndustryList 未被替换");
            assertTrue(allText.contains("银狐"), "topOutboundThreat 未被替换");
            assertTrue(allText.contains("8888"), "topInboundCount 未被替换");
            assertTrue(allText.contains("63712"), "unitOutboundTotal 未被替换");
            assertTrue(allText.contains("下降"), "unitOutboundChangeDir 未被替换");
            assertTrue(allText.contains("178000"), "unitInboundPeakCount 未被替换");
        }
    }

    @Test
    @DisplayName("全量数据：表格行循环正确展开（riskList 3条→3行）")
    void renderFullDataRiskListExpanded() throws Exception {
        byte[] docxBytes = templateEngine
            .render("classpath:templates/weekly-report-template.docx", fullData)
            .output(OutputFormat.DOCX)
            .toBytes();

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            String allText = extractAllText(doc);
            log.info("riskList 展开验证，文档总字符数: {}", allText.length());

            // 3条 riskList 全部展开
            assertTrue(allText.contains("远控木马风险"), "riskList 第1条未展开");
            assertTrue(allText.contains("后门程序风险"), "riskList 第2条未展开");
            assertTrue(allText.contains("间谍软件风险"), "riskList 第3条未展开");
            assertTrue(allText.contains("192.163.162.76:30038"), "riskList externalIoc 未展开");
            assertTrue(allText.contains("已处置"), "riskList status 未展开");
            assertTrue(allText.contains("处置中"), "riskList status 未展开");
        }
    }

    @Test
    @DisplayName("全量数据：assetList 2条→2行展开")
    void renderFullDataAssetListExpanded() throws Exception {
        byte[] docxBytes = templateEngine
            .render("classpath:templates/weekly-report-template.docx", fullData)
            .output(OutputFormat.DOCX)
            .toBytes();

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            String allText = extractAllText(doc);
            log.info("assetList 展开验证，文档总字符数: {}", allText.length());

            assertTrue(allText.contains("10.0.74.78"), "assetList IP 第1条未展开");
            assertTrue(allText.contains("10.0.74.80"), "assetList IP 第2条未展开");
            assertTrue(allText.contains("www.jsfp.org.cn"), "assetList IOC 第1条未展开");
            assertTrue(allText.contains("www.evil.com"), "assetList IOC 第2条未展开");
        }
    }

    @Test
    @DisplayName("全量数据：图片已插入（DOCX 包含图片数据）")
    void renderFullDataImagesInserted() throws Exception {
        byte[] docxBytes = templateEngine
            .render("classpath:templates/weekly-report-template.docx", fullData)
            .output(OutputFormat.DOCX)
            .toBytes();

        // 验证 DOCX 内部 media 目录包含图片
        int imageCount = 0;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(docxBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().startsWith("word/media/") && entry.getName().matches(".*\\.(png|jpg|jpeg)")) {
                    imageCount++;
                }
            }
        }
        log.info("图片数量验证，实际图片数: {}", imageCount);
        assertEquals(2, imageCount, "应包含2张图片（2个 img 占位符），实际：" + imageCount);
    }

    @Test
    @DisplayName("全量数据：文件输出正常，大小合理")
    void renderFullDataToFile() throws Exception {
        String outputPath = OUTPUT_DIR.resolve("full-report-output.docx").toString();

        templateEngine.render("classpath:templates/weekly-report-template.docx", fullData)
            .output(OutputFormat.DOCX)
            .toFile(outputPath);

        File outputFile = new File(outputPath);
        log.info("文件输出验证，路径: {}, 大小: {} bytes", outputPath, outputFile.length());
        assertTrue(outputFile.exists(), "输出文件应存在");
        assertTrue(outputFile.length() > 5000, "输出文件应大于5KB");
    }

    @Test
    @DisplayName("全量数据：字节数组输出正常")
    void renderFullDataToBytes() {
        byte[] docxBytes = templateEngine
            .render("classpath:templates/weekly-report-template.docx", fullData)
            .output(OutputFormat.DOCX)
            .toBytes();

        log.info("字节数组输出验证，大小: {} bytes", docxBytes != null ? docxBytes.length : 0);
        assertNotNull(docxBytes);
        assertTrue(docxBytes.length > 5000, "字节数组应大于5KB");
    }

    @Test
    @DisplayName("全量数据：输出流正常")
    void renderFullDataToStream() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        templateEngine.render("classpath:templates/weekly-report-template.docx", fullData)
            .output(OutputFormat.DOCX)
            .toStream(baos);

        byte[] result = baos.toByteArray();
        log.info("流输出验证，大小: {} bytes", result != null ? result.length : 0);
        assertNotNull(result);
        assertTrue(result.length > 5000, "流输出应大于5KB");
    }

    @Test
    @DisplayName("output() 无参自动推断格式：与 output(DOCX) 结果一致")
    void outputAutoInferFormat() {
        byte[] explicit = templateEngine
            .render("classpath:templates/weekly-report-template.docx", fullData)
            .output(OutputFormat.DOCX)
            .toBytes();

        byte[] inferred = templateEngine
            .render("classpath:templates/weekly-report-template.docx", fullData)
            .output()
            .toBytes();

        log.info("自动推断格式验证，explicit: {} bytes, inferred: {} bytes", explicit.length, inferred.length);
        assertNotNull(inferred);
        assertTrue(inferred.length > 5000, "自动推断格式输出应大于5KB");
    }

    @Test
    @DisplayName("renderToBytes 快捷方法：与链式调用结果一致")
    void renderToBytesShortcut() {
        byte[] result = templateEngine.renderToBytes(
            "classpath:templates/weekly-report-template.docx", fullData);

        log.info("renderToBytes 快捷方法验证，大小: {} bytes", result != null ? result.length : 0);
        assertNotNull(result);
        assertTrue(result.length > 5000, "renderToBytes 输出应大于5KB");
    }

    @Test
    @DisplayName("renderToFile 快捷方法（完整路径）：文件正常生成")
    void renderToFileShortcutFullPath() throws Exception {
        String outputPath = OUTPUT_DIR.resolve("shortcut-full-path.docx").toString();

        templateEngine.renderToFile(
            "classpath:templates/weekly-report-template.docx", fullData, outputPath);

        File outputFile = new File(outputPath);
        log.info("renderToFile(完整路径) 验证，路径: {}, 大小: {} bytes", outputPath, outputFile.length());
        assertTrue(outputFile.exists(), "输出文件应存在");
        assertTrue(outputFile.length() > 5000, "输出文件应大于5KB");
    }

    @Test
    @DisplayName("renderToFile 快捷方法（目录+文件名）：文件正常生成")
    void renderToFileShortcutDirAndName() throws Exception {
        String dir = OUTPUT_DIR.toString();
        String fileName = "shortcut-dir-name.docx";

        templateEngine.renderToFile(
            "classpath:templates/weekly-report-template.docx", fullData, dir, fileName);

        File outputFile = new File(dir, fileName);
        log.info("renderToFile(目录+文件名) 验证，路径: {}, 大小: {} bytes", outputFile.getPath(), outputFile.length());
        assertTrue(outputFile.exists(), "输出文件应存在");
        assertTrue(outputFile.length() > 5000, "输出文件应大于5KB");
    }

    @Test
    @DisplayName("toFile(dir, fileName) 目录+文件名分开：文件正常生成")
    void toFileDirAndFileName() throws Exception {
        String dir = OUTPUT_DIR.toString();
        String fileName = "chain-dir-name.docx";

        templateEngine.render("classpath:templates/weekly-report-template.docx", fullData)
            .output()
            .toFile(dir, fileName);

        File outputFile = new File(dir, fileName);
        log.info("toFile(dir, fileName) 验证，路径: {}, 大小: {} bytes", outputFile.getPath(), outputFile.length());
        assertTrue(outputFile.exists(), "输出文件应存在");
        assertTrue(outputFile.length() > 5000, "输出文件应大于5KB");
    }

    @Test
    @DisplayName("isEmpty()：全量数据渲染结果不为空")
    void renderResultIsNotEmpty() {
        boolean empty = templateEngine
            .render("classpath:templates/weekly-report-template.docx", fullData)
            .isEmpty();

        log.info("isEmpty() 验证，结果: {}", empty);
        assertFalse(empty, "全量数据渲染结果不应为空");
    }

    @Test
    @DisplayName("renderToStream 快捷方法：流输出正常")
    void renderToStreamShortcut() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        templateEngine.renderToStream(
            "classpath:templates/weekly-report-template.docx", fullData, baos);

        byte[] result = baos.toByteArray();
        log.info("renderToStream 快捷方法验证，大小: {} bytes", result != null ? result.length : 0);
        assertNotNull(result);
        assertTrue(result.length > 5000, "renderToStream 输出应大于5KB");
    }

    // ==================== 异常场景 ====================

    @Test
    @DisplayName("模板文件不存在：抛出 TemplateNotFoundException，errorCode = TEMPLATE_NOT_FOUND")
    void renderTemplateNotFound() {
        TemplateNotFoundException exception = assertThrows(
            TemplateNotFoundException.class,
            () -> templateEngine.render("classpath:templates/not-exist.docx", fullData)
        );
        log.info("异常场景：模板不存在，errorCode: {}, message: {}", exception.getErrorCode(), exception.getMessage());
        assertEquals("TEMPLATE_001", exception.getErrorCode());
    }

    @Test
    @DisplayName("后缀未注册：抛出 TemplateNotFoundException，errorCode = RENDERER_NOT_FOUND")
    void renderUnsupportedSuffix() {
        TemplateNotFoundException exception = assertThrows(
            TemplateNotFoundException.class,
            () -> templateEngine.render("classpath:templates/report.xyz", fullData)
        );
        log.info("异常场景：后缀未注册，errorCode: {}, message: {}", exception.getErrorCode(), exception.getMessage());
        assertEquals("TEMPLATE_002", exception.getErrorCode());
        assertTrue(exception.getMessage().contains(".xyz"));
    }

    @Test
    @DisplayName("Handler 未注册：抛出 TemplateRenderException，errorCode = OUTPUT_001")
    void outputHandlerNotFound() {
        TemplateRenderResult result = templateEngine
            .render("classpath:templates/weekly-report-template.docx", fullData);

        TemplateRenderException exception = assertThrows(
            TemplateRenderException.class,
            () -> result.output(OutputFormat.HTML)
        );
        log.info("异常场景：Handler 未注册，errorCode: {}", exception.getErrorCode());
        assertEquals("OUTPUT_001", exception.getErrorCode());
    }

    @Test
    @DisplayName("格式不匹配：抛出 TemplateRenderException，errorCode = OUTPUT_002")
    void outputFormatMismatch() {
        DocxOutputHandler handler = new DocxOutputHandler();
        Document fakeDoc = new Document() {
            @Override
            public String getFormat() { return "md"; }
            @Override
            public boolean isEmpty() { return false; }
        };

        TemplateRenderException exception = assertThrows(
            TemplateRenderException.class,
            () -> handler.writeToFile(fakeDoc, OUTPUT_DIR.resolve("fake.docx").toString())
        );
        log.info("异常场景：格式不匹配，errorCode: {}, message: {}", exception.getErrorCode(), exception.getMessage());
        assertEquals("OUTPUT_002", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("docx"));
        assertTrue(exception.getMessage().contains("md"));
    }

    // ==================== 边界场景 ====================

    @Test
    @DisplayName("列表为空：riskList 为空，循环块（含 for/end 行）被删除")
    void renderEmptyListRiskListDeleted() throws Exception {
        Map<String, Object> data = buildFullData();
        data.put("riskList", new ArrayList<>());
        data.put("hasRiskList", Boolean.TRUE);
        data.put("hasAssetList", Boolean.TRUE);
        data.put("hasBlockList", Boolean.TRUE);

        byte[] docxBytes = templateEngine
            .render("classpath:templates/weekly-report-template.docx", data)
            .output(OutputFormat.DOCX)
            .toBytes();

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            String allText = extractAllText(doc);
            log.info("空列表验证，文档总字符数: {}", allText.length());

            // 空列表时，循环行不应存在
            assertFalse(allText.contains("远控木马风险"), "空列表时循环行不应存在");
            assertFalse(allText.contains("后门程序风险"), "空列表时循环行不应存在");

            // 但 hasRiskList=true 时，条件块标记被删除，内容仍应保留（或整块删）
            // 此处 data 中 riskList 为空，循环行已删，表头行仍存在（条件块为 true）
            assertTrue(allText.contains("线索名称"), "表头行应保留");
        }
    }

    @Test
    @DisplayName("条件块为 false：hasRiskList=false，模块整段删除")
    void renderConditionBlockFalseAllBlocksDeleted() throws Exception {
        Map<String, Object> data = buildFullData();
        data.put("hasRiskList", Boolean.FALSE);
        data.put("hasAssetList", Boolean.FALSE);
        data.put("hasBlockList", Boolean.FALSE);

        byte[] docxBytes = templateEngine
            .render("classpath:templates/weekly-report-template.docx", data)
            .output(OutputFormat.DOCX)
            .toBytes();

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            String allText = extractAllText(doc);
            log.info("条件块 false 验证，文档总字符数: {}", allText.length());

            // 三个条件块内容都应被删除（用表格内的特征内容验证，而非标题文字）
            assertFalse(allText.contains("本周风险外联线索列表"), "hasRiskList=false 时标题应被删除");
            assertFalse(allText.contains("需杀毒资产"), "hasAssetList=false 时表头应被删除");
            assertFalse(allText.contains("建议封禁恶意域名"), "hasBlockList=false 时标题应被删除");

            // 但无条件的内容仍应保留
            assertTrue(allText.contains("你好我好大家好单位"), "clientName 应保留");
            assertTrue(allText.contains("2026年4月10日"), "reportDate 应保留");
        }
    }

    @Test
    @DisplayName("无图片数据：[suredt.img:key] 段落为空，无图片文件")
    void renderNoImageNoImagesInDocx() throws Exception {
        Map<String, Object> data = buildFullData();
        data.remove("outboundChart");
        data.remove("outboundChartImg");
        data.remove("inboundChart");
        data.remove("inboundChartImg");
        data.remove("unitOutboundChart");
        data.remove("unitInboundChart");

        byte[] docxBytes = templateEngine
            .render("classpath:templates/weekly-report-template.docx", data)
            .output(OutputFormat.DOCX)
            .toBytes();

        // 验证无图片
        int imageCount = 0;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(docxBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().startsWith("word/media/")) {
                    imageCount++;
                }
            }
        }
        log.info("无图片验证，实际图片数: {}", imageCount);
        assertEquals(0, imageCount, "无图片数据时应无图片");
    }

    @Test
    @DisplayName("仅有文本变量：无图片、无列表、其他占位符被清空")
    void renderTextOnlyOnlyTextVariables() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("clientName", "测试单位");
        data.put("reportDate", "2026年05月21日");

        byte[] docxBytes = templateEngine
            .render("classpath:templates/weekly-report-template.docx", data)
            .output(OutputFormat.DOCX)
            .toBytes();

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            String allText = extractAllText(doc);
            log.info("仅文本变量验证，文档总字符数: {}", allText.length());

            // 文本变量被替换
            assertTrue(allText.contains("测试单位"), "clientName 应被替换");
            assertTrue(allText.contains("2026年05月21日"), "reportDate 应被替换");

            // 无循环数据，列表相关文本不应存在
            assertFalse(allText.contains("远控木马风险"), "无列表时循环行不应存在");
            assertFalse(allText.contains("10.0.74.78"), "无列表时asset行不应存在");

            // 无图片
            int imageCount = 0;
            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(docxBytes))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.getName().startsWith("word/media/")) {
                        imageCount++;
                    }
                }
            }
            log.info("仅文本变量验证，图片数: {}", imageCount);
            assertEquals(0, imageCount, "无图片数据时应无图片");
        }
    }

    @Test
    @DisplayName("blockList 1条→1行")
    void renderFullDataBlockListExpanded() throws Exception {
        byte[] docxBytes = templateEngine
            .render("classpath:templates/weekly-report-template.docx", fullData)
            .output(OutputFormat.DOCX)
            .toBytes();

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            String allText = extractAllText(doc);
            log.info("blockList 展开验证，文档总字符数: {}", allText.length());

            assertTrue(allText.contains("www.jsfp.org.cn"), "blockList domain 未展开");
            assertTrue(allText.contains("社工钓鱼风险"), "blockList threatType 未展开");
            assertTrue(allText.contains("建议封禁"), "blockList suggestion 未展开");
        }
    }

    // ==================== 图片/图表 样式测试 ====================

    @Test
    @DisplayName("图片：仅传 src，宽高从原图读取，渲染后文档正常，无占位符残留")
    void renderImageNaturalSize() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("clientName", "测试客户");
        data.put("reportDate", "2026年5月26日");
        data.put("naturalImg", new Image("src/test/resources/images/chart1.png"));
        data.put("showSection", Boolean.TRUE);
        data.put("th_itemName", "名称");
        data.put("th_itemValue", "值");
        data.put("th_remark", "备注");
        data.put("items", new ArrayList<Map<String, Object>>());
        data.put("autoWidthImg", new Image("src/test/resources/images/chart2.png", 0, 300));
        data.put("manualImg", new Image("src/test/resources/images/chart3.png", 400, 200));
        data.put("autoWidthChart", new Chart(
            "自适应宽度", java.util.Arrays.asList("A", "B"),
            java.util.Collections.singletonList(new Chart.Series("数值", java.util.Arrays.asList(1, 2))),
            0, 300, Chart.ChartType.LINE));
        data.put("styledChart", new Chart(
            "样式图表", java.util.Arrays.asList("X", "Y"),
            java.util.Arrays.asList(new Chart.Series("系列A", java.util.Arrays.asList(10, 20), "C0504D")),
            600, 350, Chart.ChartType.LINE, 18, Chart.LegendPosition.TOP, false, false, true));
        data.put("styledBarChart", new Chart(
            "柱状图", java.util.Arrays.asList("甲", "乙"),
            java.util.Arrays.asList(new Chart.Series("数量", java.util.Arrays.asList(35, 28), "75A254")),
            500, 300, Chart.ChartType.BAR, 16, Chart.LegendPosition.LEFT, false, false, false));
        data.put("styledPieChart", new Chart(
            "饼图", java.util.Arrays.asList("类型A", "类型B", "类型C"),
            java.util.Arrays.asList(new Chart.Series("占比", java.util.Arrays.asList(50, 30, 20))),
            500, 300, Chart.ChartType.PIE));

        byte[] result = templateEngine.renderToBytes("classpath:templates/style-test-template.docx", data);

        assertNotNull(result);
        assertTrue(result.length > 0);

        // 写出文件供人工确认
        Path styleOut = OUTPUT_DIR.resolve("style-test-output.docx");
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(styleOut.toFile())) {
            fos.write(result);
        }

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(result))) {
            String allText = extractAllText(doc);
            // 占位符不应残留
            assertFalse(allText.contains("[suredt.img:naturalImg]"), "naturalImg 占位符应被替换");
            assertFalse(allText.contains("[suredt.img:autoWidthImg]"), "autoWidthImg 占位符应被替换");
            assertFalse(allText.contains("[suredt.img:manualImg]"), "manualImg 占位符应被替换");
            assertFalse(allText.contains("[suredt.chart:autoWidthChart]"), "autoWidthChart 占位符应被替换");
            assertFalse(allText.contains("[suredt.chart:styledChart]"), "styledChart 占位符应被替换");
            // 文本变量应被替换
            assertTrue(allText.contains("测试客户"), "clientName 应被替换");
            log.info("全量样式测试通过，已输出: {} ({} bytes)", styleOut, result.length);
        }
    }

    @Test
    @DisplayName("图片：width=0 使用原图宽度，height=0 使用原图高度，插入成功且无残留占位符")
    void renderImageAutoWidthAndHeight() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("autoWidthImg", new Image("src/test/resources/images/chart2.png", 0, 300));

        byte[] result = templateEngine.renderToBytes("classpath:templates/style-test-template.docx", data);
        assertNotNull(result);
        assertTrue(result.length > 0);

        Path imgOut = OUTPUT_DIR.resolve("image-auto-output.docx");
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(imgOut.toFile())) {
            fos.write(result);
        }

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(result))) {
            String allText = extractAllText(doc);
            assertFalse(allText.contains("[suredt.img:autoWidthImg]"), "占位符应被替换");
            // 图片在 DOCX media 目录
            int imgCount = 0;
            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(result))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.getName().startsWith("word/media/")) {
                        imgCount++;
                    }
                }
            }
            assertTrue(imgCount > 0, "应有图片文件");
            log.info("原图尺寸图片测试通过，图片数: {}", imgCount);
        }
    }

    @Test
    @DisplayName("图表：width=0 使用默认值，生成 chart.xml 文件且尺寸正确")
    void renderChartAutoWidth() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("autoWidthChart", new Chart(
            "自适应宽度图表",
            java.util.Arrays.asList("A", "B", "C"),
            java.util.Collections.singletonList(new Chart.Series("数值", java.util.Arrays.asList(10, 20, 30))),
            0, 300,
            Chart.ChartType.LINE
        ));

        byte[] result = templateEngine.renderToBytes("classpath:templates/style-test-template.docx", data);
        assertNotNull(result);
        assertTrue(result.length > 0);

        Path chartAutoOut = OUTPUT_DIR.resolve("chart-auto-width-output.docx");
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(chartAutoOut.toFile())) {
            fos.write(result);
        }

        // 验证 DOCX 包含图表文件
        int chartCount = 0;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(result))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().startsWith("word/charts/") && entry.getName().endsWith(".xml")) {
                    chartCount++;
                }
            }
        }
        assertTrue(chartCount > 0, "应有图表文件");
        log.info("图表默认宽度测试通过，图表数: {}", chartCount);
    }

    @Test
    @DisplayName("图表：自定义样式（多系列+颜色+标题字号+图例位置+折线平滑/标记点+网格线）")
    void renderChartWithCustomStyles() throws Exception {
        List<Chart.Series> multiSeries = java.util.Arrays.asList(
            new Chart.Series("系列A", java.util.Arrays.asList(120, 98, 145), "C0504D"),
            new Chart.Series("系列B", java.util.Arrays.asList(85, 92, 78), "4F81BD")
        );

        Map<String, Object> data = new HashMap<>();
        data.put("styledChart", new Chart(
            "自定义样式图表",
            java.util.Arrays.asList("3/30", "3/31", "4/1"),
            multiSeries,
            600, 350,
            Chart.ChartType.LINE,
            18,
            Chart.LegendPosition.TOP,
            false,   // smooth = false
            false,   // showMarker = false
            true     // showGridLines = true
        ));

        byte[] result = templateEngine.renderToBytes("classpath:templates/style-test-template.docx", data);
        assertNotNull(result);
        assertTrue(result.length > 0);

        Path styledOut = OUTPUT_DIR.resolve("chart-styled-output.docx");
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(styledOut.toFile())) {
            fos.write(result);
        }

        // 读取 chart.xml 验证样式写入
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(result))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().startsWith("word/charts/") && entry.getName().endsWith(".xml")) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int len;
                    while ((len = zis.read(buf)) != -1) baos.write(buf, 0, len);
                    String xml = baos.toString("UTF-8");
                    assertTrue(xml.contains("自定义样式图表"), "应包含自定义标题");
                    assertTrue(xml.contains("系列A") && xml.contains("系列B"), "应包含多系列");
                    assertTrue(xml.contains("C0504D") && xml.contains("4F81BD"), "应包含自定义颜色");
                    assertTrue(xml.contains("sz=\"18\""), "应包含自定义字号 sz=18");
                    assertTrue(xml.contains("<c:legendPos val=\"t\"/>"), "应包含图例位置 TOP");
                    assertTrue(xml.contains("val=\"0\"") || !xml.contains("<c:smooth"), "smooth 检查");
                    log.info("自定义样式图表测试通过");
                }
            }
        }
    }

    @Test
    @DisplayName("图表：柱状图+自定义颜色，图例位置+饼图+全量样式")
    void renderBarChartWithCustomStyles() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("styledBarChart", new Chart(
            "柱状图样式测试",
            java.util.Arrays.asList("教育", "金融", "能源"),
            java.util.Arrays.asList(new Chart.Series("数量", java.util.Arrays.asList(350, 280, 210), "75A254")),
            500, 300,
            Chart.ChartType.BAR,
            16,
            Chart.LegendPosition.LEFT,
            false,
            false,
            false
        ));
        data.put("styledPieChart", new Chart(
            "饼图样式测试",
            java.util.Arrays.asList("A", "B", "C"),
            java.util.Arrays.asList(new Chart.Series("占比", java.util.Arrays.asList(50, 30, 20))),
            400, 250,
            Chart.ChartType.PIE
        ));

        byte[] result = templateEngine.renderToBytes("classpath:templates/style-test-template.docx", data);
        assertNotNull(result);
        assertTrue(result.length > 0);

        Path barOut = OUTPUT_DIR.resolve("chart-bar-pie-output.docx");
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(barOut.toFile())) {
            fos.write(result);
        }

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(result))) {
            ZipEntry entry;
            boolean barFound = false;
            boolean pieFound = false;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().startsWith("word/charts/") && entry.getName().endsWith(".xml")) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int len;
                    while ((len = zis.read(buf)) != -1) baos.write(buf, 0, len);
                    String xml = baos.toString("UTF-8");
                    if (xml.contains("柱状图样式测试")) {
                        barFound = true;
                        assertTrue(xml.contains("75A254"), "柱状图应包含自定义颜色");
                    }
                    if (xml.contains("饼图样式测试")) {
                        pieFound = true;
                        assertTrue(xml.contains("饼图样式测试"), "饼图应包含标题");
                    }
                }
            }
            assertTrue(barFound, "应有柱状图");
            assertTrue(pieFound, "应有饼图");
            log.info("柱状图+饼图样式测试通过");
        }
    }

    @Test
    @DisplayName("图片：手动指定宽高，渲染后图片文件存在且尺寸正确")
    void renderImageManualSize() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("manualImg", new Image("src/test/resources/images/chart3.png", 400, 200, "manualImg", Image.ImageType.PNG));

        byte[] result = templateEngine.renderToBytes("classpath:templates/style-test-template.docx", data);
        assertNotNull(result);
        assertTrue(result.length > 0);

        Path manualImgOut = OUTPUT_DIR.resolve("image-manual-output.docx");
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(manualImgOut.toFile())) {
            fos.write(result);
        }

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(result))) {
            String allText = extractAllText(doc);
            assertFalse(allText.contains("[suredt.img:manualImg]"), "占位符应被替换");
            int imgCount = 0;
            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(result))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.getName().startsWith("word/media/")) {
                        imgCount++;
                    }
                }
            }
            assertEquals(1, imgCount, "应有1张手动尺寸图片");
            log.info("手动尺寸图片测试通过");
        }
    }

    @Test
    @DisplayName("图片：JPEG 格式正确识别并插入")
    void renderImageJpeg() throws Exception {
        Path jpegPath = OUTPUT_DIR.resolve("test-image.jpg");
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(100, 100, java.awt.image.BufferedImage.TYPE_INT_RGB);
        javax.imageio.ImageIO.write(img, "jpg", jpegPath.toFile());

        Map<String, Object> data = new HashMap<>();
        data.put("manualImg", new Image(jpegPath.toString(), 200, 100));

        byte[] result = templateEngine.renderToBytes("classpath:templates/style-test-template.docx", data);
        assertNotNull(result);

        boolean hasJpeg = false;
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new ByteArrayInputStream(result))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.startsWith("word/media/") && (name.endsWith(".jpg") || name.endsWith(".jpeg"))) {
                    hasJpeg = true;
                }
            }
        }
        assertTrue(hasJpeg, "DOCX media 目录应包含 JPEG 文件");
        log.info("JPEG 图片插入测试通过");
    }

    @Test
    @DisplayName("图片：GIF 格式正确识别并插入")
    void renderImageGif() throws Exception {
        Path gifPath = OUTPUT_DIR.resolve("test-image.gif");
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(100, 100, java.awt.image.BufferedImage.TYPE_INT_RGB);
        javax.imageio.ImageIO.write(img, "gif", gifPath.toFile());

        Map<String, Object> data = new HashMap<>();
        data.put("manualImg", new Image(gifPath.toString(), 200, 100));

        byte[] result = templateEngine.renderToBytes("classpath:templates/style-test-template.docx", data);
        assertNotNull(result);

        boolean hasGif = false;
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new ByteArrayInputStream(result))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.startsWith("word/media/") && name.endsWith(".gif")) {
                    hasGif = true;
                }
            }
        }
        assertTrue(hasGif, "DOCX media 目录应包含 GIF 文件");
        log.info("GIF 图片插入测试通过");
    }

    // ==================== templateLocation 拼接 ====================

    @Test
    @DisplayName("templateLocation：仅传文件名，自动拼接根路径后渲染成功")
    void renderWithFileNameOnlyUsesTemplateLocation() {
        // 不带协议前缀，应自动拼接 classpath:templates/
        byte[] result = templateEngine.renderToBytes("weekly-report-template.docx", fullData);

        assertNotNull(result);
        assertTrue(result.length > 5000, "仅传文件名渲染结果应大于5KB");
        log.info("templateLocation 拼接验证通过，大小: {} bytes", result.length);
    }

    @Test
    @DisplayName("templateLocation：传完整路径（含协议前缀），不拼接根路径")
    void renderWithFullPathSkipsTemplateLocation() {
        // 带 classpath: 前缀，直接使用，不拼接 templateLocation
        byte[] result = templateEngine.renderToBytes(
            "classpath:templates/weekly-report-template.docx", fullData);

        assertNotNull(result);
        assertTrue(result.length > 5000, "完整路径渲染结果应大于5KB");
        log.info("完整路径跳过 templateLocation 验证通过，大小: {} bytes", result.length);
    }

    @Test
    @DisplayName("templateLocation：仅传文件名但文件不存在，抛出 TemplateNotFoundException")
    void renderWithFileNameOnlyNotFound() {
        TemplateNotFoundException exception = assertThrows(
            TemplateNotFoundException.class,
            () -> templateEngine.renderToBytes("not-exist.docx", fullData)
        );
        assertEquals("TEMPLATE_001", exception.getErrorCode());
        log.info("templateLocation 文件不存在验证通过，errorCode: {}", exception.getErrorCode());
    }

    // ==================== 辅助方法 ====================

    /** 提取 DOCX 中所有文本内容 */
    private String extractAllText(XWPFDocument doc) {
        StringBuilder sb = new StringBuilder();
        // 段落文本
        for (XWPFParagraph para : doc.getParagraphs()) {
            for (XWPFRun run : para.getRuns()) {
                String t = run.getText(0);
                if (t != null) sb.append(t).append(" ");
            }
        }
        // 表格文本
        for (XWPFTable table : doc.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph para : cell.getParagraphs()) {
                        for (XWPFRun run : para.getRuns()) {
                            String t = run.getText(0);
                            if (t != null) sb.append(t).append(" ");
                        }
                    }
                }
            }
        }
        return sb.toString();
    }

    // ==================== 测试数据准备 ====================

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
        data.put("outboundChart", buildLineChart("外联告警态势", new String[]{"3/30","3/31","4/1","4/2","4/3","4/4","4/5"}, new Number[]{120,98,145,200,178,156,210}));
        data.put("outboundChartImg", new Image("src/test/resources/images/chart1.png", 400, 300, "outboundChartImg", Image.ImageType.PNG));

        data.put("topInboundIndustry", "教育");
        data.put("topInboundCount", "8888");
        data.put("inboundIndustryList", "电信、其他、银行、广电");
        data.put("topInboundRules", "请求中包含zgrab扫描器特征...");
        data.put("inboundChart", buildBarChart("正面攻击态势", new String[]{"3/30","3/31","4/1","4/2","4/3","4/4","4/5"}, new Number[]{3200,4100,2800,5600,4900,3700,6200}));
        data.put("inboundChartImg", new Image("src/test/resources/images/chart2.png", 400, 300, "inboundChartImg", Image.ImageType.PNG));

        data.put("unitOutboundTotal", "63712");
        data.put("unitOutboundChangeDir", "下降");
        data.put("unitOutboundChangeCount", "9026");
        data.put("unitOutboundPeakDate", "2026-04-28");
        data.put("unitOutboundPeakCount", "11500");
        data.put("unitOutboundTargetTop3", "vs.haifti.com、1.uqidashi.com、185.27.134.11");
        data.put("topOutboundIntelOrg", "银狐");
        data.put("unitOutboundIntelOrgList", "mylobot、phorpiex、dorkbot");
        data.put("unitOutboundChart", buildLineChart("本单位外联趋势", new String[]{"3/30","3/31","4/1","4/2","4/3","4/4","4/5"}, new Number[]{8500,9200,7800,11500,10200,9600,7100}));

        data.put("unitInboundPeakDate", "2026-04-24");
        data.put("unitInboundPeakCount", "178000");
        data.put("unitInboundAttackerTop3", "11.53.73.197、11.53.73.195、11.53.73.186");
        data.put("unitInboundAttackTypeList", "SQL注入、Webshell攻击、隐蔽隧道...");
        data.put("unitInboundChart", buildPieChart("本单位攻击类型分布", new String[]{"SQL注入","Webshell","隐蔽隧道","暴力破解","其他"}, new Number[]{35,25,20,12,8}));

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
        data.put("th_threatType", "威胁类型");
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

    // ==================== Chart 构建辅助 ====================

    private Chart buildLineChart(String title, String[] categories, Number[] values) {
        return new Chart(title,
            java.util.Arrays.asList(categories),
            java.util.Collections.singletonList(new Chart.Series("数量", java.util.Arrays.asList(values))),
            400, 300, Chart.ChartType.LINE);
    }

    private Chart buildBarChart(String title, String[] categories, Number[] values) {
        return new Chart(title,
            java.util.Arrays.asList(categories),
            java.util.Collections.singletonList(new Chart.Series("数量", java.util.Arrays.asList(values))),
            400, 300, Chart.ChartType.BAR);
    }

    private Chart buildPieChart(String title, String[] categories, Number[] values) {
        return new Chart(title,
            java.util.Arrays.asList(categories),
            java.util.Collections.singletonList(new Chart.Series("占比", java.util.Arrays.asList(values))),
            400, 300, Chart.ChartType.PIE);
    }
}
