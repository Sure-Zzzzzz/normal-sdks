package io.github.surezzzzzz.sdk.template.doc.handler.pdf;

import io.github.surezzzzzz.sdk.template.doc.annotation.SimpleDocTemplateComponent;
import io.github.surezzzzzz.sdk.template.doc.configuration.SimpleDocTemplateProperties;
import io.github.surezzzzzz.sdk.template.doc.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.template.doc.constant.SimpleDocTemplateConstant;
import io.github.surezzzzzz.sdk.template.doc.converter.WordToXhtmlConverter;
import io.github.surezzzzzz.sdk.template.doc.exception.DocxToPdfFailedException;
import io.github.surezzzzzz.sdk.template.doc.model.Chart;
import io.github.surezzzzzz.sdk.template.doc.renderer.ChartToPngRenderer;
import io.github.surezzzzzz.sdk.template.doc.result.PdfRenderResult;
import io.github.surezzzzzz.sdk.template.doc.support.OoxmlChartParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * PDF 输出处理器 - DOCX → PDF 双路径转换
 * <p>
 * 不实现 OutputHandler 接口（PdfRenderResult 不是 Document）。
 * 非 Spring 管理的 Bean，由 PdfRenderResult 直接 new 创建。
 * <ul>
 *   <li>普通路径：无 chart，DOCX → XHTML → PDF</li>
 *   <li>Chart 路径：chart marker 替换为 PNG 图片后，DOCX → XHTML → PDF</li>
 *   <li>直接转换：传入已有 DOCX 字节，零门槛转 PDF</li>
 * </ul>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleDocTemplateComponent
@RequiredArgsConstructor
public class PdfOutputHandler {

    private static final int IO_BUFFER_SIZE = SimpleDocTemplateConstant.IO_BUFFER_SIZE;

    // OOXML 命名空间
    private static final String NS_WP = SimpleDocTemplateConstant.NS_WP;
    private static final String NS_A = SimpleDocTemplateConstant.NS_A;
    private static final String NS_R = SimpleDocTemplateConstant.NS_R;
    private static final String REL_TYPE_IMAGE = SimpleDocTemplateConstant.REL_TYPE_IMAGE;

    // DOCX ZIP 内部路径
    private static final String DOCX_DOCUMENT_XML = SimpleDocTemplateConstant.DOCX_DOCUMENT_XML;
    private static final String DOCX_DOCUMENT_RELS = SimpleDocTemplateConstant.DOCX_DOCUMENT_RELS;
    private static final String DOCX_CONTENT_TYPES = SimpleDocTemplateConstant.DOCX_CONTENT_TYPES;
    private static final String DOCX_CHARTS_DIR = SimpleDocTemplateConstant.DOCX_CHARTS_DIR;
    private static final String DOCX_EMBEDDINGS_DIR = SimpleDocTemplateConstant.DOCX_EMBEDDINGS_DIR;
    private static final String DOCX_MEDIA_DIR = SimpleDocTemplateConstant.DOCX_MEDIA_DIR;

    // Chart marker
    private static final Pattern CHART_MARKER_PATTERN = Pattern.compile(
            SimpleDocTemplateConstant.CHART_MARKER_PREFIX + "(\\d+)");

    /**
     * rels 中 rId 数字后缀提取
     */
    private static final Pattern REL_ID_PATTERN = Pattern.compile("rId(\\d+)");

    /**
     * 默认 chart 图片宽度（EMU），与 WordRenderer 一致
     */
    private static final long DEFAULT_CHART_WIDTH_EMU = SimpleDocTemplateConstant.CONTENT_WIDTH_PX_FALLBACK * 914400L / 96;
    private static final long DEFAULT_CHART_HEIGHT_EMU = 300L * 914400L / 96;

    /**
     * 1 px @ 96 DPI = 914400/96 = 9525 EMU
     */
    private static final long EMU_PER_PIXEL = 914400L / 96;

    /**
     * Chart 图片在 DOCX 内部的命名前缀及 docPr id 起始偏移
     */
    private static final String CHART_IMAGE_NAME_PREFIX = "suredt-chart-";
    private static final String CHART_IMAGE_NAME_EXT = ".png";
    private static final String CHART_IMAGE_DRAWING_NAME = "Chart Image ";
    private static final int CHART_IMAGE_DOC_PR_ID_OFFSET = 100;
    private static final int CHART_RUN_ID_LEN = 8;
    private static final String PIC_NAMESPACE = "http://schemas.openxmlformats.org/drawingml/2006/picture";

    /**
     * 字体文件过滤（.ttf / .ttc）
     */
    private static final FilenameFilter FONT_FILE_FILTER = (dir, name) -> {
        String lower = name.toLowerCase();
        return lower.endsWith(".ttf") || lower.endsWith(".ttc");
    };

    private final OoxmlChartParser ooxmlChartParser = new OoxmlChartParser();

    /**
     * 全局唯一字体来源：properties.fontPaths
     */
    private final SimpleDocTemplateProperties properties;

    // ===== PdfRenderResult 输出 =====

    /**
     * 双路径分发：根据是否有 chart PNG 决定转换链路
     */
    public byte[] toPdfBytes(PdfRenderResult result) {
        byte[] docxBytes = result.getDocxBytes();
        List<byte[]> pngList = result.getChartPngList();

        if (pngList.isEmpty()) {
            return convertDocxToPdf(docxBytes);
        } else {
            byte[] imageDocxBytes = replaceChartsWithImages(docxBytes, pngList);
            return convertDocxToPdf(imageDocxBytes);
        }
    }

