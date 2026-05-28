package io.github.surezzzzzz.sdk.template.doc.renderer.word;

import io.github.surezzzzzz.sdk.template.doc.annotation.SimpleDocTemplateComponent;
import io.github.surezzzzzz.sdk.template.doc.constant.SimpleDocTemplateConstant;
import io.github.surezzzzzz.sdk.template.doc.document.Document;
import io.github.surezzzzzz.sdk.template.doc.document.WordDocument;
import io.github.surezzzzzz.sdk.template.doc.exception.TemplateRenderException;
import io.github.surezzzzzz.sdk.template.doc.model.Chart;
import io.github.surezzzzzz.sdk.template.doc.model.Image;
import io.github.surezzzzzz.sdk.template.doc.renderer.Renderer;
import io.github.surezzzzzz.sdk.template.doc.support.TagHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.util.Units;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTc;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Word Renderer - Word 文档渲染器（Apache POI 直连，替代 poi-tl）
 *
 * <p>自解析模板语法，处理变量、条件块、图片和表格循环：
 * <ul>
 *   <li>[suredt.var:key] — 文本变量替换</li>
 *   <li>[suredt.img:key] — 图片插入（data 中对应值为 Image）</li>
 *   <li>[suredt.chart:key] — Word 原生可编辑图表（data 中对应值为 Chart）</li>
 *   <li>[suredt.for:key] / [suredt.endfor:key] — 表格行循环（key 对应 List）</li>
 *   <li>[suredt.start:key] / [suredt.end:key] — 条件块（key 对应 Boolean）</li>
 * </ul>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleDocTemplateComponent
public class WordRenderer implements Renderer {

    // ===== 模板标签正则 =====

    private Pattern varPattern;
    private Pattern imgPattern;
    private Pattern chartPattern;

    // ===== DOCX ZIP 内部路径（Word 专属，自闭环）=====

    private static final String DOCX_DOCUMENT_XML = SimpleDocTemplateConstant.DOCX_DOCUMENT_XML;
    private static final String DOCX_DOCUMENT_RELS = "word/_rels/document.xml.rels";
    private static final String DOCX_CONTENT_TYPES = "[Content_Types].xml";
    private static final String DOCX_CHARTS_DIR = "word/charts/";
    private static final String DOCX_EMBEDDINGS_DIR = "word/embeddings/";
    private static final String DOCX_CHART_XML_TMPL = "word/charts/chart%d.xml";
    private static final String DOCX_CHART_RELS_TMPL = "word/charts/_rels/chart%d.xml.rels";
    private static final String DOCX_EMBEDDING_TMPL = "word/embeddings/Microsoft_Office_Chart%d.xlsx";

    // ===== OOXML 命名空间 & 关系类型（Word 专属，自闭环）=====

    private static final String NS_RELATIONSHIPS = "http://schemas.openxmlformats.org/package/2006/relationships";
    private static final String NS_WP = "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing";
    private static final String NS_A = "http://schemas.openxmlformats.org/drawingml/2006/main";
    private static final String NS_C = "http://schemas.openxmlformats.org/drawingml/2006/chart";
    private static final String NS_R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String REL_TYPE_CHART = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/chart";
    private static final String REL_TYPE_PACKAGE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/package";
    private static final String CT_CHART = "application/vnd.openxmlformats-officedocument.drawingml.chart+xml";
    private static final String CT_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    // ===== Chart 生成相关常量（Word 专属，自闭环）=====

    private static final String CHART_MARKER_PREFIX = "SUREDT_CHART_";
    private static final String CHART_REL_ID_PREFIX = "rIdChart";
    private static final String CHART_COLOR_DEFAULT = "4F81BD";
    private static final String CHART_LANG = "zh-CN";
    private static final String CHART_SHEET_NAME = "Sheet1";
    private static final String CHART_CATEGORY_HEADER = "Category";
    private static final int CHART_DOC_PR_ID_OFFSET = 100;
    private static final int IO_BUFFER_SIZE = SimpleDocTemplateConstant.IO_BUFFER_SIZE;
    private static final int DEFAULT_CHART_HEIGHT_PX = 300;

    /**
     * 默认页面内容宽度（px）。
     * 仅在模板无页面设置时使用，约等于 A4 页面（148mm）减去 2.5cm*2 边距后的宽度。
     */
    private static final int DEFAULT_CONTENT_WIDTH_PX = 952;

    /**
     * 1px 对应的 EMU 数（1 inch = 914400 EMU，1 inch ≈ 96px）
     */
    private static final long EMUS_PER_PX = 914400L / 96;

    /**
     * POI 图片类型常量（PNG）
     */
    private static final int PICTURE_TYPE_PNG = XWPFDocument.PICTURE_TYPE_PNG;

    @Autowired
    private TagHelper tagHelper;

