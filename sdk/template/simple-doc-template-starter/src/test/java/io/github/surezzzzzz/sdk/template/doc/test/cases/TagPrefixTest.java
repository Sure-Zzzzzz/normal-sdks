package io.github.surezzzzzz.sdk.template.doc.test.cases;

import io.github.surezzzzzz.sdk.template.doc.constant.SimpleDocTemplateConstant;
import io.github.surezzzzzz.sdk.template.doc.engine.TemplateEngine;
import io.github.surezzzzzz.sdk.template.doc.support.TagHelper;
import io.github.surezzzzzz.sdk.template.doc.test.SimpleDocTemplateTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tag Prefix Tests - TagHelper 方法验证 + 自定义前缀端到端渲染
 *
 * @author surezzzzzz
 */
@Slf4j
@DisplayName("标签前缀测试")
class TagPrefixTest {

    private static final Path OUTPUT_DIR = Paths.get("build/test-output/tag-prefix");

    // ==================== 默认前缀 ====================

    @Nested
    @DisplayName("默认前缀（suredt）")
    @SpringBootTest(classes = SimpleDocTemplateTestApplication.class)
    class DefaultPrefixTest {

        @Autowired
        TagHelper tagHelper;

        @BeforeEach
        void ensureOutputDir() throws Exception {
            Files.createDirectories(OUTPUT_DIR);
        }

        @Test
        @DisplayName("getPrefix() 返回默认前缀 suredt")
        void defaultPrefixIsCorrect() {
            assertEquals(SimpleDocTemplateConstant.DEFAULT_TAG_PREFIX, tagHelper.getPrefix());
            log.info("默认前缀: {}", tagHelper.getPrefix());
        }

        @Test
        @DisplayName("varPrefix() 返回 [suredt.var:")
        void varPrefixIsCorrect() {
            assertEquals("[suredt.var:", tagHelper.varPrefix());
        }

        @Test
        @DisplayName("imgPrefix() 返回 [suredt.img:")
        void imgPrefixIsCorrect() {
            assertEquals("[suredt.img:", tagHelper.imgPrefix());
        }

        @Test
        @DisplayName("chartPrefix() 返回 [suredt.chart:")
        void chartPrefixIsCorrect() {
            assertEquals("[suredt.chart:", tagHelper.chartPrefix());
        }

        @Test
        @DisplayName("startPrefix() 返回 [suredt.start:")
        void startPrefixIsCorrect() {
            assertEquals("[suredt.start:", tagHelper.startPrefix());
        }

        @Test
        @DisplayName("endPrefix() 返回 [suredt.end:")
        void endPrefixIsCorrect() {
            assertEquals("[suredt.end:", tagHelper.endPrefix());
        }

        @Test
        @DisplayName("forPrefix() 返回 [suredt.for:")
        void forPrefixIsCorrect() {
            assertEquals("[suredt.for:", tagHelper.forPrefix());
        }

        @Test
        @DisplayName("endforPrefix() 返回 [suredt.endfor:")
        void endforPrefixIsCorrect() {
            assertEquals("[suredt.endfor:", tagHelper.endforPrefix());
        }

        @Test
        @DisplayName("startTag(key) 返回完整标签")
        void startTagIsCorrect() {
            assertEquals("[suredt.start:showSection]", tagHelper.startTag("showSection"));
        }

        @Test
        @DisplayName("endTag(key) 返回完整标签")
        void endTagIsCorrect() {
            assertEquals("[suredt.end:showSection]", tagHelper.endTag("showSection"));
        }

        @Test
        @DisplayName("forTag(key) 返回完整标签")
        void forTagIsCorrect() {
            assertEquals("[suredt.for:items]", tagHelper.forTag("items"));
        }

        @Test
        @DisplayName("endforTag(key) 返回完整标签")
        void endforTagIsCorrect() {
            assertEquals("[suredt.endfor:items]", tagHelper.endforTag("items"));
        }

        @Test
        @DisplayName("matches() 正确判断前缀匹配")
        void matchesIsCorrect() {
            assertTrue(tagHelper.matches("[suredt.var:name]", "[suredt.var:"));
            assertFalse(tagHelper.matches("[mypfx.var:name]", "[suredt.var:"));
            assertFalse(tagHelper.matches(null, "[suredt.var:"));
        }