    /**
     * 输出到文件
     */
    public void writeToFile(PdfRenderResult result, String filePath) {
        try {
            byte[] pdfBytes = toPdfBytes(result);
            Files.write(Paths.get(filePath), pdfBytes);
        } catch (IOException e) {
            throw DocxToPdfFailedException.conversionFailed(String.format(ErrorMessage.PDF_FILE_WRITE_FAILED, filePath), e);
        }
    }

    /**
     * 输出到流
     */
    public void writeToStream(PdfRenderResult result, OutputStream os) {
        try {
            os.write(toPdfBytes(result));
            os.flush();
        } catch (IOException e) {
            throw DocxToPdfFailedException.conversionFailed(ErrorMessage.PDF_STREAM_WRITE_FAILED, e);
        }
    }

    // ===== 直接转换（零门槛）=====

    /**
     * 直接转换：传入 DOCX 字节流，返回 PDF 字节流
     * <p>
     * OOXML chart（原生 Word 图表）无法在 PDF 中渲染，对应位置留空。
     * 如需 chart 出现在 PDF 中，请使用 renderForPdf() 从模板渲染。
     */
    public byte[] convertToPdf(byte[] docxBytes) {
        return convertDocxToPdf(docxBytes);
    }

    /**
     * 直接转换：传入 DOCX 字节流，输出到流
     */
    public void convertToPdf(byte[] docxBytes, OutputStream os) throws IOException {
        os.write(convertDocxToPdf(docxBytes));
        os.flush();
    }

    // ===== 直接转换重载（1.1.1 补强）=====

    /**
     * 直接转换：传入 DOCX InputStream，返回 PDF 字节数组
     */
    public byte[] convertToPdf(InputStream inputStream) {
        return convertToPdf(toByteArray(inputStream));
    }

    // ===== 内部工具方法 =====