    @PostConstruct
    void init() {
        String prefix = tagHelper.getPrefix();
        varPattern = Pattern.compile(String.format("\\[%s\\.var:([^\\]]+)]", prefix));
        imgPattern = Pattern.compile(String.format("\\[%s\\.img:([^\\]]+)]", prefix));
        chartPattern = Pattern.compile(String.format("\\[%s\\.chart:([^\\]]+)]", prefix));
    }

    @Override
    public Document render(byte[] templateBytes, Map<String, Object> data) {
        try {
            List<ChartPlaceholderInfo> chartPlaceholders = new ArrayList<>();

            // OPCPackage 由 XWPFDocument 管理，随 doc.close() 一起关闭
            OPCPackage pkg = OPCPackage.open(new ByteArrayInputStream(templateBytes));
            XWPFDocument doc = new XWPFDocument(pkg);

            // 页面内容宽度，用于图片 width=0 时的默认值兜底
            int contentWidthPx = DEFAULT_CONTENT_WIDTH_PX;

            // 1. 先处理条件块（[suredt.start:key] / [suredt.end:key]）
            processConditionBlocks(doc, data);

            // 2. 遍历所有段落，处理文本变量、图片、图表
            processAllParagraphs(doc, data, chartPlaceholders, contentWidthPx);

            // 3. 处理所有表格（变量 + 图片 + 行循环）
            processAllTables(doc, data, contentWidthPx);

            // 4. 写出临时文件
            Path tmpDocx = Files.createTempFile("rendered-", ".docx");
            try {
                try (java.io.OutputStream os = Files.newOutputStream(tmpDocx)) {
                    doc.write(os);
                }
            } finally {
                doc.close();
            }

            // 5. 处理图表（需要操作 ZIP 结构：写入 chart.xml + 更新 rels）
            byte[] tmpBytes;
            try (InputStream is = Files.newInputStream(tmpDocx)) {
                tmpBytes = toByteArray(is);
            }
            byte[] withCharts = processChartsInZip(tmpBytes, chartPlaceholders);
            Files.deleteIfExists(tmpDocx);

            // 6. 直接用最终字节构造 WordDocument，不经 POI 二次序列化
            return new WordDocument(withCharts);
        } catch (Exception e) {
            throw TemplateRenderException.renderFailed(e.getMessage(), e);
        }
    }

    // ===== 条件块处理 =====

    private void processConditionBlocks(XWPFDocument doc, Map<String, Object> data) {
        List<IBodyElement> elements = new ArrayList<>(doc.getBodyElements());
        int i = 0;
        while (i < elements.size()) {
            IBodyElement elem = elements.get(i);
            if (!(elem instanceof XWPFParagraph)) {
                i++;
                continue;
            }
            String text = getParaText((XWPFParagraph) elem);
            if (!tagHelper.matches(text, tagHelper.startPrefix()) || !text.endsWith("]")) {
                i++;
                continue;
            }
            String key = tagHelper.extractKey(text, tagHelper.startPrefix());
            boolean condition = isTruthy(data.get(key));

            // 找 end 标记
            int endIdx = -1;
            String endTag = tagHelper.endTag(key);
            for (int j = i + 1; j < elements.size(); j++) {
                if (elements.get(j) instanceof XWPFParagraph) {
                    if (endTag.equals(getParaText((XWPFParagraph) elements.get(j)))) {
                        endIdx = j;
                        break;
                    }
                }
            }
            if (endIdx == -1) {
                i++;
                continue;
            }

            if (!condition) {
                for (int j = endIdx; j >= i; j--) {
                    int pos = doc.getBodyElements().indexOf(elements.get(j));
                    if (pos >= 0) doc.removeBodyElement(pos);
                }
            } else {
                int endPos = doc.getBodyElements().indexOf(elements.get(endIdx));
                if (endPos >= 0) doc.removeBodyElement(endPos);
                int startPos = doc.getBodyElements().indexOf(elements.get(i));
                if (startPos >= 0) doc.removeBodyElement(startPos);
            }
            elements = new ArrayList<>(doc.getBodyElements());
        }
    }