        @Test
        @DisplayName("extractKey() 正确提取 key")
        void extractKeyIsCorrect() {
            assertEquals("name", tagHelper.extractKey("[suredt.var:name]", "[suredt.var:"));
            assertEquals("showSection", tagHelper.extractKey("[suredt.start:showSection]", "[suredt.start:"));
            assertEquals("", tagHelper.extractKey("[mypfx.var:name]", "[suredt.var:"));
            assertEquals("", tagHelper.extractKey(null, "[suredt.var:"));
        }
    }

    // ==================== 自定义前缀 ====================

    @Nested
    @DisplayName("自定义前缀（mypfx）")
    @SpringBootTest(classes = SimpleDocTemplateTestApplication.class,
        properties = "io.github.surezzzzzz.sdk.template.doc.tag-prefix=mypfx")
    class CustomPrefixTest {

        @Autowired
        TagHelper tagHelper;

        @Autowired
        TemplateEngine templateEngine;

        @BeforeEach
        void ensureOutputDir() throws Exception {
            Files.createDirectories(OUTPUT_DIR);
        }

        @Test
        @DisplayName("getPrefix() 返回自定义前缀 mypfx")
        void customPrefixIsCorrect() {
            assertEquals("mypfx", tagHelper.getPrefix());
            log.info("自定义前缀: {}", tagHelper.getPrefix());
        }

        @Test
        @DisplayName("所有前缀方法均使用 mypfx")
        void allPrefixMethodsUseCustomPrefix() {
            assertEquals("[mypfx.var:", tagHelper.varPrefix());
            assertEquals("[mypfx.img:", tagHelper.imgPrefix());
            assertEquals("[mypfx.chart:", tagHelper.chartPrefix());
            assertEquals("[mypfx.start:", tagHelper.startPrefix());
            assertEquals("[mypfx.end:", tagHelper.endPrefix());
            assertEquals("[mypfx.for:", tagHelper.forPrefix());
            assertEquals("[mypfx.endfor:", tagHelper.endforPrefix());
            log.info("自定义前缀方法验证通过");
        }

        @Test
        @DisplayName("完整标签方法均使用 mypfx")
        void allTagMethodsUseCustomPrefix() {
            assertEquals("[mypfx.start:showSection]", tagHelper.startTag("showSection"));
            assertEquals("[mypfx.end:showSection]", tagHelper.endTag("showSection"));
            assertEquals("[mypfx.for:items]", tagHelper.forTag("items"));
            assertEquals("[mypfx.endfor:items]", tagHelper.endforTag("items"));
        }

        @Test
        @DisplayName("端到端：含 mypfx 占位符的模板正确渲染文本变量")
        void endToEndTextVariableRendering() throws Exception {
            Path templateFile = OUTPUT_DIR.resolve("mypfx-text-template.docx");
            buildCustomPrefixTemplate(templateFile, "mypfx");
            log.info("模板文件已生成: {}", templateFile);

            Map<String, Object> data = new HashMap<>();
            data.put("name", "测试名称");
            data.put("value", "测试值");

            byte[] result = templateEngine.renderToBytes("file:" + templateFile.toAbsolutePath(), data);

            Path outputFile = OUTPUT_DIR.resolve("mypfx-text-output.docx");
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outputFile.toFile())) {
                fos.write(result);
            }
            log.info("渲染结果已输出: {}", outputFile);

            assertNotNull(result);
            assertTrue(result.length > 0);