    private byte[] toByteArray(InputStream inputStream) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[IO_BUFFER_SIZE];
        int len;
        try {
            while ((len = inputStream.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            throw DocxToPdfFailedException.conversionFailed(ErrorMessage.PDF_DOCX_INPUT_STREAM_READ_FAILED, e);
        }
    }


    // ===== 核心转换 =====

    /**
     * 核心转换方法：DOCX → XHTML → PDF
     */
    private byte[] convertDocxToPdf(byte[] docxBytes) {
        try {
            // 0. 把 DOCX 内的 OOXML chart 解析成 Chart 模型 → JFreeChart 渲染 PNG → ZIP 级替换 c:chart 为 pic:pic
            //    openhtmltopdf 不认识 OOXML chart drawing，必须在转换前替换为图片，否则 PDF 里这个位置会留空。
            byte[] processedDocx = embedOoxmlChartsAsImages(docxBytes);

            ByteArrayOutputStream pdfOut = new ByteArrayOutputStream();
            com.openhtmltopdf.pdfboxout.PdfRendererBuilder builder =
                    new com.openhtmltopdf.pdfboxout.PdfRendererBuilder();
            // 1. 注册外部字体（properties.fontPaths），收集所有注册成功的 family name
            //    不再注册 DOCX 嵌入字体（odttf 子集字体导致 per-glyph fallback、字号视觉不一致）
            Set<String> registeredFamilies = registerFonts(builder);
            // 2. 用注册结果作为 XHTML font-family 白名单（出口和入口对齐）
            String xhtml = new WordToXhtmlConverter().toXhtml(processedDocx, registeredFamilies);
            builder.withHtmlContent(xhtml, null);
            builder.toStream(pdfOut);
            builder.run();

            return pdfOut.toByteArray();
        } catch (Exception e) {
            throw DocxToPdfFailedException.conversionFailed(e.getMessage(), e);
        }
    }

    // ===== 字体注册 =====

    /**
     * 注册字体到 builder，并返回所有注册成功的 family name 集合。
     * <p>
     * 仅从 properties.fontPaths 注册外部字体。每个字体文件会同时注册多个别名：
     * <ul>
     *   <li>真实 family name（AWT 解析得到，含中文名如「黑体」、英文名如「SimHei」）</li>
     *   <li>文件名（不含扩展名，如 simhei、msyh、simsun）</li>
     * </ul>
     * 这样 DOCX 中 fontFamily 写「SimHei」「黑体」「simhei」都能命中。
     * 不再注册 DOCX 嵌入字体：odttf 是子集字体，缺字时 per-glyph fallback 导致同段字号视觉不一致。
     *
     * @param builder PDF 渲染器
     * @return 已注册的 family name 集合
     */
    private Set<String> registerFonts(com.openhtmltopdf.pdfboxout.PdfRendererBuilder builder) {
        Set<String> registeredFamilies = new LinkedHashSet<>();

        List<File> fontFiles = resolveFontFiles();
        for (File fontFile : fontFiles) {
            // openhtmltopdf 的 useFont(File, family) 内部对 .ttc 走 TrueTypeCollection.processAllFonts，
            // 把集合内每个子字体都注册到 override family；.ttf 则直接 PDType0Font.load。
            // 我们走 File 路径即可同时覆盖 .ttf / .ttc，不再用 InputStream supplier
            // （InputStream 路径只支持单字体 TTF，对 TTC 会抛 "not a valid truetype font"）。

            // 真实 family name（含中文名/英文名）
            for (String family : readFontFamilyNames(fontFile)) {
                if (registerFontUnderName(builder, fontFile, family)) {
                    registeredFamilies.add(family);
                }
            }

            // 文件名兜底（如 simhei、msyh、simsun、simfang、simkai）
            String fileBaseName = stripExtension(fontFile.getName());
            if (registerFontUnderName(builder, fontFile, fileBaseName)) {
                registeredFamilies.add(fileBaseName);
            }

            log.info("外部字体注册成功: {}", fontFile.getAbsolutePath());
        }

        if (registeredFamilies.isEmpty()) {
            log.warn("未注册任何字体（fontPaths 为空或文件不存在），PDF 中中文可能显示为 #");
        } else {
            log.info("已注册字体族名共 {} 个: {}", registeredFamilies.size(), registeredFamilies);
        }
        return registeredFamilies;
    }

    /**
     * 解析 fontPaths 配置，展开目录为具体字体文件
     */
    private List<File> resolveFontFiles() {
        List<File> result = new ArrayList<>();
        List<String> fontPaths = properties.getFontPaths();
        if (fontPaths == null) {
            return result;
        }
        for (String path : fontPaths) {
            File file = new File(path);
            if (!file.exists()) {
                log.warn("字体路径不存在: {}", path);
                continue;
            }
            if (file.isDirectory()) {
                File[] fonts = file.listFiles(FONT_FILE_FILTER);
                if (fonts != null) {
                    for (File f : fonts) {
                        result.add(f);
                    }
                }
            } else {
                result.add(file);
            }
        }
        return result;
    }

    /**
     * 用指定 family name 注册字体到 builder（统一走 File 路径，TTC/TTF 都支持）
     */
    private boolean registerFontUnderName(com.openhtmltopdf.pdfboxout.PdfRendererBuilder builder,
                                          File fontFile, String familyName) {
        if (familyName == null || familyName.isEmpty()) {
            return false;
        }
        try {
            builder.useFont(fontFile, familyName);
            return true;
        } catch (Exception e) {
            log.debug("注册字体到族名 [{}] 失败: {} - {}", familyName, fontFile.getAbsolutePath(), e.getMessage());
            return false;
        }
    }

    /**
     * 读取字体文件的真实 family name 列表（通过 AWT Font.createFont 解析）
     * <p>
     * 中文字体在 TTF name 表里通常存了多个本地化名（中文「黑体」+ 英文「SimHei」），
     * AWT 默认只返回与 Locale 匹配的那一个。我们用 Locale.ENGLISH 和 Locale.SIMPLIFIED_CHINESE
     * 各取一次，把两个都暴露出去，让 DOCX 中无论用哪种语言写字体名都能命中。
     * <p>
     * TTC 文件取首个子字体的 family。
     */
    private List<String> readFontFamilyNames(File fontFile) {
        List<String> names = new ArrayList<>();
        java.awt.Font font;
        try (java.io.InputStream is = Files.newInputStream(fontFile.toPath())) {
            font = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, is);
        } catch (Exception e) {
            log.warn("读取字体真实族名失败: {} - {}", fontFile.getAbsolutePath(), e.getMessage());
            return names;
        }
        // AWT 的 family 受 Locale 影响：英文 Locale 取英文名，中文 Locale 取本地化名
        java.util.Locale[] locales = {java.util.Locale.ENGLISH, java.util.Locale.SIMPLIFIED_CHINESE};
        for (java.util.Locale locale : locales) {
            String family = font.getFamily(locale);
            if (family != null && !family.isEmpty() && !names.contains(family)) {
                names.add(family);
            }
        }
        return names;
    }

    private String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    // ===== 直接转换链路：OOXML chart → PNG → 替换 =====

