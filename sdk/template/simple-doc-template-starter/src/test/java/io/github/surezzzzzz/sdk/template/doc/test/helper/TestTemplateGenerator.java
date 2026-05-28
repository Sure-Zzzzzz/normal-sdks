package io.github.surezzzzzz.sdk.template.doc.test.helper;

import org.apache.poi.xwpf.usermodel.*;
import org.junit.jupiter.api.Test;

import java.io.FileOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 生成全量测试模板文件（包含所有测试场景的占位符）
 */
public class TestTemplateGenerator {

    @Test
    public void generate() throws Exception {
        Path out = Paths.get("src/test/resources/templates/style-test-template.docx");
        generateStyleTestTemplate(out);
        System.out.println("模板已生成: " + out);
    }

    /**
     * 生成测试模板，包含：
     * - 文本变量
     * - 图片占位符（含原图尺寸、手动尺寸）
     * - 图表占位符（含自定义样式）
     * - 条件块
     * - 表格循环
     */
    public static void generateStyleTestTemplate(Path outPath) throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {

            // ===== 1. 标题 =====
            XWPFParagraph title = doc.createParagraph();
            title.createRun().setText("样式测试报告");

            // ===== 2. 文本变量 =====
            XWPFParagraph p1 = doc.createParagraph();
            p1.createRun().setText("客户名称：[suredt.var:clientName]，报告日期：[suredt.var:reportDate]");

            // ===== 3. 条件块测试 =====
            XWPFParagraph pConditionStart = doc.createParagraph();
            pConditionStart.createRun().setText("[suredt.start:showSection]");

            XWPFParagraph pConditionContent = doc.createParagraph();
            pConditionContent.createRun().setText("此为条件块内容（showSection=true 时显示）");

            XWPFParagraph pConditionEnd = doc.createParagraph();
            pConditionEnd.createRun().setText("[suredt.end:showSection]");

            // ===== 4. 自然尺寸图片占位符 =====
            XWPFParagraph pImg1 = doc.createParagraph();
            pImg1.createRun().setText("[suredt.img:naturalImg]");

            // ===== 5. 原图尺寸图片占位符 =====
            XWPFParagraph pImg2 = doc.createParagraph();
            pImg2.createRun().setText("[suredt.img:autoWidthImg]");

            // ===== 6. 手动尺寸图片占位符 =====
            XWPFParagraph pImg3 = doc.createParagraph();
            pImg3.createRun().setText("[suredt.img:manualImg]");

            // ===== 7. 图表占位符（width=0 使用默认值）=====
            XWPFParagraph pChart1 = doc.createParagraph();
            pChart1.createRun().setText("[suredt.chart:autoWidthChart]");

            // ===== 8. 自定义样式图表占位符（多系列+颜色+样式）=====
            XWPFParagraph pChart2 = doc.createParagraph();
            pChart2.createRun().setText("[suredt.chart:styledChart]");

            // ===== 9. 柱状图（自定义样式）=====
            XWPFParagraph pChart3 = doc.createParagraph();
            pChart3.createRun().setText("[suredt.chart:styledBarChart]");

            // ===== 10. 饼图占位符 =====
            XWPFParagraph pChart4 = doc.createParagraph();
            pChart4.createRun().setText("[suredt.chart:styledPieChart]");

            // ===== 11. 表格循环测试 =====
            XWPFTable table = doc.createTable(4, 3);

            // 表头行
            XWPFTableRow headerRow = table.getRow(0);
            headerRow.getCell(0).getParagraphs().get(0).createRun().setText("[suredt.var:th_itemName]");
            headerRow.getCell(1).getParagraphs().get(0).createRun().setText("[suredt.var:th_itemValue]");
            headerRow.getCell(2).getParagraphs().get(0).createRun().setText("[suredt.var:th_remark]");

            // for 行
            XWPFTableRow forRow = table.getRow(1);
            forRow.getCell(0).getParagraphs().get(0).createRun().setText("[suredt.for:items]");
            forRow.getCell(1).getParagraphs().get(0).createRun().setText("");
            forRow.getCell(2).getParagraphs().get(0).createRun().setText("");

            // 数据行
            XWPFTableRow dataRow = table.getRow(2);
            dataRow.getCell(0).getParagraphs().get(0).createRun().setText("[suredt.var:itemName]");
            dataRow.getCell(1).getParagraphs().get(0).createRun().setText("[suredt.var:itemValue]");
            dataRow.getCell(2).getParagraphs().get(0).createRun().setText("[suredt.var:remark]");

            // endfor 行
            XWPFTableRow endforRow = table.getRow(3);
            endforRow.getCell(0).getParagraphs().get(0).createRun().setText("[suredt.endfor:items]");
            endforRow.getCell(1).getParagraphs().get(0).createRun().setText("");
            endforRow.getCell(2).getParagraphs().get(0).createRun().setText("");

            try (FileOutputStream fos = new FileOutputStream(outPath.toFile())) {
                doc.write(fos);
            }
        }
    }
}