            try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(result))) {
                String allText = extractAllText(doc);
                log.info("自定义前缀端到端渲染，文档文本: {}", allText);
                assertTrue(allText.contains("测试名称"), "name 变量未被替换");
                assertTrue(allText.contains("测试值"), "value 变量未被替换");
                assertFalse(allText.contains("[mypfx.var:"), "占位符应已被替换，不应残留");
            }
        }

        @Test
        @DisplayName("端到端：条件块 true 时保留内容，false 时删除内容")
        void endToEndConditionBlockRendering() throws Exception {
            Path templateFile = OUTPUT_DIR.resolve("mypfx-condition-template.docx");
            buildCustomPrefixConditionTemplate(templateFile, "mypfx");
            log.info("模板文件已生成: {}", templateFile);

            // showSection=true：保留块内容
            Map<String, Object> dataTrue = new HashMap<>();
            dataTrue.put("name", "条件测试");
            dataTrue.put("showSection", Boolean.TRUE);

            byte[] resultTrue = templateEngine.renderToBytes("file:" + templateFile.toAbsolutePath(), dataTrue);
            Path outputTrue = OUTPUT_DIR.resolve("mypfx-condition-true-output.docx");
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outputTrue.toFile())) {
                fos.write(resultTrue);
            }
            log.info("条件块 true 结果已输出: {}", outputTrue);
            try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(resultTrue))) {
                String allText = extractAllText(doc);
                log.info("条件块 true，文档文本: {}", allText);
                assertTrue(allText.contains("条件块内容"), "showSection=true 时块内容应保留");
                assertFalse(allText.contains("[mypfx.start:"), "start 标记应被删除");
                assertFalse(allText.contains("[mypfx.end:"), "end 标记应被删除");
            }

            // showSection=false：删除整块
            Map<String, Object> dataFalse = new HashMap<>();
            dataFalse.put("name", "条件测试");
            dataFalse.put("showSection", Boolean.FALSE);

            byte[] resultFalse = templateEngine.renderToBytes("file:" + templateFile.toAbsolutePath(), dataFalse);
            Path outputFalse = OUTPUT_DIR.resolve("mypfx-condition-false-output.docx");
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outputFalse.toFile())) {
                fos.write(resultFalse);
            }
            log.info("条件块 false 结果已输出: {}", outputFalse);
            try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(resultFalse))) {
                String allText = extractAllText(doc);
                log.info("条件块 false，文档文本: {}", allText);
                assertFalse(allText.contains("条件块内容"), "showSection=false 时块内容应被删除");
            }
        }

        @Test
        @DisplayName("端到端：表格循环正确展开")
        void endToEndTableLoopRendering() throws Exception {
            Path templateFile = OUTPUT_DIR.resolve("mypfx-loop-template.docx");
            buildCustomPrefixLoopTemplate(templateFile, "mypfx");
            log.info("模板文件已生成: {}", templateFile);

            List<Map<String, Object>> items = new ArrayList<>();
            Map<String, Object> item1 = new HashMap<>();
            item1.put("itemName", "条目一");
            item1.put("itemValue", "值一");
            items.add(item1);

            Map<String, Object> item2 = new HashMap<>();
            item2.put("itemName", "条目二");
            item2.put("itemValue", "值二");
            items.add(item2);

            Map<String, Object> data = new HashMap<>();
            data.put("items", items);

            byte[] result = templateEngine.renderToBytes("file:" + templateFile.toAbsolutePath(), data);
            Path outputFile = OUTPUT_DIR.resolve("mypfx-loop-output.docx");
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outputFile.toFile())) {
                fos.write(result);
            }
            log.info("表格循环结果已输出: {}", outputFile);
            try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(result))) {
                String allText = extractAllText(doc);
                log.info("表格循环端到端渲染，文档文本: {}", allText);
                assertTrue(allText.contains("条目一"), "第1条 itemName 未展开");
                assertTrue(allText.contains("条目二"), "第2条 itemName 未展开");
                assertTrue(allText.contains("值一"), "第1条 itemValue 未展开");
                assertTrue(allText.contains("值二"), "第2条 itemValue 未展开");
                assertFalse(allText.contains("[mypfx.for:"), "for 标记应被删除");
                assertFalse(allText.contains("[mypfx.endfor:"), "endfor 标记应被删除");
            }
        }

        @Test
        @DisplayName("suredt 占位符在 mypfx 上下文中不被识别，原样保留")
        void sureditPlaceholderNotRecognizedUnderCustomPrefix() throws Exception {
            // 用 suredt 前缀的模板，在 mypfx 上下文中渲染，占位符不应被替换
            Path templateFile = OUTPUT_DIR.resolve("mypfx-suredt-template.docx");
            buildCustomPrefixTemplate(templateFile, "suredt");
            log.info("模板文件已生成: {}", templateFile);

            Map<String, Object> data = new HashMap<>();
            data.put("name", "不应出现");
            data.put("value", "不应出现");

            byte[] result = templateEngine.renderToBytes("file:" + templateFile.toAbsolutePath(), data);
            Path outputFile = OUTPUT_DIR.resolve("mypfx-suredt-output.docx");
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outputFile.toFile())) {
                fos.write(result);
            }
            log.info("suredt/mypfx 对比结果已输出: {}", outputFile);
            try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(result))) {
                String allText = extractAllText(doc);
                log.info("suredt 占位符在 mypfx 上下文中，文档文本: {}", allText);
                // mypfx 上下文不识别 suredt 占位符，占位符原样保留
                assertTrue(allText.contains("[suredt.var:name]"), "suredt 占位符应原样保留");
                assertFalse(allText.contains("不应出现"), "suredt 占位符不应被替换");
            }
        }
    }

    // ==================== 模板构建辅助 ====================

    /**
     * 构建含文本变量占位符的模板：
     * 段落1：[prefix.var:name]
     * 段落2：[prefix.var:value]
     */
    private void buildCustomPrefixTemplate(Path file, String prefix) throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph p1 = doc.createParagraph();
            p1.createRun().setText(String.format("[%s.var:name]", prefix));

            XWPFParagraph p2 = doc.createParagraph();
            p2.createRun().setText(String.format("[%s.var:value]", prefix));

            try (FileOutputStream fos = new FileOutputStream(file.toFile())) {
                doc.write(fos);
            }
        }
    }

    /**
     * 构建含条件块的模板：
     * 段落1：[prefix.var:name]
     * 段落2：[prefix.start:showSection]
     * 段落3：条件块内容
     * 段落4：[prefix.end:showSection]
     */
    private void buildCustomPrefixConditionTemplate(Path file, String prefix) throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph p1 = doc.createParagraph();
            p1.createRun().setText(String.format("[%s.var:name]", prefix));

            XWPFParagraph p2 = doc.createParagraph();
            p2.createRun().setText(String.format("[%s.start:showSection]", prefix));

            XWPFParagraph p3 = doc.createParagraph();
            p3.createRun().setText("条件块内容");

            XWPFParagraph p4 = doc.createParagraph();
            p4.createRun().setText(String.format("[%s.end:showSection]", prefix));

            try (FileOutputStream fos = new FileOutputStream(file.toFile())) {
                doc.write(fos);
            }
        }
    }

    /**
     * 构建含表格循环的模板：
     * 表格结构：
     * | [prefix.for:items]  |              |
     * | [prefix.var:itemName] | [prefix.var:itemValue] |
     * | [prefix.endfor:items] |              |
     */
    private void buildCustomPrefixLoopTemplate(Path file, String prefix) throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFTable table = doc.createTable(3, 2);

            // for 行
            table.getRow(0).getCell(0).getParagraphs().get(0).createRun()
                .setText(String.format("[%s.for:items]", prefix));

            // 数据行
            table.getRow(1).getCell(0).getParagraphs().get(0).createRun()
                .setText(String.format("[%s.var:itemName]", prefix));
            table.getRow(1).getCell(1).getParagraphs().get(0).createRun()
                .setText(String.format("[%s.var:itemValue]", prefix));

            // endfor 行
            table.getRow(2).getCell(0).getParagraphs().get(0).createRun()
                .setText(String.format("[%s.endfor:items]", prefix));

            try (FileOutputStream fos = new FileOutputStream(file.toFile())) {
                doc.write(fos);
            }
        }
    }

    // ==================== 辅助方法 ====================

    private String extractAllText(XWPFDocument doc) {
        StringBuilder sb = new StringBuilder();
        for (XWPFParagraph para : doc.getParagraphs()) {
            for (XWPFRun run : para.getRuns()) {
                String t = run.getText(0);
                if (t != null) sb.append(t).append(" ");
            }
        }
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
}