    /**
     * 把 DOCX 内所有 OOXML chart drawing 替换为内嵌 PNG（直接转换链路使用）。
     * <p>
     * openhtmltopdf 无法渲染 &lt;c:chart&gt;，必须在 XHTML 转换前把 chart 变成图片。
     * 流程：
     * <ol>
     *   <li>扫描 ZIP，把 chart{N}.xml、各 *.xml.rels、document.xml/header*.xml/footer*.xml 都拿出来</li>
     *   <li>对每个引用 chart 的 XML（document/header/footer），在 rels 里查 rId → chart{N}.xml 完整路径</li>
     *   <li>解析 chart{N}.xml → 渲染 PNG → 写入 word/media/suredt-ooxml-chart-N.png</li>
     *   <li>对应 rels 增加新的 image rId（Type=image, Target=media/...png）</li>
     *   <li>XML 中 &lt;a:graphicData uri="...chart"&gt;&lt;c:chart r:id="..."/&gt;&lt;/a:graphicData&gt;
     *       换成 &lt;a:graphicData uri="...picture"&gt;&lt;pic:pic&gt;...&lt;/pic:pic&gt;&lt;/a:graphicData&gt;</li>
     * </ol>
     * 解析失败的 chart：保留原 c:chart 标签（XHTML 转换器会忽略），PDF 中留空，不阻断转换。
     */
    private byte[] embedOoxmlChartsAsImages(byte[] docxBytes) {
        try {
            // 1. 把 ZIP 全部读到内存：分类存放
            java.util.LinkedHashMap<String, byte[]> entries = new java.util.LinkedHashMap<>();
            try (ZipInputStream zis = new ZipInputStream(new java.io.ByteArrayInputStream(docxBytes))) {
                ZipEntry e;
                while ((e = zis.getNextEntry()) != null) {
                    entries.put(e.getName(), toByteArray(zis));
                    zis.closeEntry();
                }
            }

            // 2. 在 document/header/footer XML 里寻找 chart 引用
            //    rels 路径：xxx.xml → xxx.xml.rels（同名加 .rels 后缀，放在同目录的 _rels 子目录）
            ChartToPngRenderer pngRenderer = null;  // 懒加载，没 chart 就不初始化
            int chartImageCounter = 0;

            // 收集需要扫描的 XML：document.xml + word/header*.xml + word/footer*.xml
            List<String> xmlNames = new ArrayList<>();
            for (String name : entries.keySet()) {
                if (DOCX_DOCUMENT_XML.equals(name)) {
                    xmlNames.add(name);
                } else if (name.startsWith("word/header") && name.endsWith(".xml")) {
                    xmlNames.add(name);
                } else if (name.startsWith("word/footer") && name.endsWith(".xml")) {
                    xmlNames.add(name);
                }
            }

            for (String xmlName : xmlNames) {
                byte[] xmlBytes = entries.get(xmlName);
                if (xmlBytes == null) continue;
                String xml = new String(xmlBytes, StandardCharsets.UTF_8);

                // 找 <c:chart r:id="..."/> 或 <c:chart ... r:id="..." .../> 引用
                Pattern chartRefPattern = Pattern.compile(
                        "<c:chart\\s+[^>]*r:id=\"([^\"]+)\"[^>]*/?>");
                Matcher cm = chartRefPattern.matcher(xml);
                if (!cm.find()) {
                    continue;
                }

                // 这个 XML 对应的 rels：word/document.xml → word/_rels/document.xml.rels
                String relsName = relsNameFor(xmlName);
                byte[] relsBytes = entries.get(relsName);
                if (relsBytes == null) {
                    log.debug("XML {} 引用了 chart 但找不到 rels {}, 跳过", xmlName, relsName);
                    continue;
                }
                String relsXml = new String(relsBytes, StandardCharsets.UTF_8);

                // 当前 rels 中现有最大 rId（用于追加新的 image rId）
                int maxRelId = findMaxRelId(relsXml);

                // 重置 matcher 从头扫
                cm.reset();
                StringBuilder newXml = new StringBuilder();
                int lastEnd = 0;
                List<String> newRels = new ArrayList<>();

                while (cm.find()) {
                    String relId = cm.group(1);
                    // 在 rels 中查 rId → Target（chart{N}.xml 的相对路径）
                    String chartTarget = findRelTarget(relsXml, relId);
                    if (chartTarget == null) {
                        log.debug("rId {} 在 {} 里找不到 Target，保留原 chart 标签", relId, relsName);
                        continue;  // 保留原 chart（不替换），openhtmltopdf 会忽略
                    }
                    // 解析 Target 为 ZIP 内绝对路径（相对 word/）
                    String chartXmlPath = resolveTarget(xmlName, chartTarget);
                    byte[] chartXmlBytes = entries.get(chartXmlPath);
                    if (chartXmlBytes == null) {
                        log.debug("chart 文件 {} 不在 ZIP 内，保留原 chart 标签", chartXmlPath);
                        continue;
                    }

                    Chart chartModel = ooxmlChartParser.parse(chartXmlBytes);
                    if (chartModel == null) {
                        log.debug("chart {} 解析失败，PDF 对应位置留空", chartXmlPath);
                        continue;
                    }

                    // 从 document.xml/header*.xml/footer*.xml 中当前 c:chart 所在 drawing 的 wp:extent 抽取尺寸（EMU）
                    // 模板设计者已在 docx 里固定了图表的显示尺寸；PDF 中也应保持一致，避免 600×360 兜底导致比例失真。
                    int chartTagStart0 = cm.start();
                    int gdStart0 = xml.lastIndexOf("<a:graphicData", chartTagStart0);
                    long extentCx = extractGraphicSize(xml, gdStart0, "cx");
                    long extentCy = extractGraphicSize(xml, gdStart0, "cy");
                    int targetWpx = 0;
                    int targetHpx = 0;
                    if (extentCx > 0 && extentCy > 0) {
                        targetWpx = (int) Math.round(extentCx / (double) EMU_PER_PIXEL);
                        targetHpx = (int) Math.round(extentCy / (double) EMU_PER_PIXEL);
                    }

                    // 懒加载渲染器
                    if (pngRenderer == null) {
                        try {
                            pngRenderer = new ChartToPngRenderer(properties.getFontPaths());
                        } catch (Exception fe) {
                            log.warn("初始化 ChartToPngRenderer 失败，PDF 中所有 chart 留空: {}", fe.getMessage());
                            return docxBytes;  // 没字体直接放弃，链路其余部分照常
                        }
                    }

                    byte[] pngBytes;
                    try {
                        if (targetWpx > 0 && targetHpx > 0) {
                            pngBytes = pngRenderer.render(chartModel, targetWpx, targetHpx);
                        } else {
                            pngBytes = pngRenderer.render(chartModel);
                        }
                    } catch (Exception re) {
                        log.warn("chart {} 渲染 PNG 失败，PDF 对应位置留空: {}", chartXmlPath, re.getMessage());
                        continue;
                    }

                    chartImageCounter++;
                    String imageName = "suredt-ooxml-chart-" + chartImageCounter + ".png";
                    String imagePath = DOCX_MEDIA_DIR + imageName;
                    entries.put(imagePath, pngBytes);

                    // 追加 image rels（注意：每个 XML 用自己的 rels，每张图用一个新 rId）
                    String newRelId = "rId" + (++maxRelId);
                    newRels.add("<Relationship Id=\"" + newRelId + "\"" +
                            " Type=\"" + REL_TYPE_IMAGE + "\"" +
                            " Target=\"media/" + imageName + "\"/>");

                    // 替换：构造 pic:pic drawing，宿主仍是当前 c:chart 所在的 graphicData。
                    //      extent 必须用 PNG 真实像素尺寸，而非 OOXML 里的错误 extent，
                    //      否则图片被拉伸/压缩导致 PDF 里 chart 变形。
                    int[] realSize = readPngSize(pngBytes);
                    long cx, cy;
                    if (realSize != null) {
                        cx = (long) realSize[0] * EMU_PER_PIXEL;
                        cy = (long) realSize[1] * EMU_PER_PIXEL;
                    } else {
                        cx = DEFAULT_CHART_WIDTH_EMU;
                        cy = DEFAULT_CHART_HEIGHT_EMU;
                    }

                    int chartTagStart = cm.start();
                    int chartTagEnd = cm.end();
                    // 向前找最近 <a:graphicData
                    int gdStart = xml.lastIndexOf("<a:graphicData", chartTagStart);
                    int gdEnd = xml.indexOf("</a:graphicData>", chartTagEnd);

                    if (gdStart < 0 || gdEnd < 0 || gdStart > chartTagStart) {
                        // graphicData 边界异常，退化为只换 chart 标签
                        newXml.append(xml, lastEnd, chartTagStart);
                        newXml.append(buildPicDrawingFragment(newRelId, chartImageCounter, cx, cy));
                        lastEnd = chartTagEnd;
                    } else {
                        gdEnd += "</a:graphicData>".length();
                        newXml.append(xml, lastEnd, gdStart);
                        newXml.append("<a:graphicData uri=\"").append(PIC_NAMESPACE).append("\">");
                        newXml.append(buildPicInner(newRelId, chartImageCounter, cx, cy));
                        newXml.append("</a:graphicData>");
                        lastEnd = gdEnd;
                    }
                }

                if (lastEnd > 0) {
                    newXml.append(xml.substring(lastEnd));
                    entries.put(xmlName, newXml.toString().getBytes(StandardCharsets.UTF_8));

                    // 把新的 image rel 插入 rels
                    if (!newRels.isEmpty()) {
                        int insertPos = relsXml.lastIndexOf("</Relationships>");
                        if (insertPos > 0) {
                            StringBuilder sb = new StringBuilder(relsXml);
                            for (String r : newRels) {
                                sb.insert(insertPos, r);
                                insertPos += r.length();
                            }
                            entries.put(relsName, sb.toString().getBytes(StandardCharsets.UTF_8));
                        }
                    }
                }
            }

            // 没产生任何替换 → 原样返回，避免重写 ZIP 浪费
            if (chartImageCounter == 0) {
                return docxBytes;
            }

            byte[] contentTypes = entries.get(DOCX_CONTENT_TYPES);
            if (contentTypes != null) {
                entries.put(DOCX_CONTENT_TYPES, ensurePngContentType(contentTypes));
            }

            // 3. 重新打包 ZIP
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(out)) {
                for (java.util.Map.Entry<String, byte[]> e : entries.entrySet()) {
                    zos.putNextEntry(new ZipEntry(e.getKey()));
                    zos.write(e.getValue());
                    zos.closeEntry();
                }
            }
            return out.toByteArray();
        } catch (Exception e) {
            log.warn("OOXML chart → PNG 替换失败，回退原 DOCX: {}", e.getMessage());
            return docxBytes;
        }
    }

    /**
     * xxx/document.xml → xxx/_rels/document.xml.rels
     */
    private String relsNameFor(String xmlName) {
        int slash = xmlName.lastIndexOf('/');
        if (slash < 0) {
            return "_rels/" + xmlName + ".rels";
        }
        return xmlName.substring(0, slash) + "/_rels/" + xmlName.substring(slash + 1) + ".rels";
    }

    /**
     * 在 rels 字符串中按 Id 查 Target
     */
    private String findRelTarget(String relsXml, String relId) {
        Pattern p = Pattern.compile(
                "<Relationship\\s+[^>]*Id=\"" + Pattern.quote(relId) + "\"[^>]*Target=\"([^\"]+)\"");
        Matcher m = p.matcher(relsXml);
        if (m.find()) return m.group(1);
        // Id 和 Target 顺序可能反过来
        p = Pattern.compile(
                "<Relationship\\s+[^>]*Target=\"([^\"]+)\"[^>]*Id=\"" + Pattern.quote(relId) + "\"");
        m = p.matcher(relsXml);
        if (m.find()) return m.group(1);
        return null;
    }

    /**
     * Target 是相对当前 XML 所在目录的路径，转成 ZIP 内绝对路径
     */
    private String resolveTarget(String hostXml, String target) {
        if (target.startsWith("/")) {
            return target.substring(1);
        }
        // hostXml 所在目录
        int slash = hostXml.lastIndexOf('/');
        String hostDir = slash > 0 ? hostXml.substring(0, slash + 1) : "";
        // 处理 ../
        String combined = hostDir + target;
        // 折叠 a/b/../c → a/c
        while (combined.contains("/../")) {
            int idx = combined.indexOf("/../");
            int prevSlash = combined.lastIndexOf('/', idx - 1);
            if (prevSlash < 0) {
                combined = combined.substring(idx + 4);
            } else {
                combined = combined.substring(0, prevSlash + 1) + combined.substring(idx + 4);
            }
        }
        return combined;
    }

    /**
     * 在 graphicData 之前的 wp:extent 标签里抽 cx/cy（EMU）。返回 0 表示没找到。
     */
    private long extractGraphicSize(String xml, int graphicDataStart, String attr) {
        if (graphicDataStart < 0) return 0;
        // 向前找最近的 <wp:extent
        int extentStart = xml.lastIndexOf("<wp:extent", graphicDataStart);
        if (extentStart < 0) return 0;
        int extentEnd = xml.indexOf("/>", extentStart);
        if (extentEnd < 0 || extentEnd > graphicDataStart) return 0;
        String tag = xml.substring(extentStart, extentEnd);
        Matcher m = Pattern.compile(attr + "=\"(\\d+)\"").matcher(tag);
        if (m.find()) {
            try {
                return Long.parseLong(m.group(1));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * 构造完整的 w:drawing 内 pic:pic（用于退化路径，理论上一般走 buildPicInner）
     */
    private String buildPicDrawingFragment(String imageRelId, int chartIndex, long cx, long cy) {
        return "<a:graphic xmlns:a=\"" + NS_A + "\">" +
                "<a:graphicData uri=\"" + PIC_NAMESPACE + "\">" +
                buildPicInner(imageRelId, chartIndex, cx, cy) +
                "</a:graphicData></a:graphic>";
    }

    /**
     * graphicData 内部 pic:pic 主体
     */
    private String buildPicInner(String imageRelId, int chartIndex, long cx, long cy) {
        int docPrId = CHART_IMAGE_DOC_PR_ID_OFFSET + chartIndex;
        String name = "ChartImage" + chartIndex;
        return "<pic:pic xmlns:pic=\"" + PIC_NAMESPACE + "\">" +
                "<pic:nvPicPr>" +
                "<pic:cNvPr id=\"" + docPrId + "\" name=\"" + name + "\"/>" +
                "<pic:cNvPicPr/>" +
                "</pic:nvPicPr>" +
                "<pic:blipFill>" +
                "<a:blip xmlns:r=\"" + NS_R + "\" r:embed=\"" + imageRelId + "\"/>" +
                "<a:stretch><a:fillRect/></a:stretch>" +
                "</pic:blipFill>" +
                "<pic:spPr>" +
                "<a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"" + cx + "\" cy=\"" + cy + "\"/></a:xfrm>" +
                "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom>" +
                "</pic:spPr>" +
                "</pic:pic>";
    }

    // ===== Chart 图片替换 =====

    /**
     * 将 DOCX 中的 chart marker 替换为 PNG 图片（ZIP 级操作）
     * <p>
     * 流程：
     * 1. 扫描 document.xml 收集 SUREDT_CHART_N marker 及其位置
     * 2. 分配 rel ID（在 document.xml.rels 中）
     * 3. PNG 写入 word/media/imageN.png
     * 4. XML 中 marker 所在段落替换为图片 drawing
     * 5. rels 插入图片关系
     * 6. 跳过 word/charts/ 和 word/embeddings/ 目录
     */
    private byte[] replaceChartsWithImages(byte[] docxBytes, List<byte[]> pngList) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            String docXml = null;
            String relsXml = null;
            byte[] contentTypesXml = null;

            // 第一遍：读出 document.xml、rels、[Content_Types].xml
            List<ZipEntryHolder> otherEntries = new ArrayList<>();
            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(docxBytes))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    byte[] content = toByteArray(zis);
                    zis.closeEntry();

                    String name = entry.getName();

                    // 跳过 chart 和 embedding
                    if (name.startsWith(DOCX_CHARTS_DIR) || name.startsWith(DOCX_EMBEDDINGS_DIR)) {
                        continue;
                    }

                    if (DOCX_DOCUMENT_XML.equals(name)) {
                        docXml = new String(content, StandardCharsets.UTF_8);
                    } else if (DOCX_DOCUMENT_RELS.equals(name)) {
                        relsXml = new String(content, StandardCharsets.UTF_8);
                    } else if (DOCX_CONTENT_TYPES.equals(name)) {
                        contentTypesXml = content;
                    } else {
                        otherEntries.add(new ZipEntryHolder(name, content));
                    }
                }
            }

            // 为每个 chart PNG 生成唯一文件名，避免与模板内已有 image*.png 冲突
            String runId = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, CHART_RUN_ID_LEN);
            List<String> chartImageNames = new ArrayList<>();
            for (int i = 0; i < pngList.size(); i++) {
                chartImageNames.add(CHART_IMAGE_NAME_PREFIX + runId + "-" + (i + 1) + CHART_IMAGE_NAME_EXT);
            }

            if (docXml == null || relsXml == null) {
                throw DocxToPdfFailedException.conversionFailed(ErrorMessage.PDF_DOCX_CORE_PART_MISSING, null);
            }

            // 收集 marker 位置
            List<ChartMarkerInfo> markers = collectChartMarkers(docXml);
            if (markers.isEmpty()) {
                // 无 marker，不需要替换，直接返回原 bytes
                return docxBytes;
            }

            // 分配 rel ID
            int maxRelId = findMaxRelId(relsXml);
            for (ChartMarkerInfo marker : markers) {
                marker.relId = "rId" + (maxRelId + marker.index + 1);
            }

            // 替换 document.xml 中的 marker 段落为图片 drawing
            docXml = replaceMarkersWithDrawings(docXml, markers, chartImageNames, pngList);

            // 更新 rels：插入图片关系
            relsXml = insertImageRels(relsXml, markers, chartImageNames);

            // 清理 [Content_Types].xml 中的 chart/xlsx 引用
            if (contentTypesXml != null) {
                contentTypesXml = cleanContentTypes(contentTypesXml);
            }

            // 第二遍：写出新 ZIP
            try (ZipOutputStream zos = new ZipOutputStream(out)) {
                // 先写 [Content_Types].xml
                if (contentTypesXml != null) {
                    zos.putNextEntry(new ZipEntry(DOCX_CONTENT_TYPES));
                    zos.write(contentTypesXml);
                    zos.closeEntry();
                }

                // 其他条目
                for (ZipEntryHolder holder : otherEntries) {
                    zos.putNextEntry(new ZipEntry(holder.name));
                    zos.write(holder.content);
                    zos.closeEntry();
                }

                // 写入 PNG 到 word/media/（使用唯一名称避免与模板内已有 image*.png 冲突）
                for (int i = 0; i < pngList.size(); i++) {
                    String mediaPath = DOCX_MEDIA_DIR + chartImageNames.get(i);
                    zos.putNextEntry(new ZipEntry(mediaPath));
                    zos.write(pngList.get(i));
                    zos.closeEntry();
                }

                // 写入更新后的 document.xml
                zos.putNextEntry(new ZipEntry(DOCX_DOCUMENT_XML));
                zos.write(docXml.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                // 写入更新后的 rels
                zos.putNextEntry(new ZipEntry(DOCX_DOCUMENT_RELS));
                zos.write(relsXml.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }

            return out.toByteArray();
        } catch (DocxToPdfFailedException e) {
            throw e;
        } catch (Exception e) {
            throw DocxToPdfFailedException.conversionFailed(String.format(ErrorMessage.PDF_CHART_IMAGE_REPLACE_FAILED, e.getMessage()), e);
        }
    }

    /**
     * 收集 document.xml 中所有 SUREDT_CHART_N marker
     */
    private List<ChartMarkerInfo> collectChartMarkers(String docXml) {
        List<ChartMarkerInfo> markers = new ArrayList<>();
        Matcher m = CHART_MARKER_PATTERN.matcher(docXml);
        while (m.find()) {
            int index = Integer.parseInt(m.group(1));
            markers.add(new ChartMarkerInfo(index, m.start(), m.end()));
        }
        return markers;
    }

    /**
     * 找 rels 中现有最大 rId 数字后缀
     */
    private int findMaxRelId(String relsXml) {
        int max = 0;
        Matcher m = REL_ID_PATTERN.matcher(relsXml);
        while (m.find()) {
            int id = Integer.parseInt(m.group(1));
            if (id > max) max = id;
        }
        return max;
    }

    /**
     * 将 document.xml 中的 marker 段落替换为图片 drawing
     */
    private String replaceMarkersWithDrawings(String docXml, List<ChartMarkerInfo> markers, List<String> chartImageNames, List<byte[]> pngList) {
        // 从后往前替换，防止位置偏移
        for (int i = markers.size() - 1; i >= 0; i--) {
            ChartMarkerInfo marker = markers.get(i);
            int chartIndex = marker.index;
            // 读 PNG 实际宽高（像素），换算 EMU。让 inline drawing 的 cx/cy 与
            // 业务对象 Chart.getWidth()/getHeight() 渲染出的 PNG 完全一致，避免
            // 出现 PNG 是 600×360 而 drawing 仍按 952×300 默认值导致的拉伸/压缩。
            long cx = DEFAULT_CHART_WIDTH_EMU;
            long cy = DEFAULT_CHART_HEIGHT_EMU;
            if (chartIndex < pngList.size()) {
                int[] wh = readPngSize(pngList.get(chartIndex));
                if (wh != null) {
                    cx = wh[0] * EMU_PER_PIXEL;
                    cy = wh[1] * EMU_PER_PIXEL;
                }
            }
            String imageRelId = marker.relId;
            int docPrId = CHART_IMAGE_DOC_PR_ID_OFFSET + chartIndex;
            String drawingName = CHART_IMAGE_DRAWING_NAME + (chartIndex + 1);

            String drawingXml =
                    "<w:drawing xmlns:wp=\"" + NS_WP + "\">" +
                            "<wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\">" +
                            "<wp:extent cx=\"" + cx + "\" cy=\"" + cy + "\"/>" +
                            "<wp:effectExtent l=\"0\" t=\"0\" r=\"0\" b=\"0\"/>" +
                            "<wp:docPr id=\"" + docPrId + "\" name=\"" + drawingName + "\"/>" +
                            "<wp:cNvGraphicFramePr/>" +
                            "<a:graphic xmlns:a=\"" + NS_A + "\">" +
                            "<a:graphicData uri=\"" + PIC_NAMESPACE + "\">" +
                            "<pic:pic xmlns:pic=\"" + PIC_NAMESPACE + "\">" +
                            "<pic:nvPicPr><pic:cNvPr id=\"" + docPrId + "\" name=\"" + drawingName + "\"/>" +
                            "<pic:cNvPicPr/></pic:nvPicPr>" +
                            "<pic:blipFill><a:blip xmlns:r=\"" + NS_R + "\" r:embed=\"" + imageRelId + "\"/>" +
                            "<a:stretch><a:fillRect/></a:stretch></pic:blipFill>" +
                            "<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/>" +
                            "<a:ext cx=\"" + cx + "\" cy=\"" + cy + "\"/></a:xfrm>" +
                            "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr>" +
                            "</pic:pic>" +
                            "</a:graphicData>" +
                            "</a:graphic>" +
                            "</wp:inline>" +
                            "</w:drawing>";

            // 找 marker 所在段落，整段替换
            int markerStart = marker.startPos;
            int markerEnd = marker.endPos;

            // 找段落起始 <w:p
            int paraStart = findParagraphStart(docXml, markerStart);
            // 找段落结束 </w:p>
            int paraEnd = docXml.indexOf("</w:p>", markerEnd);
            if (paraEnd > paraStart) {
                paraEnd += "</w:p>".length();
                String chartPara = "<w:p><w:r>" + drawingXml + "</w:r></w:p>";
                docXml = docXml.substring(0, paraStart) + chartPara + docXml.substring(paraEnd);
            }
        }
        return docXml;
    }

    /**
     * 从 marker 位置向前找段落起始 &lt;w:p
     */
    private int findParagraphStart(String xml, int markerPos) {
        int pos = markerPos;
        while (pos > 0) {
            pos = xml.lastIndexOf("<w:p", pos - 1);
            if (pos < 0) break;
            char next = xml.charAt(pos + 4);
            if (next == '>' || next == ' ') break;
        }
        return pos;
    }

    /**
     * 在 rels 中插入图片关系
     */
    private String insertImageRels(String relsXml, List<ChartMarkerInfo> markers, List<String> chartImageNames) {
        int insertPos = relsXml.lastIndexOf("</Relationships>");
        if (insertPos <= 0) return relsXml;

        StringBuilder sb = new StringBuilder(relsXml);
        for (ChartMarkerInfo marker : markers) {
            String imageName = chartImageNames.get(marker.index);
            String rel = "<Relationship Id=\"" + marker.relId + "\"" +
                    " Type=\"" + REL_TYPE_IMAGE + "\"" +
                    " Target=\"media/" + imageName + "\"/>\n";
            sb.insert(insertPos, rel);
            // 后续插入位置需要偏移
            insertPos += rel.length();
        }
        return sb.toString();
    }

    /**
     * 清理 [Content_Types].xml 中的 chart/xlsx 引用
     */
    private byte[] cleanContentTypes(byte[] xml) {
        String s = new String(xml, StandardCharsets.UTF_8);
        s = s.replaceAll("<Override\\s+PartName=\"/word/charts/[^\"]+\"[^/]*/>", "");
        s = s.replaceAll("<Default\\s+Extension=\"xlsx\"[^/]*/>", "");
        return ensurePngContentType(s.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] ensurePngContentType(byte[] xml) {
        String s = new String(xml, StandardCharsets.UTF_8);
        if (!s.contains("Extension=\"png\"")) {
            s = s.replace("</Types>", "<Default Extension=\"png\" ContentType=\"image/png\"/></Types>");
        }
        return s.getBytes(StandardCharsets.UTF_8);
    }

    // ===== 工具方法 =====

    /**
     * 读 PNG 头部 IHDR 块的 width/height（像素）。
     * <p>
     * PNG 文件结构：8 字节签名 + IHDR chunk（length 4B + type 4B + width 4B + height 4B ...）
     * 偏移 16-19 = width，20-23 = height（big-endian, 32-bit unsigned）。
     * 不是 PNG 或文件过短，返回 null。
     */
    private int[] readPngSize(byte[] png) {
        if (png == null || png.length < 24) return null;
        // PNG 签名校验：89 50 4E 47 0D 0A 1A 0A
        if ((png[0] & 0xFF) != 0x89 || png[1] != 'P' || png[2] != 'N' || png[3] != 'G') {
            return null;
        }
        int w = ((png[16] & 0xFF) << 24) | ((png[17] & 0xFF) << 16)
                | ((png[18] & 0xFF) << 8) | (png[19] & 0xFF);
        int h = ((png[20] & 0xFF) << 24) | ((png[21] & 0xFF) << 16)
                | ((png[22] & 0xFF) << 8) | (png[23] & 0xFF);
        if (w <= 0 || h <= 0) return null;
        return new int[]{w, h};
    }

    // ===== 内部数据结构 =====

    private static class ZipEntryHolder {
        final String name;
        final byte[] content;

        ZipEntryHolder(String name, byte[] content) {
            this.name = name;
            this.content = content;
        }
    }

    private static class ChartMarkerInfo {
        final int index;
        final int startPos;
        final int endPos;
        String relId;

        ChartMarkerInfo(int index, int startPos, int endPos) {
            this.index = index;
            this.startPos = startPos;
            this.endPos = endPos;
        }
    }
}