    private boolean isTruthy(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) return !((String) value).isEmpty();
        if (value instanceof List) return !((List<?>) value).isEmpty();
        return true;
    }

    // ===== 段落处理 =====

    private void processAllParagraphs(XWPFDocument doc, Map<String, Object> data, List<ChartPlaceholderInfo> chartPlaceholders, int contentWidthPx) {
        for (XWPFParagraph para : doc.getParagraphs()) {
            processParagraphText(para, data);
            replaceImgPlaceholder(para, data, contentWidthPx);
            replaceChartPlaceholder(para, data, chartPlaceholders);
        }
    }

    /**
     * 替换段落中所有 run 的文本（[suredt.var:xxx]）
     */
    private void processParagraphText(XWPFParagraph para, Map<String, Object> data) {
        for (XWPFRun run : para.getRuns()) {
            String text = run.getText(0);
            if (text == null || text.isEmpty()) continue;

            String replaced = replaceVar(text, data);
            run.setText(replaced, 0);
        }
    }

    /**
     * 将段落中的图片占位符替换为 Image 中的实际图片
     */
    private void replaceImgPlaceholder(XWPFParagraph para, Map<String, Object> data, int containerWidthPx) {
        String full = getParaText(para);
        Matcher m = imgPattern.matcher(full);
        if (!m.find()) return;

        String key = m.group(1);
        Object value = data.get(key);
        if (!(value instanceof Image)) return;

        Image img = (Image) value;
        clearPara(para);
        insertPicture(para, img, containerWidthPx);
    }

    private void replaceChartPlaceholder(XWPFParagraph para, Map<String, Object> data, List<ChartPlaceholderInfo> chartPlaceholders) {
        String full = getParaText(para);
        Matcher m = chartPattern.matcher(full);
        if (!m.find()) return;

        String key = m.group(1);
        Object value = data.get(key);
        if (!(value instanceof Chart)) return;

        Chart chart = (Chart) value;
        String marker = CHART_MARKER_PREFIX + chartPlaceholders.size();
        clearPara(para);
        XWPFRun run = para.createRun();
        run.setText(marker);
        chartPlaceholders.add(new ChartPlaceholderInfo(key, chart, marker));
    }

    private void insertPicture(XWPFParagraph para, Image img, int containerWidthPx) {
        String src = img.getSrc();
        try {
            int targetWidthPx;
            int targetHeightPx;
            if (img.isNaturalSize()) {
                // width=0 且 height=0：两维都用原图尺寸
                targetWidthPx = img.resolveWidth() > 0 ? img.resolveWidth() : containerWidthPx;
                targetHeightPx = img.resolveHeight() > 0 ? img.resolveHeight() : targetWidthPx;
            } else if (img.getWidth() <= 0) {
                // width=0 但 height>0：宽用原图，高用指定值
                targetWidthPx = img.resolveWidth() > 0 ? img.resolveWidth() : containerWidthPx;
                targetHeightPx = img.getHeight();
            } else if (img.getHeight() <= 0) {
                // width>0 但 height=0：宽用指定值，高按比例
                targetWidthPx = img.getWidth();
                targetHeightPx = img.resolveHeight();
                if (targetHeightPx == 0) {
                    targetHeightPx = targetWidthPx;
                }
            } else {
                targetWidthPx = img.getWidth();
                targetHeightPx = img.getHeight();
            }

            if (targetWidthPx <= 0 || targetHeightPx <= 0) {
                log.warn("图片尺寸无效: src={}, width={}, height={}", src, targetWidthPx, targetHeightPx);
                return;
            }

            Path path = Paths.get(src);
            if (!Files.exists(path)) {
                log.warn("图片文件不存在: {}", src);
                return;
            }
            int pictureType = toPictureType(img.getType());
            XWPFRun run = para.createRun();
            try (InputStream is = Files.newInputStream(path)) {
                run.addPicture(is, pictureType, path.getFileName().toString(),
                        Units.toEMU(targetWidthPx), Units.toEMU(targetHeightPx));
            }
        } catch (Exception e) {
            log.error("插入图片失败: src={}, error={}", src, e.getMessage());
        }
    }

    private int toPictureType(Image.ImageType type) {
        if (type == null) {
            return PICTURE_TYPE_PNG;
        }
        switch (type) {
            case JPEG:
                return XWPFDocument.PICTURE_TYPE_JPEG;
            case GIF:
                return XWPFDocument.PICTURE_TYPE_GIF;
            default:
                return PICTURE_TYPE_PNG;
        }
    }

    // ===== 图表处理（ZIP 级别）=====

    /**
     * 将 [suredt.chart:xxx] 占位符替换为 Word 原生 Chart
     * 需要操作 DOCX 的 ZIP 结构：写入 chart.xml + 更新 rels
     */
    private byte[] processChartsInZip(byte[] docxBytes, List<ChartPlaceholderInfo> chartPlaceholders) throws Exception {
        if (chartPlaceholders.isEmpty()) {
            return docxBytes;
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        // 预先构建 chartRels（document.xml.rels 可能在 document.xml 之前出现）
        List<String> chartRels = new ArrayList<>();
        for (int i = 0; i < chartPlaceholders.size(); i++) {
            int n = i + 1;
            String chartId = CHART_REL_ID_PREFIX + n;
            chartRels.add("<Relationship Id=\"" + chartId +
                    "\" Type=\"" + REL_TYPE_CHART + "\"" +
                    " Target=\"charts/chart" + n + ".xml\"/>\n");
        }

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(docxBytes));
             ZipOutputStream zos = new ZipOutputStream(bos)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                byte[] content = toByteArray(zis);
                zis.closeEntry();

                if (DOCX_DOCUMENT_XML.equals(entry.getName())) {
                    String docXml = new String(content, SimpleDocTemplateConstant.CHARSET_UTF8);
                    docXml = insertChartsIntoDocument(docXml, chartPlaceholders);
                    zos.putNextEntry(new ZipEntry(entry.getName()));
                    zos.write(docXml.getBytes(SimpleDocTemplateConstant.CHARSET_UTF8));
                } else if (DOCX_DOCUMENT_RELS.equals(entry.getName())) {
                    String rels = new String(content, SimpleDocTemplateConstant.CHARSET_UTF8);
                    int insertPos = rels.lastIndexOf("</Relationships>");
                    if (insertPos > 0) {
                        StringBuilder sb = new StringBuilder(rels);
                        for (String rel : chartRels) {
                            sb.insert(insertPos, rel);
                        }
                        rels = sb.toString();
                    }
                    zos.putNextEntry(new ZipEntry(entry.getName()));
                    zos.write(rels.getBytes(SimpleDocTemplateConstant.CHARSET_UTF8));
                } else if (DOCX_CONTENT_TYPES.equals(entry.getName())) {
                    String ct = new String(content, SimpleDocTemplateConstant.CHARSET_UTF8);
                    int ctInsert = ct.lastIndexOf("</Types>");
                    if (ctInsert > 0) {
                        StringBuilder sb = new StringBuilder(ct);
                        for (int ci = 0; ci < chartPlaceholders.size(); ci++) {
                            String override = "<Override PartName=\"/word/charts/chart" + (ci + 1) + ".xml\" " +
                                    "ContentType=\"" + CT_CHART + "\"/>";
                            if (!ct.contains("chart" + (ci + 1) + ".xml")) {
                                sb.insert(ctInsert, override);
                            }
                        }
                        if (!ct.contains("Extension=\"xlsx\"")) {
                            sb.insert(ctInsert, "<Default Extension=\"xlsx\" ContentType=\"" + CT_XLSX + "\"/>");
                        }
                        ct = sb.toString();
                    }
                    zos.putNextEntry(new ZipEntry(entry.getName()));
                    zos.write(ct.getBytes(SimpleDocTemplateConstant.CHARSET_UTF8));
                } else if (entry.getName().startsWith(DOCX_CHARTS_DIR) || entry.getName().startsWith(DOCX_EMBEDDINGS_DIR)) {
                    // 跳过原有 chart/embedding，由新生成的覆盖
                    continue;
                } else {
                    zos.putNextEntry(new ZipEntry(entry.getName()));
                    zos.write(content);
                }
                zos.closeEntry();
            }

            // 写入所有 chart.xml + chart rels + embedded xlsx
            for (int i = 0; i < chartPlaceholders.size(); i++) {
                Chart chart = chartPlaceholders.get(i).chart;
                int n = i + 1;

                // chart.xml（引用 externalData）
                String chartXml = generateChartXml(chart);
                zos.putNextEntry(new ZipEntry(String.format(DOCX_CHART_XML_TMPL, n)));
                zos.write(chartXml.getBytes(SimpleDocTemplateConstant.CHARSET_UTF8));
                zos.closeEntry();

                // chart rels（链接到 embedded xlsx）
                String chartRelsXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                        "<Relationships xmlns=\"" + NS_RELATIONSHIPS + "\">" +
                        "<Relationship Id=\"rId1\" " +
                        "Type=\"" + REL_TYPE_PACKAGE + "\" " +
                        "Target=\"../embeddings/Microsoft_Office_Chart" + n + ".xlsx\"/>" +
                        "</Relationships>";
                zos.putNextEntry(new ZipEntry(String.format(DOCX_CHART_RELS_TMPL, n)));
                zos.write(chartRelsXml.getBytes(SimpleDocTemplateConstant.CHARSET_UTF8));
                zos.closeEntry();

                // embedded xlsx（含真实数据）
                zos.putNextEntry(new ZipEntry(String.format(DOCX_EMBEDDING_TMPL, n)));
                zos.write(generateEmbeddedXlsx(chart));
                zos.closeEntry();
            }
        }

        return bos.toByteArray();
    }

    private String insertChartsIntoDocument(String docXml, List<ChartPlaceholderInfo> chartPlaceholders) {
        for (int i = 0; i < chartPlaceholders.size(); i++) {
            ChartPlaceholderInfo info = chartPlaceholders.get(i);
            Chart chart = info.chart;
            int targetWidth = chart.getWidth() > 0 ? chart.getWidth() : DEFAULT_CONTENT_WIDTH_PX;
            int targetHeight = chart.getHeight() > 0 ? chart.getHeight() : DEFAULT_CHART_HEIGHT_PX;
            int chartNum = i + 1;
            long cx = Units.toEMU(targetWidth);
            long cy = Units.toEMU(targetHeight);
            String chartId = CHART_REL_ID_PREFIX + chartNum;

            String drawingXml =
                    "<w:drawing xmlns:wp=\"" + NS_WP + "\">" +
                            "<wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\">" +
                            "<wp:extent cx=\"" + cx + "\" cy=\"" + cy + "\"/>" +
                            "<wp:effectExtent l=\"0\" t=\"0\" r=\"0\" b=\"0\"/>" +
                            "<wp:docPr id=\"" + (CHART_DOC_PR_ID_OFFSET + i) + "\" name=\"Chart " + chartNum + "\"/>" +
                            "<wp:cNvGraphicFramePr/>" +
                            "<a:graphic xmlns:a=\"" + NS_A + "\">" +
                            "<a:graphicData uri=\"" + NS_C + "\">" +
                            "<c:chart xmlns:c=\"" + NS_C + "\" " +
                            "xmlns:r=\"" + NS_R + "\" r:id=\"" + chartId + "\"/>" +
                            "</a:graphicData>" +
                            "</a:graphic>" +
                            "</wp:inline>" +
                            "</w:drawing>";

            // 找到包含 marker 的段落，整段替换为 chart drawing
            int markerIdx = docXml.indexOf(info.marker);
            if (markerIdx >= 0) {
                // 找段落起始 <w:p 或 <w:p>
                int paraStart = markerIdx;
                while (paraStart > 0) {
                    paraStart = docXml.lastIndexOf("<w:p", paraStart - 1);
                    if (paraStart < 0) break;
                    char c = docXml.charAt(paraStart + 4);
                    if (c == '>' || c == ' ') break;
                }
                // 找段落结束 </w:p>
                int paraEnd = docXml.indexOf("</w:p>", markerIdx);
                if (paraStart >= 0 && paraEnd > paraStart) {
                    paraEnd += "</w:p>".length();
                    String chartPara = "<w:p><w:r>" + drawingXml + "</w:r></w:p>";
                    docXml = docXml.substring(0, paraStart) + chartPara + docXml.substring(paraEnd);
                }
            }
        }
        return docXml;
    }

    private byte[] toByteArray(InputStream is) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[IO_BUFFER_SIZE];
        int len;
        while ((len = is.read(buf)) != -1) bos.write(buf, 0, len);
        return bos.toByteArray();
    }

    private String generateChartXml(Chart chart) {
        int titleFontSize = chart.getTitleFontSize();
        String legendPosCode = chart.getLegendPosition() != null
                ? chart.getLegendPosition().getCode() : Chart.LegendPosition.BOTTOM.getCode();

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        sb.append("<c:chartSpace xmlns:c=\"").append(NS_C).append("\" ");
        sb.append("xmlns:a=\"").append(NS_A).append("\" ");
        sb.append("xmlns:r=\"").append(NS_R).append("\">\n");
        sb.append("  <c:date1904 val=\"0\"/>\n");
        sb.append("  <c:lang val=\"").append(CHART_LANG).append("\"/>\n");
        sb.append("  <c:roundedCorners val=\"0\"/>\n");
        sb.append("  <c:chart>\n");
        sb.append("    <c:title>\n");
        sb.append("      <c:tx><c:rich>\n");
        sb.append("        <a:bodyPr/><a:lstStyle/>\n");
        sb.append("        <a:p><a:pPr><a:defRPr sz=\"").append(titleFontSize).append("\" b=\"1\"/></a:pPr>\n");
        sb.append("          <a:r><a:rPr lang=\"").append(CHART_LANG).append("\" sz=\"").append(titleFontSize).append("\" b=\"1\"/>\n");
        sb.append("            <a:t>").append(escapeXml(chart.getTitle())).append("</a:t>\n");
        sb.append("          </a:r></a:p>\n");
        sb.append("      </c:rich></c:tx>\n");
        sb.append("      <c:overlay val=\"0\"/>\n");
        sb.append("    </c:title>\n");
        sb.append("    <c:autoTitleDeleted val=\"0\"/>\n");
        sb.append("    <c:plotArea>\n");

        Chart.ChartType type = chart.getChartType();
        if (type == Chart.ChartType.PIE) {
            sb.append("      <c:pieChart>\n");
            sb.append("        <c:varyColors val=\"1\"/>\n");
            for (int si = 0; si < chart.getSeries().size(); si++) {
                appendPieSeries(sb, si, chart);
            }
            sb.append("        <c:dLbls>\n");
            sb.append("          <c:showLegendKey val=\"0\"/><c:showVal val=\"1\"/>\n");
            sb.append("          <c:showCatName val=\"1\"/><c:showSerName val=\"0\"/>\n");
            sb.append("          <c:showPercent val=\"1\"/>\n");
            sb.append("        </c:dLbls>\n");
            sb.append("        <c:firstSliceAng val=\"0\"/>\n");
            sb.append("      </c:pieChart>\n");
        } else if (type == Chart.ChartType.BAR) {
            sb.append("      <c:barChart>\n");
            sb.append("        <c:barDir val=\"col\"/>\n");
            sb.append("        <c:grouping val=\"clustered\"/>\n");
            for (int si = 0; si < chart.getSeries().size(); si++) {
                appendCategorySeries(sb, si, chart);
            }
            sb.append("        <c:dLbls>\n");
            sb.append("          <c:showLegendKey val=\"0\"/><c:showVal val=\"0\"/>\n");
            sb.append("          <c:showCatName val=\"0\"/><c:showSerName val=\"0\"/>\n");
            sb.append("        </c:dLbls>\n");
            sb.append("        <c:gapWidth val=\"150\"/>\n");
            sb.append("        <c:axId val=\"1\"/><c:axId val=\"2\"/>\n");
            sb.append("      </c:barChart>\n");
        } else {
            // LINE (default)
            sb.append("      <c:lineChart>\n");
            sb.append("        <c:grouping val=\"standard\"/>\n");
            for (int si = 0; si < chart.getSeries().size(); si++) {
                appendCategorySeries(sb, si, chart);
            }
            sb.append("        <c:dLbls>\n");
            sb.append("          <c:showLegendKey val=\"0\"/><c:showVal val=\"0\"/>\n");
            sb.append("        </c:dLbls>\n");
            if (chart.isShowMarker()) {
                sb.append("        <c:marker val=\"1\"/>\n");
            }
            if (chart.isSmooth()) {
                sb.append("        <c:smooth val=\"1\"/>\n");
            }
            sb.append("        <c:axId val=\"1\"/><c:axId val=\"2\"/>\n");
            sb.append("      </c:lineChart>\n");
        }

        // 坐标轴（饼图不需要）
        if (type != Chart.ChartType.PIE) {
            sb.append("      <c:catAx>\n");
            sb.append("        <c:axId val=\"1\"/>\n");
            sb.append("        <c:scaling><c:orientation val=\"minMax\"/></c:scaling>\n");
            sb.append("        <c:delete val=\"0\"/><c:axPos val=\"b\"/>\n");
            sb.append("        <c:numFmt formatCode=\"General\" sourceLinked=\"0\"/>\n");
            sb.append("        <c:majorTickMark val=\"out\"/><c:minorTickMark val=\"none\"/>\n");
            sb.append("        <c:tickLblPos val=\"nextTo\"/>\n");
            sb.append("        <c:crossAx val=\"2\"/>\n");
            sb.append("      </c:catAx>\n");
            sb.append("      <c:valAx>\n");
            sb.append("        <c:axId val=\"2\"/>\n");
            sb.append("        <c:scaling><c:orientation val=\"minMax\"/></c:scaling>\n");
            sb.append("        <c:delete val=\"0\"/><c:axPos val=\"l\"/>\n");
            if (chart.isShowGridLines()) {
                sb.append("        <c:majorGridlines/>\n");
            }
            sb.append("        <c:numFmt formatCode=\"General\" sourceLinked=\"0\"/>\n");
            sb.append("        <c:majorTickMark val=\"out\"/><c:minorTickMark val=\"none\"/>\n");
            sb.append("        <c:tickLblPos val=\"nextTo\"/>\n");
            sb.append("        <c:crossAx val=\"1\"/>\n");
            sb.append("      </c:valAx>\n");
        }

        sb.append("    </c:plotArea>\n");
        sb.append("    <c:legend>\n");
        sb.append("      <c:legendPos val=\"").append(legendPosCode).append("\"/>\n");
        sb.append("      <c:overlay val=\"0\"/>\n");
        sb.append("    </c:legend>\n");
        sb.append("    <c:plotVisOnly val=\"1\"/>\n");
        sb.append("    <c:dispBlanksAs val=\"gap\"/>\n");
        sb.append("  </c:chart>\n");
        sb.append("  <c:externalData r:id=\"rId1\"><c:autoUpdate val=\"0\"/></c:externalData>\n");
        sb.append("  <c:spPr><a:noFill/></c:spPr>\n");
        sb.append("</c:chartSpace>\n");
        return sb.toString();
    }

    /**
     * 将系列索引转为 Excel 列字母（si=0→B, si=1→C, ...）
     */
    private String columnLetter(int si) {
        return String.valueOf((char) ('B' + si));
    }

    private void appendCategorySeries(StringBuilder sb, int si, Chart chart) {
        Chart.Series s = chart.getSeries().get(si);
        String seriesColor = s.getColor() != null ? s.getColor() : CHART_COLOR_DEFAULT;
        appendSeriesCore(sb, si, chart, s, seriesColor);
    }

    /**
     * 生成饼图的 series XML
     */
    private void appendPieSeries(StringBuilder sb, int si, Chart chart) {
        Chart.Series s = chart.getSeries().get(si);
        String seriesColor = s.getColor() != null ? s.getColor() : CHART_COLOR_DEFAULT;
        appendSeriesCore(sb, si, chart, s, seriesColor);
    }

    /**
     * series XML 共用结构
     */
    private void appendSeriesCore(StringBuilder sb, int si, Chart chart, Chart.Series s, String seriesColor) {
        int catCount = chart.getCategories().size();
        String col = columnLetter(si);
        sb.append("        <c:ser>\n");
        sb.append("          <c:idx val=\"").append(si).append("\"/>\n");
        sb.append("          <c:order val=\"").append(si).append("\"/>\n");
        sb.append("          <c:tx><c:v>").append(escapeXml(s.getName())).append("</c:v></c:tx>\n");
        sb.append("          <c:spPr><a:solidFill><a:srgbClr val=\"").append(seriesColor).append("\"/></a:solidFill></c:spPr>\n");
        sb.append("          <c:cat><c:strRef>\n");
        sb.append("            <c:f>").append(CHART_SHEET_NAME).append("!$A$2:$A$").append(catCount + 1).append("</c:f>\n");
        sb.append("            <c:strCache><c:ptCount val=\"").append(catCount).append("\"/>\n");
        for (int ci = 0; ci < catCount; ci++) {
            sb.append("              <c:pt idx=\"").append(ci).append("\"><c:v>").append(escapeXml(chart.getCategories().get(ci))).append("</c:v></c:pt>\n");
        }
        sb.append("            </c:strCache></c:strRef></c:cat>\n");
        sb.append("          <c:val><c:numRef>\n");
        sb.append("            <c:f>").append(CHART_SHEET_NAME).append("!$").append(col).append("$2:$").append(col).append("$").append(catCount + 1).append("</c:f>\n");
        sb.append("            <c:numCache><c:formatCode>General</c:formatCode><c:ptCount val=\"").append(s.getValues().size()).append("\"/>\n");
        for (int vi = 0; vi < s.getValues().size(); vi++) {
            sb.append("              <c:pt idx=\"").append(vi).append("\"><c:v>").append(s.getValues().get(vi)).append("</c:v></c:pt>\n");
        }
        sb.append("            </c:numCache></c:numRef></c:val>\n");
        sb.append("        </c:ser>\n");
    }

    /**
     * 生成 embedded xlsx 字节（含 chart 数据，供 Word 双击编辑）
     */
    private byte[] generateEmbeddedXlsx(Chart chart) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet(CHART_SHEET_NAME);
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue(CHART_CATEGORY_HEADER);
            for (int si = 0; si < chart.getSeries().size(); si++) {
                header.createCell(si + 1).setCellValue(chart.getSeries().get(si).getName());
            }
            for (int ci = 0; ci < chart.getCategories().size(); ci++) {
                Row row = sheet.createRow(ci + 1);
                row.createCell(0).setCellValue(chart.getCategories().get(ci));
                for (int si = 0; si < chart.getSeries().size(); si++) {
                    List<Number> vals = chart.getSeries().get(si).getValues();
                    if (ci < vals.size()) {
                        row.createCell(si + 1).setCellValue(vals.get(ci).doubleValue());
                    }
                }
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        }
    }

    private String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    // ===== 表格处理 =====

    private void processAllTables(XWPFDocument doc, Map<String, Object> data, int contentWidthPx) {
        for (XWPFTable table : doc.getTables()) {
            processTableRows(table, data, contentWidthPx);
        }
    }

    @SuppressWarnings("unchecked")
    private void processTableRows(XWPFTable table, Map<String, Object> data, int contentWidthPx) {
        if (table.getRows().isEmpty()) return;

        for (int i = 0; i < table.getRows().size(); i++) {
            XWPFTableRow row = table.getRows().get(i);
            String forKey = findForKey(row);
            if (forKey == null) {
                // 普通行：处理变量替换和图片占位符
                replaceRowText(row, data, null, contentWidthPx);
                continue;
            }

            // 找到对应 endfor 所在行
            int endforRowIdx = findEndforRowIndex(table, i + 1, forKey);
            if (endforRowIdx == -1) {
                log.warn("未找到 [suredt.endfor:{}] 对应行，跳过循环块", forKey);
                continue;
            }

            // 获取数据列表
            Object listObj = data.get(forKey);
            if (!(listObj instanceof List)) {
                log.warn("数据中 key={} 不是 List 类型，删除循环块", forKey);
                deleteRows(table, i, endforRowIdx);
                i = i - 1;  // 删除后下一行在 index i，i-1 后 i++ 正好到达
                continue;
            }

            List<Map<String, Object>> itemList = (List<Map<String, Object>>) listObj;
            if (itemList.isEmpty()) {
                // 列表为空：删除整个循环块（含 for/end 行及中间的数据行）
                deleteRows(table, i, endforRowIdx);
                i = i - 1;
                continue;
            }

            // 模板行 = for 行之后的第一行
            int tplRowIdx = i + 1;
            if (tplRowIdx > endforRowIdx - 1) {
                log.warn("[suredt.for:{}] 和 [suredt.endfor:{}] 之间无数据行，跳过", forKey, forKey);
                deleteRows(table, i, endforRowIdx);
                i = i - 1;
                continue;
            }
            XWPFTableRow tplRow = table.getRows().get(tplRowIdx);

            // 先在模板行之后插入数据行（在删除任何行之前，避免索引错乱）
            int insertPos = table.getRows().indexOf(tplRow) + 1;
            for (Map<String, Object> item : itemList) {
                XWPFTableRow newRow = table.insertNewTableRow(insertPos);
                copyRowStructure(tplRow, newRow);
                replaceRowText(newRow, data, item, contentWidthPx);
                insertPos++;
            }

            // 删除模板行、endfor 行、for 行（从后往前删，避免索引偏移）
            table.removeRow(table.getRows().indexOf(tplRow));
            // endfor 行的引用可能已变，重新查找
            int newEndforIdx = findEndforRowIndex(table, i + 1, forKey);
            if (newEndforIdx != -1) {
                table.removeRow(newEndforIdx);
            }
            // 删除 for 行
            table.removeRow(i);

            // 跳过已处理的行（for 行已删，下一行在 index i，i-1 后 i++ 正好到达）
            i = i - 1;
        }
    }

    /**
     * 替换行中所有单元格的文本和图片占位符
     *
     * @param item 循环项，为 null 时使用 data
     */
    private void replaceRowText(XWPFTableRow row, Map<String, Object> data, Map<String, Object> item, int contentWidthPx) {
        Map<String, Object> ctx = item != null ? item : data;
        for (XWPFTableCell cell : row.getTableCells()) {
            // 取单元格宽度（EMU），转换为 px
            int cellWidthPx = (int) (getCellWidth(cell) / EMUS_PER_PX);
            for (XWPFParagraph para : cell.getParagraphs()) {
                // [suredt.var:xxx] 替换，数据源：循环项（item）或主数据（data）
                processParagraphText(para, ctx);
                // 图片占位符：主数据中的 Image（图片 key 不在 item 中）
                replaceImgPlaceholder(para, data, cellWidthPx);
            }
        }
    }

    private long getCellWidth(XWPFTableCell cell) {
        CTTc tc = cell.getCTTc();
        if (tc.isSetTcPr()) {
            CTTcPr tcPr = tc.getTcPr();
            if (tcPr.isSetTcW()) {
                Object wObj = tcPr.getTcW().getW();
                return new java.math.BigInteger(wObj.toString()).longValue();
            }
        }
        return DEFAULT_CONTENT_WIDTH_PX * EMUS_PER_PX;
    }

    // ===== 工具方法 =====

    private String replaceVar(String text, Map<String, Object> data) {
        Matcher m = varPattern.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String key = m.group(1);
            Object val = data.get(key);
            String replacement = val != null ? val.toString() : "";
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String getParaText(XWPFParagraph para) {
        StringBuilder sb = new StringBuilder();
        for (XWPFRun run : para.getRuns()) {
            String t = run.getText(0);
            if (t != null) sb.append(t);
        }
        return sb.toString();
    }

    private void clearPara(XWPFParagraph para) {
        for (int i = para.getRuns().size() - 1; i >= 0; i--) {
            para.removeRun(i);
        }
    }

    /**
     * 判断行是否包含 [suredt.for:key]
     */
    private String findForKey(XWPFTableRow row) {
        for (XWPFTableCell cell : row.getTableCells()) {
            for (XWPFParagraph para : cell.getParagraphs()) {
                String text = getParaText(para);
                if (tagHelper.matches(text, tagHelper.forPrefix())) {
                    return tagHelper.extractKey(text, tagHelper.forPrefix());
                }
            }
        }
        return null;
    }

    private int findEndforRowIndex(XWPFTable table, int fromRow, String forKey) {
        String expectedTag = tagHelper.endforTag(forKey);
        List<XWPFTableRow> rows = table.getRows();
        for (int i = fromRow; i < rows.size(); i++) {
            XWPFTableRow row = rows.get(i);
            for (XWPFTableCell cell : row.getTableCells()) {
                for (XWPFParagraph para : cell.getParagraphs()) {
                    String text = getParaText(para);
                    if (text.contains(expectedTag)) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    private void copyRowStructure(XWPFTableRow src, XWPFTableRow dst) {
        while (dst.getTableCells().size() < src.getTableCells().size()) {
            dst.addNewTableCell();
        }
        for (int i = 0; i < src.getTableCells().size(); i++) {
            XWPFTableCell srcCell = src.getTableCells().get(i);
            XWPFTableCell dstCell = dst.getTableCells().get(i);
            for (int p = dstCell.getParagraphs().size() - 1; p >= 0; p--) {
                dstCell.removeParagraph(p);
            }
            for (XWPFParagraph srcPara : srcCell.getParagraphs()) {
                XWPFParagraph dstPara = dstCell.addParagraph();
                for (XWPFRun srcRun : srcPara.getRuns()) {
                    dstPara.createRun().setText(srcRun.getText(0));
                }
            }
        }
    }

    private void deleteRows(XWPFTable table, int fromInclusive, int toInclusive) {
        for (int i = toInclusive; i >= fromInclusive; i--) {
            if (i < table.getRows().size()) {
                table.removeRow(i);
            }
        }
    }

    @Override
    public String supportedSuffix() {
        return SimpleDocTemplateConstant.SUFFIX_DOCX;
    }

    /**
     * 图表占位符信息：数据 key、Chart 对象、在 document.xml 中的唯一标记文本
     */
    private static class ChartPlaceholderInfo {
        final String key;
        final Chart chart;
        final String marker;

        ChartPlaceholderInfo(String key, Chart chart, String marker) {
            this.key = key;
            this.chart = chart;
            this.marker = marker;
        }
    }
}