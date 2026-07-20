package io.github.surezzzzzz.sdk.template.doc.test.cases;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.annotation.ExcelProperty;
import io.github.surezzzzzz.sdk.template.doc.engine.TemplateEngine;
import io.github.surezzzzzz.sdk.template.doc.support.NumberingProcessor;
import io.github.surezzzzzz.sdk.template.doc.test.SimpleDocTemplateTestApplication;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.poi.Version;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EasyExcel 与文档模板的 POI 兼容测试
 *
 * @author surezzzzzz
 */
@SpringBootTest(classes = SimpleDocTemplateTestApplication.class)
@DisplayName("EasyExcel POI 共存测试")
class EasyExcelPoiCompatibilityTest {

    @Autowired
    private TemplateEngine templateEngine;

    @Test
    @DisplayName("EasyExcel 写读与 DOCX 渲染在同 JVM 正常运行")
    void easyExcelAndDocxRenderingShouldCoexist() throws Exception {
        String expectedPoiVersion = System.getProperty("poiRuntimeVersion");
        assertEquals(expectedPoiVersion, Version.getVersion(), "测试运行时 POI 版本不符合预期");
        assertEquals("3.17", expectedPoiVersion, "EasyExcel 2.2.9 共存测试只应运行在 POI 3.17 档");

        ByteArrayOutputStream excelOutput = new ByteArrayOutputStream();
        EasyExcel.write(excelOutput, ExcelRow.class)
                .sheet("compatibility")
                .doWrite(Collections.singletonList(new ExcelRow("easyexcel-poi-compatibility")));

        List<Object> rows = EasyExcel.read(new ByteArrayInputStream(excelOutput.toByteArray()))
                .head(ExcelRow.class)
                .sheet()
                .doReadSync();
        assertEquals(1, rows.size(), "EasyExcel 读取行数不正确");
        assertEquals("easyexcel-poi-compatibility", ((ExcelRow) rows.get(0)).getValue(), "EasyExcel 读取内容不正确");

        Map<String, Object> data = new HashMap<>();
        data.put("clientName", "poi-compatibility-client");
        byte[] docxBytes = templateEngine.renderToBytes("classpath:templates/weekly-report-template.docx", data);

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            StringBuilder text = new StringBuilder();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                text.append(paragraph.getText());
            }
            assertTrue(text.toString().contains("poi-compatibility-client"), "DOCX 渲染结果缺少替换文本");
            NumberingProcessor.from(document);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExcelRow {

        @ExcelProperty("value")
        private String value;
    }
}
