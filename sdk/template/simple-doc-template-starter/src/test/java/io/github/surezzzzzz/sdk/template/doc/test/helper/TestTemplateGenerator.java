package io.github.surezzzzzz.sdk.template.doc.test.helper;

import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.*;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import lombok.extern.slf4j.Slf4j;

import java.io.FileOutputStream;
import java.math.BigInteger;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 生成全量测试模板文件（包含所有测试场景的占位符）
 */
@Slf4j
public class TestTemplateGenerator {

    @Test
    public void generate() throws Exception {
        Path styleOut = Paths.get("src/test/resources/templates/style-test-template.docx");
        generateStyleTestTemplate(styleOut);
        log.info("样式模板已生成: {}", styleOut);

        Path hfOut = Paths.get("src/test/resources/templates/header-footer-template.docx");
        generateHeaderFooterTemplate(hfOut);
        log.info("页眉页脚模板已生成: {}", hfOut);

        Path cellFmtOut = Paths.get("src/test/resources/templates/cell-format-template.docx");
        generateCellFormatTemplate(cellFmtOut);
        log.info("单元格格式模板已生成: {}", cellFmtOut);
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

    /**
     * 生成含 header/footer 的测试模板
     */
    public static void generateHeaderFooterTemplate(Path outPath) throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {

            // 创建默认 header：每个占位符独占一个段落
            XWPFHeader header = doc.createHeader(HeaderFooterType.DEFAULT);
            header.createParagraph().createRun().setText("报告单位：[suredt.var:clientName]");
            header.createParagraph().createRun().setText("[suredt.start:showHeaderDate]");
            header.createParagraph().createRun().setText("日期：[suredt.var:reportDate]");
            header.createParagraph().createRun().setText("[suredt.end:showHeaderDate]");

            // 创建默认 footer：页码由用户在 Word 模板中直接插入 Word 字段，此处保留文字示例
            XWPFFooter footer = doc.createFooter(HeaderFooterType.DEFAULT);
            XWPFParagraph fPara = footer.createParagraph();
            fPara.createRun().setText("第 [suredt.var:pageNum] 页");

            // body 内容
            XWPFParagraph p1 = doc.createParagraph();
            p1.createRun().setText("正文内容：客户 [suredt.var:clientName]，日期 [suredt.var:reportDate]");

            // 设置页面大小（A4）和页眉页脚边距，否则 Word 不显示页眉页脚区域
            CTSectPr sectPr = doc.getDocument().getBody().isSetSectPr()
                    ? doc.getDocument().getBody().getSectPr()
                    : doc.getDocument().getBody().addNewSectPr();
            CTPageSz pgSz = sectPr.isSetPgSz() ? sectPr.getPgSz() : sectPr.addNewPgSz();
            pgSz.setW(BigInteger.valueOf(11906));   // A4 宽 (595.3pt * 20)
            pgSz.setH(BigInteger.valueOf(16838));   // A4 高 (841.9pt * 20)
            CTPageMar pgMar = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
            pgMar.setTop(BigInteger.valueOf(1440));
            pgMar.setBottom(BigInteger.valueOf(1440));
            pgMar.setLeft(BigInteger.valueOf(1800));
            pgMar.setRight(BigInteger.valueOf(1800));
            pgMar.setHeader(BigInteger.valueOf(720));
            pgMar.setFooter(BigInteger.valueOf(720));

            try (FileOutputStream fos = new FileOutputStream(outPath.toFile())) {
                doc.write(fos);
            }
        }
    }

    /**
     * 生成含单元格格式的表格循环测试模板
     * 数据行设置了浅蓝色背景色，验证展开后格式是否保留
     */
    public static void generateCellFormatTemplate(Path outPath) throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFTable table = doc.createTable(4, 3);

            // 表头行
            XWPFTableRow headerRow = table.getRow(0);
            headerRow.getCell(0).getParagraphs().get(0).createRun().setText("名称");
            headerRow.getCell(1).getParagraphs().get(0).createRun().setText("值");
            headerRow.getCell(2).getParagraphs().get(0).createRun().setText("备注");

            // for 行
            XWPFTableRow forRow = table.getRow(1);
            forRow.getCell(0).getParagraphs().get(0).createRun().setText("[suredt.for:items]");
            forRow.getCell(1).getParagraphs().get(0).createRun().setText("");
            forRow.getCell(2).getParagraphs().get(0).createRun().setText("");

            // 数据行：设置浅蓝色背景（E7EFF7）
            XWPFTableRow dataRow = table.getRow(2);
            setCellShading(dataRow.getCell(0), "E7EFF7");
            setCellShading(dataRow.getCell(1), "E7EFF7");
            setCellShading(dataRow.getCell(2), "E7EFF7");
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

    /**
     * 设置单元格背景色
     */
    private static void setCellShading(XWPFTableCell cell, String fillColor) {
        CTTc tc = cell.getCTTc();
        CTTcPr tcPr = tc.isSetTcPr() ? tc.getTcPr() : tc.addNewTcPr();
        CTShd shd = tcPr.isSetShd() ? tcPr.getShd() : tcPr.addNewShd();
        shd.setVal(STShd.CLEAR);
        shd.setColor("auto");
        shd.setFill(fillColor);
    }
}
