package io.github.surezzzzzz.sdk.template.doc.converter;

import io.github.surezzzzzz.sdk.template.doc.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.template.doc.constant.SimpleDocTemplateConstant;
import io.github.surezzzzzz.sdk.template.doc.exception.DocxToPdfFailedException;
import io.github.surezzzzzz.sdk.template.doc.exception.PdfFooterUnsupportedException;
import io.github.surezzzzzz.sdk.template.doc.resolver.WordStyleResolver;
import io.github.surezzzzzz.sdk.template.doc.support.NumberingProcessor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.*;

/**
 * Word → XHTML 转换器（POI XWPFDocument → XHTML 字符串）
 * <p>
 * 自研 emitter，不引入 docx4j，与 1.0 自主渲染原则一致。
 * 覆盖范围按模板实际使用的元素子集设计，非追求 100% OOXML 覆盖。
 *
 * @author surezzzzzz
 */
@Slf4j
public class WordToXhtmlConverter {

    /**
     * Twips → mm 换算系数：1 inch = 1440 twips = 25.4 mm，故 1 mm ≈ 56.6929 twips。
     * <p>
     * 注意不是 567（那会让 A4 21cm 缩成 2.1cm，PDF 整体缩小 10 倍 → 每页 1-2 字、几百页）。
     */
    private static final double TWIPS_PER_MM = 1440.0 / 25.4;

    /**
     * 当前转换上下文中的字体白名单（小写）。
     * <p>
     * 由调用方（PdfOutputHandler）传入实际注册成功的 family name 集合，
     * 仅当 run.fontFamily 落在此集合内才输出到 CSS。未命中（含 .odttf 嵌入字体名）
     * 一律丢弃，由 body 字体链兜底。
     */
    private Set<String> fontFamilyWhitelistLowercase = Collections.emptySet();

    /**
     * 当前转换上下文中的 body 默认 font-family CSS 值（已加引号、逗号分隔）。
     * <p>
     * 由 registeredFamilies 推导：若有注册字体，body 使用首个注册族；否则退回 serif。
     */
    private String bodyFontFamilyCss = "serif";

    /**
     * 当前文档的列表编号处理器（从 numbering.xml 解析得到）
     */
    private NumberingProcessor numberingProcessor = NumberingProcessor.empty();

    /**
     * 当前文档的样式继承解析器（docDefaults / pStyle 链 / rStyle 链 → 段落&run 属性）。
     * 由 toXhtml 在打开 XWPFDocument 之后初始化，用于替代直接读 self pPr/rPr 的旧路径。
     */
    private WordStyleResolver styleResolver = null;

    /**
     * 当前文档的内容区宽度（px），由 sectPr/pageMargin 计算得出。
     * 用于表格单元格 width: X% 精确排版。
     */
    private int contentWidthPx = SimpleDocTemplateConstant.CONTENT_WIDTH_PX_FALLBACK;

    /**
     * 表格单元格默认字号（pt），从 Normal Table 样式链动态解析。
     * 未设置时为 -1，由 writeHtmlHeader 兜底 10pt。
     */
    private double tableCellFontSizePt = -1;

    /**
     * 当前 section 的内容区宽度（mm）= pageW - leftMargin - rightMargin。
     * 由 writePageCss 计算并赋值，writeTable 将 tblW(pct) 转为绝对 mm 时使用。
     * 0 表示尚未初始化（极少数无 sectPr 的文档），此时 pct 兜底用 A4 内容宽 ≈ 146.6mm。
     */
    private double contentWidthMm = 0;

    private boolean isWhitelistedFontFamily(String name) {
        return name != null
                && !name.isEmpty()
                && fontFamilyWhitelistLowercase.contains(name.toLowerCase());
    }

    /**
     * 将 DOCX 字节流转换为 XHTML 字符串（无注册上下文，body 使用 serif，所有 fontFamily 都被丢弃）
     *
     * @param docxBytes DOCX 字节
     * @return XHTML 字符串，可直接传给 openhtmltopdf
     */
    public String toXhtml(byte[] docxBytes) {
        return toXhtml(docxBytes, Collections.emptySet());
    }

    /**
     * 将 DOCX 字节流转换为 XHTML 字符串
     *
     * @param docxBytes          DOCX 字节
     * @param registeredFamilies PDF 渲染器实际注册成功的 family name 集合（动态来自 fontPaths）；
     *                           作为 fontFamily 白名单，并驱动 body 默认字体链
     * @return XHTML 字符串
     */
    public String toXhtml(byte[] docxBytes, Set<String> registeredFamilies) {
        Set<String> whitelist = new HashSet<>();
        StringBuilder bodyChain = new StringBuilder();
        if (registeredFamilies != null) {
            for (String n : registeredFamilies) {
                if (n == null || n.isEmpty()) continue;
                whitelist.add(n.toLowerCase());
                if (bodyChain.length() > 0) bodyChain.append(", ");
                bodyChain.append("'").append(n).append("'");
            }
        }
        if (bodyChain.length() > 0) bodyChain.append(", serif");
        else bodyChain.append("serif");
        this.fontFamilyWhitelistLowercase = whitelist;
        this.bodyFontFamilyCss = bodyChain.toString();

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            // 内容区宽度：pageW - leftMargin - rightMargin（单位 twips → px）
            this.contentWidthPx = computeContentWidthPx(doc);
            // 表格单元格默认字号：从 styles.xml 直接解析（styleId 可能是 "11"/"Normal"/"TableNormal"，用 w:name 匹配）
            this.tableCellFontSizePt = resolveTableCellFontPtFromZip(docxBytes);
            // 解析 numbering.xml（chineseCounting / decimal / roman / letter / bullet 等）
            this.numberingProcessor = NumberingProcessor.from(doc);
            // 样式继承解析器（docDefaults + pStyle/rStyle 链）
            this.styleResolver = new WordStyleResolver(doc);

            StringBuilder html = new StringBuilder(16 * 1024);
            writeHtmlHeader(html, doc);
            // 页眉走 running element（保留 1.0.x 全功能：图片/表格/复杂段落）；
            // 页脚改为在 @page 的 @bottom-center 里直接拼 content，PAGE/NUMPAGES 走 CSS counter
            writeHeaderRunningElement(html, doc);
            for (IBodyElement el : doc.getBodyElements()) {
                if (el instanceof XWPFParagraph) {
                    writeParagraph(html, (XWPFParagraph) el, doc);
                } else if (el instanceof XWPFTable) {
                    writeTable(html, (XWPFTable) el);
                }
            }
            writeHtmlFooter(html);
            return html.toString();
        } catch (IOException e) {
            throw DocxToPdfFailedException.conversionFailed(String.format(ErrorMessage.PDF_POI_DOCX_LOAD_FAILED, e.getMessage()), e);
        }
    }

    // ===== 页眉（running element，全功能）=====

    /**
     * 把 DOCX 页眉渲染为 openhtmltopdf 的 running element。
     * 在 @page CSS 里通过 position: running(headerRunning) 引用，绑定到 @top-center。
     * 保留 1.0.x 全功能：图片（logo）、表格、多段落、border、变量替换。
     */
    private void writeHeaderRunningElement(StringBuilder out, XWPFDocument doc) {
        List<XWPFHeader> headers = doc.getHeaderList();
        if (headers.isEmpty()) return;
        out.append("<div id=\"docHeader\" style=\"position: running(headerRunning); font-size: 9pt;\">");
        // 仅取第一个 default header；多 section 模板暂不区分
        XWPFHeader h = headers.get(0);
        for (IBodyElement el : h.getBodyElements()) {
            if (el instanceof XWPFParagraph) {
                // running element 里 openhtmltopdf 对 <p> 的 text-indent / white-space:pre-wrap 处理不可靠
                // （实测页眉文字本应缩进 ~67mm，结果直接贴在 padding-left=0 起点，
                // 既不应用 text-indent 也折叠了所有前导空格）。
                // 走 header 专用 writer：把 ind/firstLine + 前导空格 一律折算成 padding-left。
                writeHeaderParagraph(out, (XWPFParagraph) el, doc);
            } else if (el instanceof XWPFTable) {
                writeTable(out, (XWPFTable) el);
            }
        }
        out.append("</div>\n");
    }

    /**
     * 页眉 running element 内段落的专用写出。
     * <p>
     * 与正文 writeParagraph 的差异：
     * <ul>
     *   <li>段落 ind/firstLine 转 padding-left（text-indent 不可靠）</li>
     *   <li>所有 run 文本里的前导空格折算成 padding-left（按各 run 实际字号，CJK 字体半角空格≈0.5em 估算）</li>
     *   <li>仍然保留 border-bottom / 字体 / 颜色 等其他样式</li>
     * </ul>
     */
    private void writeHeaderParagraph(StringBuilder out, XWPFParagraph para, XWPFDocument doc) {
        // 1. 计算 indent (mm)
        double indentMm = 0.0;
        if (styleResolver != null) {
            long firstLine = styleResolver.resolveIndFirstLine(para);
            long left = styleResolver.resolveIndLeft(para);
            if (left > 0) indentMm += left / TWIPS_PER_MM;
            if (firstLine > 0) indentMm += firstLine / TWIPS_PER_MM;
        }

        // 2. 按各 run 累计前导空格的视觉宽度（mm）
        //    各 run 字号可能不同（如页眉里 21 half-pt ≠ body 默认）；用 run 自己的字号估算
        //    CJK 字体（SimSun/SimHei/Yahei 等）下 ASCII 半角空格 U+0020 通常被字体 cmap 映射为半字宽，
        //    占 0.5em；Latin 字体里约 0.25~0.3em。我们没法在转换期知道具体字体，取 0.5em 兼容
        //    "CJK 模板里用空格做对齐" 这个最常见的情况，对纯英文模板会略宽一点点，
        //    但页眉里靠空格对齐就是常见 CJK 排版手法。
        int leadingSpacesTotal = 0;
        double extraIndent = countLeadingIndentMm(para);
        double totalIndentMm = indentMm + extraIndent;

        // 3. 输出 <p> with padding-left
        out.append("<p style=\"");
        if (totalIndentMm > 0) {
            out.append(String.format("padding-left:%.2fmm;", totalIndentMm));
        }
        // border-bottom 保留（页眉常见的下划线）
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr pPr = para.getCTP().getPPr();
        if (pPr != null && pPr.getPBdr() != null && pPr.getPBdr().getBottom() != null) {
            out.append("border-bottom:0.50pt solid #000;");
        }
        out.append("\">");

        // 4. 输出 runs，把段首前导空格剥离掉（已经折算到 padding-left）
        leadingSpacesTotal = countLeadingSpacesAcrossRuns(para);
        writeHeaderRuns(out, para, doc, leadingSpacesTotal);
        out.append("</p>\n");
    }

    /**
     * 跨 run 累计段首前导空格的视觉宽度（mm）。
     * 每个空格按所在 run 的解析字号 × 0.5em 估算（CJK 字体下半角空格 = 半字宽）。
     */
    private double countLeadingIndentMm(XWPFParagraph para) {
        double sumMm = 0.0;
        boolean stillLeading = true;
        // body 默认字号兜底（pt）
        double bodyPt = SimpleDocTemplateConstant.WORD_HEADER_DEFAULT_FONT_PT;
        if (styleResolver != null) {
            long halfPt = styleResolver.resolveDocDefaultSzHalfPt();
            if (halfPt > 0) bodyPt = halfPt / 2.0;
        }
        for (XWPFRun r : para.getRuns()) {
            if (!stillLeading) break;
            String t = r.getText(0);
            if (t == null) continue;
            // 解析 run 字号（half-point）
            double runPt = bodyPt;
            if (styleResolver != null) {
                long halfPt = styleResolver.resolveRunSzHalfPt(r, para);
                if (halfPt > 0) runPt = halfPt / 2.0;
            }
            // 0.5em × pt → mm: 1pt = 0.3528mm
            double mmPerSpace = runPt * SimpleDocTemplateConstant.WORD_SPACE_WIDTH_COEFFICIENT * 0.3528;
            for (int i = 0; i < t.length(); i++) {
                char c = t.charAt(i);
                if (c == ' ' || c == '\u00A0') {
                    sumMm += mmPerSpace;
                } else if (c == '\t') {
                    // tab：估算为 4 个空格宽
                    sumMm += mmPerSpace * 4;
                } else {
                    stillLeading = false;
                    break;
                }
            }
        }
        return sumMm;
    }

    private int countLeadingSpacesAcrossRuns(XWPFParagraph para) {
        int total = 0;
        boolean stillLeading = true;
        for (XWPFRun r : para.getRuns()) {
            if (!stillLeading) break;
            String t = r.getText(0);
            if (t == null) continue;
            for (int i = 0; i < t.length(); i++) {
                char c = t.charAt(i);
                if (c == ' ' || c == '\u00A0' || c == '\t') {
                    total++;
                } else {
                    stillLeading = false;
                    break;
                }
            }
        }
        return total;
    }

    /**
     * 写 header 段落里的 runs，剥离最前导的 N 个空格（已折算到 padding-left 的部分）
     */
    private void writeHeaderRuns(StringBuilder out, XWPFParagraph para, XWPFDocument doc, int leadingSpacesToStrip) {
        int remaining = leadingSpacesToStrip;
        for (XWPFRun run : para.getRuns()) {
            String text = run.getText(0);
            if (text == null) continue;
            String stripped = text;
            if (remaining > 0) {
                int dropped = 0;
                while (dropped < remaining && dropped < stripped.length()) {
                    char c = stripped.charAt(dropped);
                    if (c == ' ' || c == '\u00A0' || c == '\t') {
                        dropped++;
                    } else {
                        break;
                    }
                }
                stripped = stripped.substring(dropped);
                remaining -= dropped;
            }
            if (stripped.isEmpty()) continue;
            // 用同样的 buildRunStyleCss + escape 复用主路径的 run 样式
            StringBuilder runStyle = buildRunStyleCss(run.getCTR(), run, para);
            out.append("<span");
            if (runStyle.length() > 0) {
                out.append(" style=\"").append(runStyle).append('"');
            }
            out.append('>').append(escapeHtml(stripped)).append("</span>");
        }
    }

    // ===== HTML 结构 =====

    private void writeHtmlHeader(StringBuilder out, XWPFDocument doc) {
        out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        out.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" ");
        out.append("\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n");
        out.append("<html xmlns=\"http://www.w3.org/1999/xhtml\">\n");
        out.append("<head>\n");
        out.append("<meta charset=\"UTF-8\"/>\n");
        out.append("<style type=\"text/css\">\n");
        writePageCss(out, doc);
        // body font-size：优先读模板 docDefaults > rPrDefault > sz（half-point）。
        // 兜底 12pt：OOXML 规范默认是 10pt，但 Word/WPS 在 docDefaults 缺失 sz 时
        // 实测渲染为 12pt（实测对比：本工程 weekly-report 模板就是这种情况，
        // 改成 10pt 会让总页数从 7 页变成 6 页）。所以兜底跟 Word/WPS 行为对齐而非跟规范。
        double bodyFontSizePt = SimpleDocTemplateConstant.WORD_DEFAULT_BODY_FONT_PT;
        if (styleResolver != null) {
            long halfPt = styleResolver.resolveDocDefaultSzHalfPt();
            if (halfPt > 0) {
                bodyFontSizePt = halfPt / 2.0;
            }
        }
        out.append(String.format("body { font-family: %s; font-size: %.1fpt; margin: 0; padding: 0; }%n",
                bodyFontFamilyCss, bodyFontSizePt));
        // 段落默认外边距置 0：openhtmltopdf 默认给 <p> 加 1em 上下边距，
        // 会和 OOXML pPr/spacing 翻译出的 margin-top/bottom 叠加（实测 page 1 左右多 +2.1mm，正文 Y 轴整体下移）。
        // 我们逐段落显式输出真实的 margin/text-indent，需要先把 UA 默认重置掉。
        out.append("p { margin: 0; padding: 0; }\n");
        // DOCX 段落里的前导空格、连续空格、tab 都要保留视觉宽度，不能被 HTML 折叠
        out.append("p, h1, h2, h3, h4, h5, h6, li, td, th { white-space: pre-wrap; }\n");
        // PAGE / NUMPAGES 字段：用 CSS counter 实现页码自动渲染
        out.append(".sdt-page-num::before { content: counter(page); }\n");
        out.append(".sdt-page-count::before { content: counter(pages); }\n");
        // 列表编号前缀（chineseCounting/decimal/roman/letter/bullet）
        out.append(".sdt-list-prefix { display: inline-block; }\n");
        // WPS 文本框（页脚里包页码用）：用绝对定位还原 wp:anchor 位置
        out.append(".sdt-textbox { display: inline-block; }\n");
        out.append("table { border-collapse: collapse; table-layout: fixed; }\n");
        double tcFontPt = tableCellFontSizePt > 0 ? tableCellFontSizePt : SimpleDocTemplateConstant.WORD_TABLE_CELL_DEFAULT_FONT_PT;
        out.append(String.format("td, th { border: 1px solid #000; padding: 4px; font-size: %.1fpt; word-break: break-word; }%n", tcFontPt));
        out.append(".page-break { page-break-before: always; }\n");
        out.append("</style>\n");
        out.append("</head>\n<body>\n");
    }

    private void writeHtmlFooter(StringBuilder out) {
        out.append("</body>\n</html>");
    }

    // ===== 页面 CSS =====

    private void writePageCss(StringBuilder out, XWPFDocument doc) {
        CTSectPr sectPr = doc.getDocument().getBody().getSectPr();

        // 默认 A4，单位 twips
        long pageWidthTwips = 11906;
        long pageHeightTwips = 16838;
        long topTwips = 1440;
        long bottomTwips = 1440;
        long leftTwips = 1440;
        long rightTwips = 1440;
        long headerTwips = 720;
        long footerTwips = 720;

        if (sectPr != null) {
            CTPageSz pgSz = sectPr.getPgSz();
            if (pgSz != null) {
                pageWidthTwips = getSchemaLong(pgSz, "getW", pageWidthTwips);
                pageHeightTwips = getSchemaLong(pgSz, "getH", pageHeightTwips);
            }
            CTPageMar pgMar = sectPr.getPgMar();
            if (pgMar != null) {
                topTwips = getSchemaLong(pgMar, "getTop", topTwips);
                bottomTwips = getSchemaLong(pgMar, "getBottom", bottomTwips);
                leftTwips = getSchemaLong(pgMar, "getLeft", leftTwips);
                rightTwips = getSchemaLong(pgMar, "getRight", rightTwips);
                headerTwips = getSchemaLong(pgMar, "getHeader", headerTwips);
                footerTwips = getSchemaLong(pgMar, "getFooter", footerTwips);
            }
        }

        boolean hasHeader = !doc.getHeaderList().isEmpty();
        boolean hasFooter = !doc.getFooterList().isEmpty();

        // 记录内容区宽度（mm），供 writeTable 将 pct 类型 tblW 转换为绝对 mm 用
        long contentTwips = pageWidthTwips - leftTwips - rightTwips;
        this.contentWidthMm = contentTwips > 0 ? contentTwips / TWIPS_PER_MM : 0;

        // twips → mm: 1 inch = 1440 twips = 25.4 mm，故 1 mm = 1440/25.4 twips
        out.append(String.format(
                "@page { size: %.2fmm %.2fmm; margin: %.2fmm %.2fmm %.2fmm %.2fmm;",
                pageWidthTwips / TWIPS_PER_MM, pageHeightTwips / TWIPS_PER_MM,
                topTwips / TWIPS_PER_MM, rightTwips / TWIPS_PER_MM,
                bottomTwips / TWIPS_PER_MM, leftTwips / TWIPS_PER_MM));
        if (hasHeader) {
            // OOXML w:header = 页眉顶边距页面顶部的距离。
            // CSS Paged Media：@top-center 盒子高度 = top page margin。
            // vertical-align:top + padding-top 让页眉距盒顶 = headerTwips
            out.append(String.format(
                    " @top-center { content: element(headerRunning); vertical-align: top; padding-top: %.2fmm; }",
                    headerTwips / TWIPS_PER_MM));
        }
        if (hasFooter) {
            // 页脚走 @bottom-center 直接拼接 content（PAGE/NUMPAGES 走 CSS counter）
            // —— 受限于 CSS Paged Media 规范：counter(pages) 在 running element 上下文里行为不可靠
            FooterPageCss footerCss = buildFooterPageCss(doc.getFooterList().get(0));
            // OOXML w:footer = 页脚底边距页面底部的距离。
            // vertical-align:bottom + padding-bottom 让页脚距盒底 = footerTwips
            // @bottom-center 不继承 body 的 font-family，必须显式注入字体链兜底（否则中文渲染为井号 ####）
            // footer 自己若声明了 font-family（命中白名单），extraDecl 已经把它放在前面，会覆盖这个兜底链
            out.append(String.format(
                    " @bottom-center { content: %s; font-family: %s;%s vertical-align: bottom; padding-bottom: %.2fmm; }",
                    footerCss.contentExpr,
                    bodyFontFamilyCss,
                    footerCss.extraDecl,
                    footerTwips / TWIPS_PER_MM));
        }
        out.append(" }\n");
    }

    // ===== 页脚（PDF 专用收紧实现，独立于正文/页眉的渲染路径）=====

    /**
     * 页脚 CSS 输出片段：CSS content 表达式 + 附加 @bottom-center 样式（border-top / text-align / font-*）。
     * 完全独立于 writeParagraph / writeRunsWithFields 这条路径，footer 只走 buildFooterPageCss + extractFooter*。
     */
    private static class FooterPageCss {
        /**
         * CSS content 属性的右值表达式，例如 "\"第 \" counter(page) \" 页 共 \" counter(pages) \" 页\""; 为空时设为 "\"\""
         */
        final String contentExpr;
        /**
         * 额外塞到 @bottom-center 块里的样式声明（带前导空格、以分号结尾），可能为空字符串
         */
        final String extraDecl;

        FooterPageCss(String contentExpr, String extraDecl) {
            this.contentExpr = contentExpr;
            this.extraDecl = extraDecl;
        }
    }

    /**
     * 把 footer 解析为 @bottom-center 可用的 CSS。
     * <p>
     * <b>1.1.0 PDF 输出对 footer 的约束</b>：
     * <ul>
     *     <li>✅ 纯文本 + 变量替换 + 条件块（变量/条件块在 1.0.x 字节级处理阶段已落地）</li>
     *     <li>✅ PAGE / NUMPAGES（含 mc:AlternateContent → wps:txbxContent 包裹的 WPS 文本框形态）</li>
     *     <li>✅ 段落级样式：对齐、border-top、字体、字号、颜色</li>
     *     <li>❌ 图片（drawing/pict）、表格、列表（numId） — 检测到一律抛 UnsupportedOperationException</li>
     * </ul>
     */
    private FooterPageCss buildFooterPageCss(XWPFFooter footer) {
        // 只取 footer 里第一个段落作为 @bottom-center 内容（多段落 footer 在 @bottom-center 里会被拼成一行）
        // 检测到表格/列表/图片立刻 throw
        for (IBodyElement el : footer.getBodyElements()) {
            if (el instanceof XWPFTable) {
                throw PdfFooterUnsupportedException.unsupported(SimpleDocTemplateConstant.ELEMENT_TABLE);
            }
        }

        StringBuilder contentExpr = new StringBuilder();
        StringBuilder extraDecl = new StringBuilder();
        boolean anyContent = false;
        boolean firstPara = true;

        for (IBodyElement el : footer.getBodyElements()) {
            if (!(el instanceof XWPFParagraph)) continue;
            XWPFParagraph para = (XWPFParagraph) el;
            if (para.getNumID() != null) {
                throw PdfFooterUnsupportedException.unsupported(SimpleDocTemplateConstant.ELEMENT_LIST);
            }
            // 段落级样式只取第一个段落，作为 @bottom-center 整体样式
            if (firstPara) {
                appendFooterParagraphLevelStyle(extraDecl, para);
                firstPara = false;
            }
            // 段落级换行：多段落 footer 在 @bottom-center 里用 "\A" 表示换行（需配合 white-space:pre）
            if (anyContent) {
                contentExpr.append(" \"\\A\" ");
            }
            boolean appended = appendFooterParagraphContent(contentExpr, para);
            if (appended) anyContent = true;
        }
        if (!extraDecl.toString().isEmpty()) {
            // 多段落时 white-space:pre 让 \A 生效
            extraDecl.append(" white-space: pre;");
        }
        if (!anyContent) {
            // 空 footer，避免 @bottom-center 渲染异常
            return new FooterPageCss("\"\"", extraDecl.toString());
        }
        return new FooterPageCss(contentExpr.toString().trim(), extraDecl.toString());
    }

    /**
     * 抽 footer 第一个段落的段落级样式 → @bottom-center 整体声明（border-top / text-align / 首段首 run 字体）
     */
    private void appendFooterParagraphLevelStyle(StringBuilder extraDecl, XWPFParagraph para) {
        // 对齐
        ParagraphAlignment align = para.getAlignment();
        if (align != null) {
            switch (align) {
                case CENTER:
                    extraDecl.append(" text-align: center;");
                    break;
                case RIGHT:
                    extraDecl.append(" text-align: right;");
                    break;
                case BOTH:
                    extraDecl.append(" text-align: justify;");
                    break;
                default:
                    break;
            }
        }
        // border-top（页脚常见分隔线）
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr pPr = para.getCTP().getPPr();
        if (pPr != null && pPr.getPBdr() != null) {
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder topBorder = pPr.getPBdr().getTop();
            if (topBorder != null) {
                StringBuilder borderCss = new StringBuilder();
                appendBorderSide(borderCss, "top", topBorder);
                if (borderCss.length() > 0) {
                    extraDecl.append(" ").append(borderCss);
                }
            }
        }
        // 首 run 字体/字号/颜色（CSS content 不支持 run-level 样式，所以整段 footer 共用首 run 样式）
        for (XWPFRun r : para.getRuns()) {
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr rPr = r.getCTR().getRPr();
            if (rPr == null) continue;
            if (rPr.sizeOfSzArray() > 0) {
                Object v = rPr.getSzArray(0).getVal();
                if (v != null) {
                    try {
                        long half = new BigInteger(v.toString()).longValue();
                        extraDecl.append(" font-size: ").append(half / 2).append("pt;");
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            if (rPr.sizeOfColorArray() > 0) {
                Object v = rPr.getColorArray(0).getVal();
                if (v != null) {
                    String c = v.toString();
                    if (!c.isEmpty() && !"auto".equalsIgnoreCase(c)) {
                        extraDecl.append(" color: #").append(c).append(";");
                    }
                }
            }
            if (rPr.sizeOfRFontsArray() > 0) {
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts f = rPr.getRFontsArray(0);
                String name = null;
                if (f.getAscii() != null && !f.getAscii().isEmpty()) name = f.getAscii();
                else if (f.getEastAsia() != null && !f.getEastAsia().isEmpty()) name = f.getEastAsia();
                else if (f.getHAnsi() != null && !f.getHAnsi().isEmpty()) name = f.getHAnsi();
                if (isWhitelistedFontFamily(name)) {
                    extraDecl.append(" font-family: '").append(name).append("';");
                }
            }
            if (rPr.sizeOfBArray() > 0) extraDecl.append(" font-weight: bold;");
            if (rPr.sizeOfIArray() > 0) extraDecl.append(" font-style: italic;");
            break; // 只取首 run
        }
    }

    /**
     * 把单个 footer 段落的内容拼成 CSS content 表达式片段。
     * <p>
     * 段落里按顺序处理 w:r / mc:AlternateContent，识别 fldChar PAGE/NUMPAGES（含 wps:txbxContent 嵌套）
     * 转成 counter(page) / counter(pages)；普通文本走带转义的字符串字面量。
     *
     * @return true 表示输出了任何 content 片段
     */
    private boolean appendFooterParagraphContent(StringBuilder out, XWPFParagraph para) {
        FooterFieldState state = new FooterFieldState();
        StringBuilder text = new StringBuilder();
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP ctP = para.getCTP();
        org.apache.xmlbeans.XmlCursor cur = ctP.newCursor();
        boolean any;
        try {
            if (cur.toFirstChild()) {
                do {
                    walkFooterNode(out, text, cur, state);
                } while (cur.toNextSibling());
            }
        } finally {
            cur.dispose();
        }
        any = flushFooterText(out, text) || state.emittedAny;
        return any;
    }

    /**
     * footer 字段状态机（独立于 body 端的 FieldState，逻辑相似但不复用）。
     */
    private static class FooterFieldState {
        boolean inField = false;
        boolean afterSeparate = false;
        StringBuilder instr = new StringBuilder();
        boolean emittedAny = false;

        void begin() {
            inField = true;
            afterSeparate = false;
            instr.setLength(0);
        }

        void separate() {
            afterSeparate = true;
        }

        boolean isPage() {
            return inField && containsToken("PAGE") && !containsToken("NUMPAGES");
        }

        boolean isNumPages() {
            return inField && containsToken("NUMPAGES");
        }

        void end() {
            inField = false;
            afterSeparate = false;
            instr.setLength(0);
        }

        private boolean containsToken(String token) {
            String s = instr.toString();
            int idx = s.indexOf(token);
            if (idx < 0) return false;
            int before = idx - 1;
            int after = idx + token.length();
            char b = before >= 0 ? s.charAt(before) : ' ';
            char a = after < s.length() ? s.charAt(after) : ' ';
            return !Character.isLetterOrDigit(b) && b != '_' && !Character.isLetterOrDigit(a) && a != '_';
        }
    }

    /**
     * 递归遍历 footer 节点：w:r → 走 footer 专用 run 处理；mc:AlternateContent → 进 Choice 找 wps:txbxContent 里的段落。
     * 检测到 drawing/pict/tbl 抛 UnsupportedOperationException。
     */
    private void walkFooterNode(StringBuilder out, StringBuilder text,
                                org.apache.xmlbeans.XmlCursor cur, FooterFieldState state) {
        javax.xml.namespace.QName qn = cur.getName();
        if (qn == null) return;
        String local = qn.getLocalPart();
        if ("r".equals(local)) {
            handleFooterRunCursor(out, text, cur, state);
        } else if ("hyperlink".equals(local)) {
            // hyperlink 内一组 w:r，递归
            org.apache.xmlbeans.XmlCursor child = cur.newCursor();
            try {
                if (child.toFirstChild()) {
                    do {
                        walkFooterNode(out, text, child, state);
                    } while (child.toNextSibling());
                }
            } finally {
                child.dispose();
            }
        } else if ("AlternateContent".equals(local)) {
            // mc:AlternateContent → mc:Choice → w:drawing → wps:txbxContent → w:p
            // footer 用 textbox 包页码是 WPS 常见结构，必须穿透
            walkFooterAlternateContent(out, text, cur, state);
        }
    }

    /**
     * footer 内 w:r 的处理：解析 fldChar 状态、instrText、t、tab、br、sym。
     * 检测到 drawing / pict 抛 UnsupportedOperationException（footer 不支持图片）。
     */
    private void handleFooterRunCursor(StringBuilder out, StringBuilder text,
                                       org.apache.xmlbeans.XmlCursor runCursor, FooterFieldState state) {
        org.apache.xmlbeans.XmlCursor cur = runCursor.newCursor();
        try {
            if (!cur.toFirstChild()) return;
            do {
                javax.xml.namespace.QName qn = cur.getName();
                if (qn == null) continue;
                String local = qn.getLocalPart();
                if ("fldChar".equals(local)) {
                    String type = cur.getAttributeText(new javax.xml.namespace.QName(
                            "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "fldCharType"));
                    if (type == null) type = cur.getAttributeText(new javax.xml.namespace.QName(null, "fldCharType"));
                    if ("begin".equalsIgnoreCase(type)) {
                        state.begin();
                    } else if ("separate".equalsIgnoreCase(type)) {
                        state.separate();
                    } else if ("end".equalsIgnoreCase(type)) {
                        if (state.isPage()) {
                            // flush 之前累积的纯文本，再 emit counter(page)
                            flushFooterText(out, text);
                            appendFooterContentToken(out, "counter(page)");
                            state.emittedAny = true;
                        } else if (state.isNumPages()) {
                            flushFooterText(out, text);
                            appendFooterContentToken(out, "counter(pages)");
                            state.emittedAny = true;
                        }
                        state.end();
                    }
                } else if ("instrText".equals(local)) {
                    if (state.inField) state.instr.append(cur.getTextValue());
                } else if ("t".equals(local)) {
                    String s = cur.getTextValue();
                    if (s == null || s.isEmpty()) continue;
                    // separate..end 之间的「字段缓存值」跳过（PAGE/NUMPAGES 缓存值由 counter 接管）
                    if (state.inField && state.afterSeparate
                            && (state.isPage() || state.isNumPages())) {
                        continue;
                    }
                    text.append(s);
                } else if ("tab".equals(local)) {
                    text.append("    ");
                } else if ("br".equals(local)) {
                    // CSS content 里的换行用 \A（需 white-space: pre）
                    flushFooterText(out, text);
                    appendFooterContentToken(out, "\"\\A\"");
                    state.emittedAny = true;
                } else if ("sym".equals(local)) {
                    String charHex = cur.getAttributeText(new javax.xml.namespace.QName(
                            "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "char"));
                    if (charHex != null) {
                        try {
                            int cp = Integer.parseInt(charHex, 16);
                            text.appendCodePoint(cp);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                } else if ("drawing".equals(local) || "pict".equals(local)) {
                    throw PdfFooterUnsupportedException.unsupported(SimpleDocTemplateConstant.ELEMENT_IMAGE_DRAWING);
                } else if ("AlternateContent".equals(local)) {
                    // CTR 内嵌的 mc:AlternateContent（页脚 WPS 文本框包页码的常见结构）
                    walkFooterAlternateContent(out, text, cur, state);
                }
            } while (cur.toNextSibling());
        } finally {
            cur.dispose();
        }
    }

    /**
     * footer 内 mc:AlternateContent 处理：进 Choice，找 wps:txbxContent 里的 w:p，递归遍历内部子节点。
     * Choice 失败再尝试 Fallback。
     */
    private void walkFooterAlternateContent(StringBuilder out, StringBuilder text,
                                            org.apache.xmlbeans.XmlCursor cursor, FooterFieldState state) {
        org.apache.xmlbeans.XmlCursor branch = cursor.newCursor();
        boolean handled = false;
        try {
            if (branch.toFirstChild()) {
                do {
                    javax.xml.namespace.QName qn = branch.getName();
                    if (qn == null) continue;
                    if ("Choice".equals(qn.getLocalPart())) {
                        if (walkFooterTextbox(out, text, branch, state)) {
                            handled = true;
                            break;
                        }
                    }
                } while (branch.toNextSibling());
            }
            if (!handled) {
                branch.dispose();
                branch = cursor.newCursor();
                if (branch.toFirstChild()) {
                    do {
                        javax.xml.namespace.QName qn = branch.getName();
                        if (qn == null) continue;
                        if ("Fallback".equals(qn.getLocalPart())) {
                            walkFooterTextbox(out, text, branch, state);
                            break;
                        }
                    } while (branch.toNextSibling());
                }
            }
        } finally {
            branch.dispose();
        }
    }

    /**
     * 在 Choice/Fallback 子树里搜 wps:txbxContent，遍历其下的 w:p 内容（递归处理 w:r / 嵌套 AlternateContent）。
     * 注：txbxContent 子节点是 XmlAnyTypeImpl（xs:any 降级），全程靠 cursor 遍历，不依赖 schema 类型。
     */
    private boolean walkFooterTextbox(StringBuilder out, StringBuilder text,
                                      org.apache.xmlbeans.XmlCursor branchCursor, FooterFieldState state) {
        org.apache.xmlbeans.XmlCursor scan = branchCursor.newCursor();
        boolean any = false;
        try {
            scan.selectPath("declare namespace w='http://schemas.openxmlformats.org/wordprocessingml/2006/main' "
                    + ".//w:txbxContent");
            while (scan.toNextSelection()) {
                org.apache.xmlbeans.XmlCursor inner = scan.newCursor();
                try {
                    if (inner.toFirstChild()) {
                        do {
                            javax.xml.namespace.QName qn = inner.getName();
                            if (qn == null) continue;
                            if (!"p".equals(qn.getLocalPart())) continue;
                            // 进入 w:p，遍历它的 w:r / w:hyperlink / mc:AlternateContent 子节点
                            org.apache.xmlbeans.XmlCursor pChild = inner.newCursor();
                            try {
                                if (pChild.toFirstChild()) {
                                    do {
                                        walkFooterNode(out, text, pChild, state);
                                    } while (pChild.toNextSibling());
                                }
                            } finally {
                                pChild.dispose();
                            }
                            any = true;
                        } while (inner.toNextSibling());
                    }
                } finally {
                    inner.dispose();
                }
            }
        } finally {
            scan.dispose();
        }
        return any;
    }

    /**
     * 把累积的纯文本作为一个带引号的字符串字面量 token 输出到 content 表达式。
     */
    private boolean flushFooterText(StringBuilder out, StringBuilder text) {
        if (text.length() == 0) return false;
        appendFooterContentToken(out, "\"" + escapeCssString(text.toString()) + "\"");
        text.setLength(0);
        return true;
    }

    /**
     * 把 token（已带引号的字符串字面量或函数调用如 counter(page)）追加到 content 表达式，自动加分隔空格。
     */
    private void appendFooterContentToken(StringBuilder out, String token) {
        if (out.length() > 0) out.append(' ');
        out.append(token);
    }

    /**
     * CSS 字符串字面量转义：双引号 → \"，反斜杠 → \\
     */
    private String escapeCssString(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 4);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' || c == '"') {
                sb.append('\\').append(c);
            } else if (c == '\n') {
                sb.append("\\A ");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * CTPageMar 的 getter 返回 Object（XMLBeans），安全转换为 long
     */
    private long toLong(Object value, long defaultValue) {
        if (value == null) return defaultValue;
        try {
            return new BigInteger(value.toString()).longValue();
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // ===== 段落 =====

    private void writeParagraph(StringBuilder out, XWPFParagraph para, XWPFDocument doc) {
        // 分页符
        if (para.isPageBreak()) {
            out.append("<div class=\"page-break\"></div>\n");
        }

        // 标题样式映射
        String styleId = para.getStyle();
        String tag = mapStyleToTag(styleId);

        // 段落 CSS
        StringBuilder style = new StringBuilder();
        // 对齐：走 resolver 取继承链（docDefaults → pStyle 链 → self pPr）
        String jc = styleResolver != null ? styleResolver.resolveJc(para) : null;
        if (jc != null) {
            switch (jc.toLowerCase()) {
                case "center":
                    style.append("text-align:center;");
                    break;
                case "right":
                case "end":
                    style.append("text-align:right;");
                    break;
                case "both":
                case "distribute":
                case "justify":
                    style.append("text-align:justify;");
                    break;
                default:
                    break;
            }
        }
        // 缩进：走 resolver
        long indentLeft = styleResolver != null ? styleResolver.resolveIndLeft(para) : -1;
        if (indentLeft > 0) {
            style.append(String.format("margin-left:%.1fmm;", indentLeft / TWIPS_PER_MM));
        }
        long indentFirst = styleResolver != null ? styleResolver.resolveIndFirstLine(para) : -1;
        if (indentFirst > 0) {
            style.append(String.format("text-indent:%.1fmm;", indentFirst / TWIPS_PER_MM));
        }
        // 段落上下间距 + 行距（pPr/spacing）：走 resolver 继承链
        appendParagraphSpacing(style, para);
        // 段落背景色（pPr/shd）
        appendParagraphBackgroundColor(style, para);
        // 段落边框（pPr/pBdr）→ CSS border-*：DOCX 页眉常用 pBdr/bottom 画分隔线
        appendParagraphBorders(style, para);
        // 段落默认 run 样式（sz/font-family/color/bold）：仅看 paraMark.rPr / pStyle 链 / docDefaults。
        // 让段落里没在 run rPr 自身覆盖样式的裸文本/run 也能继承到 pStyle 链上的字号字体，
        // 否则会落到 body 的兜底字号，与 Word/WPS 渲染不一致。
        // run 级 emit 通过 CSS 继承自动覆盖段落级。
        appendParagraphDefaultRunStyle(style, para);

        out.append("<").append(tag);
        if (style.length() > 0) {
            out.append(" style=\"").append(style).append("\"");
        }
        out.append(">");

        // 列表前缀：解析 numId/ilvl，根据 numbering.xml 模板生成「一、」「1.」「a)」等
        if (para.getNumID() != null) {
            String prefix = numberingProcessor.nextPrefix(para);
            if (prefix != null && !prefix.isEmpty()) {
                out.append("<span class=\"sdt-list-prefix\">")
                        .append(escapeHtml(prefix))
                        .append("</span>");
            }
            // 仍然写正文 runs（前缀外）
            writeRunsWithFields(out, para);
        } else {
            // 普通段落：写 runs（含 fldChar PAGE/NUMPAGES、textbox、tab 处理）
            writeRunsWithFields(out, para);
        }

        out.append("</").append(tag).append(">\n");
    }

    /**
     * 段落上下间距 + 行距（pPr/spacing）→ CSS margin-top/margin-bottom + line-height
     * <p>
     * OOXML w:spacing 属性映射：
     * <ul>
     *   <li>before/after：twips（1/1440 in），转 mm 写到 margin-top/margin-bottom；
     *       beforeAutospacing/afterAutospacing=1 时 Word 自己算，本侧不输出（让 PDF 引擎用默认）</li>
     *   <li>line + lineRule：
     *     <ul>
     *       <li>auto + docGrid type=lines/linesAndChars：行盒高度 = (line/240) × linePitch_pt；
     *           emit 为 line-height: N pt 绝对值。这是中文版式（含 WPS/Word 国内版）的实际行为：
     *           docGrid 启用时，单倍行距=一行网格高度（linePitch），不是字号。</li>
     *       <li>auto + docGrid 关闭：line/240 当字号倍率，emit 无单位 line-height</li>
     *       <li>exact / atLeast：精确/最小行高，line/20 pt 绝对值</li>
     *     </ul>
     *   </li>
     * </ul>
     * <p>
     * Phase B：走 styleResolver，沿 docDefaults → pStyle 链 → self pPr 找首个命中的属性，
     * autospacing 也走继承链。
     */
    private void appendParagraphSpacing(StringBuilder style, XWPFParagraph para) {
        if (styleResolver == null) return;
        long before = styleResolver.resolveSpacingBefore(para);
        if (before >= 0) {
            style.append(String.format("margin-top:%.2fmm;", before / TWIPS_PER_MM));
        }
        long after = styleResolver.resolveSpacingAfter(para);
        if (after >= 0) {
            style.append(String.format("margin-bottom:%.2fmm;", after / TWIPS_PER_MM));
        }
        long line = styleResolver.resolveSpacingLine(para);
        if (line > 0) {
            String rule = styleResolver.resolveSpacingLineRule(para);
            if (rule == null) rule = "auto";
            switch (rule) {
                case "exact":
                case "atleast":
                    // 精确/最小：line/20 pt（标准 OOXML 行为）
                    style.append(String.format("line-height:%.2fpt;", line / 20.0));
                    break;
                case "auto":
                default:
                    if (styleResolver.isLineGridActive()) {
                        // docGrid type=lines/linesAndChars：行盒 = (line/240) × linePitch_pt
                        // line=240 时一倍 = linePitch_pt（比如 312tw=15.6pt）
                        double pitchPt = styleResolver.getDocGridLinePitchTwips() / 20.0;
                        double lineHeightPt = (line / 240.0) * pitchPt;
                        style.append(String.format("line-height:%.2fpt;", lineHeightPt));
                    } else {
                        // 非 lines 网格：line/240 当字号倍率
                        style.append(String.format("line-height:%.2f;", line / 240.0));
                    }
                    break;
            }
        } else if (styleResolver.isLineGridActive()) {
            // 没有显式 w:line：docGrid type=lines 的行盒必须 snap 到整数倍 linePitch。
            // 行盒高 ≈ 段内最大字号 × 1.2（CSS normal lineHeight 近似），不足 1 格按 1 格，
            // 超过则向上取整：snapped_pt = ceil(content_pt / pitch_pt) × pitch_pt
            // 例：14pt × 1.2 = 16.8pt，pitch=15.6pt → 2 × 15.6 = 31.2pt（与 WPS 实测一致）
            //     22pt × 1.2 = 26.4pt → 2 × 15.6 = 31.2pt
            //     16pt × 1.2 = 19.2pt → 2 × 15.6 = 31.2pt
            //     12pt × 1.2 = 14.4pt → 1 × 15.6 = 15.6pt
            long maxSzHalf = styleResolver.resolveMaxRunSzHalfPt(para);
            if (maxSzHalf > 0) {
                double pitchPt = styleResolver.getDocGridLinePitchTwips() / 20.0;
                double contentPt = (maxSzHalf / 2.0) * 1.2;
                int n = (int) Math.ceil(contentPt / pitchPt);
                if (n < 1) n = 1;
                double snappedPt = n * pitchPt;
                style.append(String.format("line-height:%.2fpt;", snappedPt));
            }
        }
        // 注意：未设 w:line 且不在 lines 网格下的段落不主动 emit line-height，
        // 让浏览器/openhtmltopdf 走 normal（≈1.2× font-size）。
    }

    /**
     * 段落级 emit 一份兜底 run 样式（sz / font-family / color / bold），
     * 来自 docDefaults → pStyle 链 → paraMark.rPr。
     * <p>
     * 必要性：DOCX 段落里大量"只设了 hint=eastAsia 没设其他属性"的 run，
     * 我们的 emitter 在 buildRunStyleCss 输出空字符串时走 fast-path 不包 span，
     * 这些文本就只能走 body 兜底字号。Word/WPS 的语义是这些文本应继承 pStyle/paraMark
     * 上的 sz/font，所以在 &lt;p&gt; 上 emit 一份默认值，再依靠 CSS 继承让裸文本拿到正确样式。
     * run 级 emit 通过 CSS 继承自然覆盖段落级。
     */
    private void appendParagraphDefaultRunStyle(StringBuilder style, XWPFParagraph para) {
        if (styleResolver == null) return;
        long halfPt = styleResolver.resolveParaDefaultSzHalfPt(para);
        if (halfPt > 0) {
            style.append("font-size:").append(halfPt / 2).append("pt;");
        }
        String fontFamily = styleResolver.resolveParaDefaultFontFamily(para);
        if (isWhitelistedFontFamily(fontFamily)) {
            style.append("font-family:'").append(fontFamily).append("';");
        }
        String color = styleResolver.resolveParaDefaultColor(para);
        if (color != null && !color.isEmpty()) {
            style.append("color:#").append(color).append(";");
        }
        if (styleResolver.resolveParaDefaultBold(para)) {
            style.append("font-weight:bold;");
        }
    }

    /**
     * 段落边框（pPr/pBdr）→ CSS border-*
     * <p>
     * Word 页眉常见 &lt;w:pBdr&gt;&lt;w:bottom w:val="single" w:sz="4"/&gt;&lt;/w:pBdr&gt; 画分隔线。
     * 这里转 border-{top|bottom|left|right}。
     */
    private void appendParagraphBorders(StringBuilder style, XWPFParagraph para) {
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr pPr = para.getCTP().getPPr();
        if (pPr == null) return;
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPBdr pBdr = pPr.getPBdr();
        if (pBdr == null) return;
        appendBorderSide(style, "top", pBdr.getTop());
        appendBorderSide(style, "bottom", pBdr.getBottom());
        appendBorderSide(style, "left", pBdr.getLeft());
        appendBorderSide(style, "right", pBdr.getRight());
    }

    private void appendBorderSide(StringBuilder style, String side,
                                  org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder border) {
        if (border == null) return;
        // sz 单位：1/8 pt（4 = 0.5pt，8 = 1pt）。null/0 用 0.5pt 兜底。
        long sz = 4;
        if (border.getSz() != null) {
            try {
                sz = new java.math.BigInteger(border.getSz().toString()).longValue();
            } catch (NumberFormatException ignored) {
            }
        }
        double widthPt = sz / 8.0;
        if (widthPt <= 0) widthPt = 0.5;

        // 颜色（auto → 黑）
        String color = "#000";
        Object colorObj = border.getColor();
        if (colorObj != null) {
            String cs = colorObj.toString();
            if (!cs.isEmpty() && !"auto".equalsIgnoreCase(cs)) {
                color = "#" + cs;
            }
        }

        // 样式（none → 不输出）
        String cssStyle = "solid";
        if (border.getVal() != null) {
            String v = border.getVal().toString().toLowerCase();
            if ("none".equals(v) || "nil".equals(v)) {
                return;
            }
            if ("dashed".equals(v) || "dashSmallGap".toLowerCase().equals(v)) cssStyle = "dashed";
            else if ("dotted".equals(v)) cssStyle = "dotted";
            else if ("double".equals(v)) cssStyle = "double";
        }

        style.append("border-").append(side).append(":")
                .append(String.format("%.2fpt %s %s;", widthPt, cssStyle, color));
    }

    /**
     * 段落背景色：pPr/shd w:fill
     */
    private void appendParagraphBackgroundColor(StringBuilder style, XWPFParagraph para) {
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr pPr = para.getCTP().getPPr();
        if (pPr == null) return;
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTShd shd = pPr.getShd();
        if (shd == null) return;
        Object fill = shd.getFill();
        if (fill == null) return;
        String hex = fill.toString();
        if (hex.isEmpty() || "auto".equalsIgnoreCase(hex)) return;
        style.append("background-color:#").append(hex).append(";");
    }

    private String mapStyleToTag(String styleId) {
        if (styleId == null) return "p";
        String lower = styleId.toLowerCase();
        if (lower.contains("heading1") || lower.equals("1")) return "h1";
        if (lower.contains("heading2") || lower.equals("2")) return "h2";
        if (lower.contains("heading3") || lower.equals("3")) return "h3";
        if (lower.contains("heading4") || lower.equals("4")) return "h4";
        if (lower.contains("heading5") || lower.equals("5")) return "h5";
        if (lower.contains("heading6") || lower.equals("6")) return "h6";
        return "p";
    }

    // ===== Run =====

    /**
     * 段落级 run 输出：处理 fldChar PAGE/NUMPAGES、w:tab、AlternateContent (WPS textbox)。
     * <p>
     * 直接遍历 CTR 序列而不仅是 XWPFRun，能识别普通 run 拿不到的细节：
     * <ul>
     *   <li>fldChar begin/separate/end + instrText：识别 PAGE / NUMPAGES，输出 CSS counter 占位</li>
     *   <li>w:tab：保留为半角空格序列（足以模拟 Word 默认 tab stop 视觉）</li>
     *   <li>mc:AlternateContent → mc:Choice (Requires=wps) → wps:txbx：WPS 文本框，递归输出内容</li>
     * </ul>
     */
    private void writeRunsWithFields(StringBuilder out, XWPFParagraph para) {
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP ctP = para.getCTP();
        // 建立 CTR → XWPFRun 映射，让 drawing 处理可以复用 writeRun 的图片路径
        java.util.IdentityHashMap<org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR, XWPFRun> ctrToRun =
                new java.util.IdentityHashMap<>();
        for (XWPFRun r : para.getRuns()) {
            ctrToRun.put(r.getCTR(), r);
        }

        FieldState state = new FieldState();
        org.apache.xmlbeans.XmlCursor cursor = ctP.newCursor();
        try {
            cursor.toFirstChild();
            do {
                org.apache.xmlbeans.XmlObject xo = cursor.getObject();
                if (xo == null) continue;
                if (xo instanceof org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR) {
                    handleRunCt(out,
                            (org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR) xo,
                            state, ctrToRun, para);
                } else {
                    String localName = cursor.getName() != null ? cursor.getName().getLocalPart() : "";
                    if ("hyperlink".equals(localName)) {
                        handleHyperlink(out, cursor, state, ctrToRun, para);
                    } else if ("AlternateContent".equals(localName)) {
                        handleAlternateContent(out, cursor, state, ctrToRun, para);
                    }
                }
            } while (cursor.toNextSibling());
        } finally {
            cursor.dispose();
        }
    }

    /**
     * fldChar 字段状态：在 begin..end 之间的 instrText 决定字段类型，end 时输出 placeholder
     */
    private static class FieldState {
        boolean inField = false;
        boolean afterSeparate = false;
        StringBuilder instr = new StringBuilder();

        void begin() {
            inField = true;
            afterSeparate = false;
            instr.setLength(0);
        }

        void separate() {
            afterSeparate = true;
        }

        void end() {
            inField = false;
            afterSeparate = false;
            instr.setLength(0);
        }

        boolean isPageField() {
            return inField && containsToken("PAGE") && !containsToken("NUMPAGES");
        }

        boolean isNumPagesField() {
            return inField && containsToken("NUMPAGES");
        }

        private boolean containsToken(String token) {
            String s = instr.toString();
            int idx = s.indexOf(token);
            if (idx < 0) return false;
            // 简单边界判断：前后非字母数字/下划线
            int before = idx - 1;
            int after = idx + token.length();
            char b = before >= 0 ? s.charAt(before) : ' ';
            char a = after < s.length() ? s.charAt(after) : ' ';
            return !Character.isLetterOrDigit(b) && b != '_' && !Character.isLetterOrDigit(a) && a != '_';
        }
    }

    // ===== CTR-aware run handlers（fldChar 状态机 / hyperlink / WPS textbox）=====

    /**
     * 处理一个 CTR：解析 fldChar 状态、instrText 累积、文本/制表符/换行/图片输出。
     * <p>
     * 字段状态机：begin → instrText 累积 → separate → 字段「计算结果」文本 → end
     * <ul>
     *   <li>begin..separate 之间：仅累积 instrText（不输出任何文字）</li>
     *   <li>separate..end 之间：若字段是 PAGE/NUMPAGES，跳过 docx 里 Word 缓存的旧值文本，
     *       end 时统一输出 CSS counter 占位 span</li>
     *   <li>非字段范围：正常输出 t/tab/br/drawing</li>
     * </ul>
     */
    private void handleRunCt(StringBuilder out,
                             org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR ctr,
                             FieldState state,
                             java.util.IdentityHashMap<org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR, XWPFRun> ctrToRun,
                             XWPFParagraph para) {
        XWPFRun run = ctrToRun.get(ctr);
        StringBuilder runStyle = buildRunStyleCss(ctr, run, para);
        org.apache.xmlbeans.XmlCursor cur = ctr.newCursor();
        try {
            handleRunChildren(out, cur, runStyle, state, ctr, ctrToRun, para);
        } finally {
            cur.dispose();
        }
    }

    /**
     * 处理一个 w:r 节点（无论是类型化的 CTR 还是 mc:AlternateContent 子树里 XmlAnyType 形态的节点）。
     * cursor 必须当前停留在 w:r 元素上；本方法负责枚举子节点完成 fldChar/instrText/t/tab/br/drawing/sym
     * 的渲染。XmlAnyType 形态下没有 rPr 样式且没法走 ctrToRun 的图片表，按降级路径处理。
     */
    private void handleRunCursor(StringBuilder out,
                                 org.apache.xmlbeans.XmlCursor runCursor,
                                 FieldState state,
                                 java.util.IdentityHashMap<org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR, XWPFRun> ctrToRun,
                                 XWPFParagraph para) {
        org.apache.xmlbeans.XmlObject xo = runCursor.getObject();
        StringBuilder runStyle;
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR typedCtr = null;
        if (xo instanceof org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR) {
            typedCtr = (org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR) xo;
            XWPFRun run = ctrToRun.get(typedCtr);
            runStyle = buildRunStyleCss(typedCtr, run, para);
        } else {
            // XmlAnyType 形态：rPr 也是 xs:any，从 schema 拿不到结构化数据，降级为不带样式
            runStyle = new StringBuilder();
        }
        org.apache.xmlbeans.XmlCursor cur = runCursor.newCursor();
        try {
            handleRunChildren(out, cur, runStyle, state, typedCtr, ctrToRun, para);
        } finally {
            cur.dispose();
        }
    }

    /**
     * 共享子节点遍历：cursor 当前必须停在 w:r 上，方法内部 toFirstChild 进入子节点。
     *
     * @param typedCtr 仅当当前 w:r 是 schema 类型化 CTR 时非空，用于 drawing/pict 路径下的图片解析
     */
    private void handleRunChildren(StringBuilder out,
                                   org.apache.xmlbeans.XmlCursor cur,
                                   StringBuilder runStyle,
                                   FieldState state,
                                   org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR typedCtr,
                                   java.util.IdentityHashMap<org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR, XWPFRun> ctrToRun,
                                   XWPFParagraph para) {
        // 临时缓冲，决定本 CTR 是否真的输出 <span>
        StringBuilder body = new StringBuilder();

        if (cur.toFirstChild()) {
            do {
                javax.xml.namespace.QName qn = cur.getName();
                if (qn == null) continue;
                String local = qn.getLocalPart();

                if ("fldChar".equals(local)) {
                    String type = cur.getAttributeText(new javax.xml.namespace.QName(
                            "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "fldCharType"));
                    if (type == null) type = cur.getAttributeText(new javax.xml.namespace.QName(null, "fldCharType"));
                    if ("begin".equalsIgnoreCase(type)) {
                        state.begin();
                    } else if ("separate".equalsIgnoreCase(type)) {
                        state.separate();
                    } else if ("end".equalsIgnoreCase(type)) {
                        // 字段结束时输出 placeholder
                        if (state.isPageField()) {
                            body.append("<span class=\"sdt-page-num\"></span>");
                        } else if (state.isNumPagesField()) {
                            body.append("<span class=\"sdt-page-count\"></span>");
                        }
                        state.end();
                    }
                } else if ("instrText".equals(local)) {
                    if (state.inField) {
                        state.instr.append(cur.getTextValue());
                    }
                } else if ("t".equals(local)) {
                    String text = cur.getTextValue();
                    if (text == null || text.isEmpty()) continue;
                    // 字段 separate 之后的「缓存值文本」（PAGE/NUMPAGES）跳过，由 CSS counter 接管
                    if (state.inField && state.afterSeparate
                            && (state.isPageField() || state.isNumPagesField())) {
                        continue;
                    }
                    body.append(escapeHtml(text));
                } else if ("tab".equals(local)) {
                    // 用两个 em 空格模拟 tab 视觉
                    body.append("&#x2003;&#x2003;");
                } else if ("br".equals(local)) {
                    String brType = cur.getAttributeText(new javax.xml.namespace.QName(
                            "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "type"));
                    if (brType == null) brType = cur.getAttributeText(new javax.xml.namespace.QName(null, "type"));
                    if ("page".equalsIgnoreCase(brType)) {
                        body.append("<br class=\"page-break\" />");
                    } else {
                        body.append("<br />");
                    }
                } else if ("drawing".equals(local) || "pict".equals(local)) {
                    if (typedCtr != null) {
                        XWPFRun run = ctrToRun.get(typedCtr);
                        if (run != null) {
                            for (XWPFPicture pic : run.getEmbeddedPictures()) {
                                writePicture(body, pic);
                            }
                        }
                    }
                } else if ("AlternateContent".equals(local)) {
                    // CTR 内嵌的 mc:AlternateContent（页脚 WPS 文本框包页码的常见结构）
                    // 委托给同名 handler，注意需要单独 cursor，否则 outer cur 会跑偏
                    org.apache.xmlbeans.XmlCursor altCur = cur.newCursor();
                    try {
                        handleAlternateContent(body, altCur, state, ctrToRun, para);
                    } finally {
                        altCur.dispose();
                    }
                } else if ("sym".equals(local)) {
                    String charHex = cur.getAttributeText(new javax.xml.namespace.QName(
                            "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "char"));
                    if (charHex != null) {
                        try {
                            int cp = Integer.parseInt(charHex, 16);
                            body.append("&#").append(cp).append(";");
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            } while (cur.toNextSibling());
        }

        if (body.length() == 0) return;
        if (runStyle.length() > 0) {
            out.append("<span style=\"").append(runStyle).append("\">").append(body).append("</span>");
        } else {
            out.append(body);
        }
    }

    /**
     * 处理超链接 &lt;w:hyperlink&gt;：内部一般是若干 &lt;w:r&gt;。这里递归遍历子 CTR。
     * PDF 转换不需要点击，直接以普通文本输出（如需保留链接可改 a 标签，这里保持简单）。
     */
    private void handleHyperlink(StringBuilder out,
                                 org.apache.xmlbeans.XmlCursor cursor,
                                 FieldState state,
                                 java.util.IdentityHashMap<org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR, XWPFRun> ctrToRun,
                                 XWPFParagraph para) {
        org.apache.xmlbeans.XmlCursor child = cursor.newCursor();
        try {
            if (!child.toFirstChild()) return;
            do {
                org.apache.xmlbeans.XmlObject xo = child.getObject();
                if (xo instanceof org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR) {
                    handleRunCt(out,
                            (org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR) xo,
                            state, ctrToRun, para);
                }
            } while (child.toNextSibling());
        } finally {
            child.dispose();
        }
    }

    /**
     * 处理 mc:AlternateContent。优先 mc:Choice，没有可用内容再退到 mc:Fallback。
     * 实际作用：还原 WPS / Word 文本框（页脚里包页码的常见结构）。
     */
    private void handleAlternateContent(StringBuilder out,
                                        org.apache.xmlbeans.XmlCursor cursor,
                                        FieldState state,
                                        java.util.IdentityHashMap<org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR, XWPFRun> ctrToRun,
                                        XWPFParagraph para) {
        // 先 Choice，再 Fallback
        org.apache.xmlbeans.XmlCursor child = cursor.newCursor();
        try {
            boolean rendered = false;
            if (child.toFirstChild()) {
                do {
                    javax.xml.namespace.QName qn = child.getName();
                    if (qn == null) continue;
                    String local = qn.getLocalPart();
                    if ("Choice".equals(local)) {
                        if (renderTextboxFromAlternate(out, child, state, ctrToRun, para)) {
                            rendered = true;
                            break;
                        }
                    }
                } while (child.toNextSibling());
            }
            if (!rendered) {
                child.dispose();
                child = cursor.newCursor();
                if (child.toFirstChild()) {
                    do {
                        javax.xml.namespace.QName qn = child.getName();
                        if (qn == null) continue;
                        if ("Fallback".equals(qn.getLocalPart())) {
                            renderTextboxFromAlternate(out, child, state, ctrToRun, para);
                            break;
                        }
                    } while (child.toNextSibling());
                }
            }
        } finally {
            child.dispose();
        }
    }

    /**
     * 在 Choice/Fallback 节点内查找 w:txbxContent，提取每个 w:p 渲染为内联段落。
     *
     * @return true 表示找到并输出了 textbox 段落（外层不再尝试 Fallback）
     */
    private boolean renderTextboxFromAlternate(StringBuilder out,
                                               org.apache.xmlbeans.XmlCursor branchCursor,
                                               FieldState state,
                                               java.util.IdentityHashMap<org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR, XWPFRun> ctrToRun,
                                               XWPFParagraph para) {
        org.apache.xmlbeans.XmlCursor scan = branchCursor.newCursor();
        boolean any = false;
        try {
            scan.selectPath("declare namespace w='http://schemas.openxmlformats.org/wordprocessingml/2006/main' "
                    + ".//w:txbxContent");
            while (scan.toNextSelection()) {
                // 在 txbxContent 内枚举 w:p（手动遍历子节点：xmlbeans selectPath("./w:p") 在某些
                // 上下文返回 0 命中，原因不详；toFirstChild + toNextSibling 更稳）
                org.apache.xmlbeans.XmlCursor inner = scan.newCursor();
                try {
                    if (inner.toFirstChild()) {
                        do {
                            javax.xml.namespace.QName qn = inner.getName();
                            if (qn == null) continue;
                            if (!"p".equals(qn.getLocalPart())) continue;
                            // 不再走 CTP.Factory.parse —— 那条路径会构造出一个外层 CTPImpl，
                            // 实际数据被嵌在它的第一个子节点里（一个匿名 <p>），后续遍历 r/AlternateContent
                            // 全部命中不到。直接用游标在「原始 w:p」内部遍历 w:r/w:hyperlink。
                            out.append("<span class=\"sdt-textbox\">");
                            walkTextboxParagraphChildren(out, inner, ctrToRun, para);
                            out.append("</span>");
                            any = true;
                        } while (inner.toNextSibling());
                    }
                } finally {
                    inner.dispose();
                }
            }
        } finally {
            scan.dispose();
        }
        return any;
    }

    /**
     * textbox 段落的 child 遍历：cursor 当前指向 mc:AlternateContent 子树里的 w:p 节点。
     * 这棵子树所有节点都是 XmlAnyTypeImpl（xs:any schema 降级），所以不能用 instanceof CTR。
     * 这里按 localName 分发，对 w:r 走 cursor 形态的 handleRunCursor，避免依赖 schema 类型化。
     */
    private void walkTextboxParagraphChildren(StringBuilder out,
                                              org.apache.xmlbeans.XmlCursor pCursor,
                                              java.util.IdentityHashMap<org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR, XWPFRun> ctrToRun,
                                              XWPFParagraph para) {
        // textbox 段落使用独立的 FieldState，避免污染外层段落
        FieldState state = new FieldState();
        org.apache.xmlbeans.XmlCursor cur = pCursor.newCursor();
        try {
            if (!cur.toFirstChild()) return;
            do {
                javax.xml.namespace.QName qn = cur.getName();
                if (qn == null) continue;
                String local = qn.getLocalPart();
                if ("r".equals(local)) {
                    handleRunCursor(out, cur, state, ctrToRun, para);
                } else if ("hyperlink".equals(local)) {
                    handleHyperlink(out, cur, state, ctrToRun, para);
                } else if ("AlternateContent".equals(local)) {
                    handleAlternateContent(out, cur, state, ctrToRun, para);
                }
            } while (cur.toNextSibling());
        } finally {
            cur.dispose();
        }
    }

    // (replaced by ctrToRun-aware overloads below)

    /**
     * 抽 run 视觉样式（粗体、斜体、字号、颜色、字体）→ CSS 文本。
     * <p>
     * 优先走 styleResolver（支持 docDefaults / pStyle 链 / rStyle 链 / 自身 rPr 的继承合并）；
     * 当 run 或 para 为 null（如 mc:AlternateContent textbox 内的 XmlAnyType run）退化到
     * 仅读 CTR.rPr 的旧路径，保持向后兼容。
     */
    private StringBuilder buildRunStyleCss(org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR ctr,
                                           XWPFRun run,
                                           XWPFParagraph para) {
        if (styleResolver != null && run != null) {
            // 继承感知路径
            StringBuilder style = new StringBuilder();
            if (styleResolver.resolveRunBold(run, para)) style.append("font-weight:bold;");
            if (styleResolver.resolveRunItalic(run, para)) style.append("font-style:italic;");
            // u / strike：CTRPr 上是数组，没法走通用 resolver；走旧路径补回（仅 self rPr，
            // 这两个属性几乎都是 run 级直接给的，未观察到通过 pStyle 继承的实际场景）
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr selfRPr = ctr.getRPr();
            if (selfRPr != null) {
                if (hasUnderline(selfRPr)) {
                    style.append("text-decoration:underline;");
                }
                if (hasStrike(selfRPr)) style.append("text-decoration:line-through;");
            }
            String color = styleResolver.resolveRunColor(run, para);
            if (color != null && !color.isEmpty()) {
                style.append("color:#").append(color).append(";");
            }
            long halfPt = styleResolver.resolveRunSzHalfPt(run, para);
            if (halfPt > 0) {
                style.append("font-size:").append(halfPt / 2).append("pt;");
            }
            String fontFamily = styleResolver.resolveRunFontFamily(run, para);
            if (isWhitelistedFontFamily(fontFamily)) {
                style.append("font-family:'").append(fontFamily).append("';");
            }
            // 背景色（shd > highlight）：仅 self rPr，不沿继承（与旧路径保持一致）
            appendCtrBackgroundColor(style, ctr);
            return style;
        }
        // 降级路径：textbox 等 XmlAnyType run，没有 XWPFRun，仅读 CTR.rPr
        return buildRunStyleCssFallback(ctr);
    }

    /**
     * 仅读 self rPr 的旧路径（textbox / 无 para 上下文场景使用）
     */
    private StringBuilder buildRunStyleCssFallback(org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR ctr) {
        StringBuilder style = new StringBuilder();
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr rPr = ctr.getRPr();
        if (rPr == null) return style;
        if (rPr.sizeOfBArray() > 0) style.append("font-weight:bold;");
        if (rPr.sizeOfIArray() > 0) style.append("font-style:italic;");
        if (hasUnderline(rPr)) {
            style.append("text-decoration:underline;");
        }
        if (hasStrike(rPr)) style.append("text-decoration:line-through;");
        if (rPr.sizeOfColorArray() > 0) {
            Object v = rPr.getColorArray(0).getVal();
            if (v != null) {
                String c = v.toString();
                if (!c.isEmpty() && !"auto".equalsIgnoreCase(c)) {
                    style.append("color:#").append(c).append(";");
                }
            }
        }
        if (rPr.sizeOfSzArray() > 0) {
            Object v = rPr.getSzArray(0).getVal();
            if (v != null) {
                try {
                    long half = new java.math.BigInteger(v.toString()).longValue();
                    style.append("font-size:").append(half / 2).append("pt;");
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (rPr.sizeOfRFontsArray() > 0) {
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts f = rPr.getRFontsArray(0);
            String name = null;
            if (f.getAscii() != null && !f.getAscii().isEmpty()) name = f.getAscii();
            else if (f.getEastAsia() != null && !f.getEastAsia().isEmpty()) name = f.getEastAsia();
            else if (f.getHAnsi() != null && !f.getHAnsi().isEmpty()) name = f.getHAnsi();
            if (isWhitelistedFontFamily(name)) {
                style.append("font-family:'").append(name).append("';");
            }
        }
        appendCtrBackgroundColor(style, ctr);
        return style;
    }

    /**
     * run 背景色：shd 优先（HEX 精确），其次 highlight 命名色。读 self rPr。
     */
    private void appendCtrBackgroundColor(StringBuilder style,
                                          org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR ctr) {
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr rPr = ctr.getRPr();
        if (rPr == null) return;
        Object shd = getRunProperty(rPr, "getShd", "sizeOfShdArray", "getShdArray");
        if (shd != null) {
            try {
                Object fill = shd.getClass().getMethod("getFill").invoke(shd);
                if (fill != null) {
                    String hex = fill.toString();
                    if (!hex.isEmpty() && !"auto".equalsIgnoreCase(hex)) {
                        style.append("background-color:#").append(hex).append(";");
                    }
                }
            } catch (Exception e) {
                log.debug("读取 OOXML run 背景色失败: {}", e.getMessage());
            }
            return;
        }
        Object highlight = getRunProperty(rPr, "getHighlight", "sizeOfHighlightArray", "getHighlightArray");
        if (highlight != null) {
            try {
                Object val = highlight.getClass().getMethod("getVal").invoke(highlight);
                if (val != null) {
                    String css = mapHighlightColorName(val.toString());
                    if (css != null) {
                        style.append("background-color:").append(css).append(";");
                    }
                }
            } catch (Exception e) {
                log.debug("读取 OOXML run 高亮色失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 从 CTR 拿第一个图片输出（已被 ctrToRun 版本取代，保留空实现避免引用错误）
     */
    private void writeDrawingFromCtr(StringBuilder out,
                                     org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR ctr) {
        // no-op: drawing 现由 handleRunCt(ctrToRun) 路径处理
    }

    private void writeRun(StringBuilder out, XWPFRun run) {
        // 图片检测
        for (XWPFPicture pic : run.getEmbeddedPictures()) {
            writePicture(out, pic);
            return;
        }

        String text = run.getText(0);
        if (text == null || text.isEmpty()) return;

        StringBuilder style = new StringBuilder();
        if (run.isBold()) style.append("font-weight:bold;");
        if (run.isItalic()) style.append("font-style:italic;");
        if (run.getUnderline() != UnderlinePatterns.NONE) {
            style.append("text-decoration:underline;");
        }
        if (run.isStrikeThrough()) style.append("text-decoration:line-through;");
        String color = run.getColor();
        if (color != null && !color.isEmpty() && !"auto".equalsIgnoreCase(color)) {
            style.append("color:#").append(color).append(";");
        }
        int fontSize = run.getFontSize();
        if (fontSize > 0) {
            style.append("font-size:").append(fontSize).append("pt;");
        }
        String fontFamily = run.getFontFamily();
        if (isWhitelistedFontFamily(fontFamily)) {
            style.append("font-family:'").append(fontFamily).append("';");
        }
        // 背景色：w:highlight（命名色） + w:shd w:fill（HEX 色）
        appendRunBackgroundColor(style, run);

        out.append("<span");
        if (style.length() > 0) {
            out.append(" style=\"").append(style).append("\"");
        }
        out.append(">");
        out.append(escapeHtml(text));
        out.append("</span>");
    }

    /**
     * 把 run 的 highlight / shd 转换成 CSS background-color
     * <p>
     * Word 两种背景色：
     * <ul>
     *   <li>&lt;w:highlight w:val="yellow"/&gt; — 命名色（yellow / red / green / cyan / ...）</li>
     *   <li>&lt;w:shd w:fill="FFFF00"/&gt; — HEX 色（更精确）</li>
     * </ul>
     * 优先 shd（HEX 更精确），其次 highlight 命名色。
     */
    private void appendRunBackgroundColor(StringBuilder style, XWPFRun run) {
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr rPr = run.getCTR().getRPr();
        if (rPr == null) return;

        // shd（HEX 优先）— CTRPr 用 array 风格
        if (rPr.sizeOfShdArray() > 0) {
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTShd shd = rPr.getShdArray(0);
            Object fill = shd.getFill();
            if (fill != null) {
                String hex = fill.toString();
                if (!hex.isEmpty() && !"auto".equalsIgnoreCase(hex)) {
                    style.append("background-color:#").append(hex).append(";");
                    return;
                }
            }
        }

        // highlight（命名色）
        if (rPr.sizeOfHighlightArray() > 0) {
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTHighlight hl = rPr.getHighlightArray(0);
            Object val = hl.getVal();
            if (val != null) {
                String name = val.toString();
                String css = mapHighlightColorName(name);
                if (css != null) {
                    style.append("background-color:").append(css).append(";");
                }
            }
        }
    }

    /**
     * Word 命名 highlight 色映射到 CSS 颜色名
     */
    private String mapHighlightColorName(String name) {
        if (name == null) return null;
        switch (name.toLowerCase()) {
            case "yellow":
                return "yellow";
            case "green":
                return "lime";
            case "cyan":
                return "cyan";
            case "magenta":
                return "magenta";
            case "blue":
                return "blue";
            case "red":
                return "red";
            case "darkblue":
                return "#000080";
            case "darkcyan":
                return "#008080";
            case "darkgreen":
                return "#008000";
            case "darkmagenta":
                return "#800080";
            case "darkred":
                return "#800000";
            case "darkyellow":
                return "#808000";
            case "darkgray":
                return "#808080";
            case "lightgray":
                return "#C0C0C0";
            case "black":
                return "black";
            case "white":
                return "white";
            case "none":
                return null;
            default:
                return null;
        }
    }

    // ===== 图片 =====

    private void writePicture(StringBuilder out, XWPFPicture pic) {
        try {
            XWPFPictureData picData = pic.getPictureData();
            if (picData == null) return;
            String mime = getMimeType(picData.getPictureType());
            String base64 = Base64.getEncoder().encodeToString(picData.getData());

            // POI XWPFPicture.getWidth()/getDepth() 在 inline drawing 下返回的是 cm（double）；
            // 在 anchor / 缺失 wp:extent 的情况下可能为 0。两种宽高都需要兜底。
            // 我们直接读 wp:extent / pic:spPr 的 EMU 值；都没有就根据真实图片像素回退。
            long cxEmu = readPictureExtent(pic, "cx");
            long cyEmu = readPictureExtent(pic, "cy");

            int widthPx;
            int heightPx;
            if (cxEmu > 0 && cyEmu > 0) {
                widthPx = (int) (cxEmu / 9525.0); // 1 px = 9525 EMU (96 DPI)
                heightPx = (int) (cyEmu / 9525.0);
            } else {
                // 用真实图片尺寸（PNG / JPG 头解析）做兜底
                int[] dim = readImageDimensions(picData.getData(), picData.getPictureType());
                widthPx = dim[0] > 0 ? dim[0] : 200;
                heightPx = dim[1] > 0 ? dim[1] : 100;
            }

            out.append(String.format("<img src=\"data:%s;base64,%s\" width=\"%d\" height=\"%d\" />",
                    mime, base64, widthPx, heightPx));
        } catch (Exception e) {
            log.warn("图片转换失败: {}", e.getMessage());
        }
    }

    /**
     * 从 picture 的 spPr / wp:extent 中读 EMU
     */
    private long readPictureExtent(XWPFPicture pic, String attr) {
        try {
            org.apache.xmlbeans.XmlCursor cur = pic.getCTPicture().newCursor();
            try {
                cur.selectPath("declare namespace a='http://schemas.openxmlformats.org/drawingml/2006/main' .//a:ext");
                if (cur.toNextSelection()) {
                    String v = cur.getAttributeText(new javax.xml.namespace.QName(null, attr));
                    if (v != null) {
                        try {
                            return Long.parseLong(v);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            } finally {
                cur.dispose();
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    /**
     * 从 PNG/JPEG 字节流读取图像尺寸（不依赖外部库），失败返回 [0,0]
     */
    private int[] readImageDimensions(byte[] data, int pictureType) {
        if (data == null || data.length < 8) return new int[]{0, 0};
        try {
            // PNG: 8 字节签名后第 16 字节起 IHDR：4 字节 width + 4 字节 height (big endian)
            if (pictureType == XWPFDocument.PICTURE_TYPE_PNG && data.length >= 24) {
                int w = ((data[16] & 0xFF) << 24) | ((data[17] & 0xFF) << 16) | ((data[18] & 0xFF) << 8) | (data[19] & 0xFF);
                int h = ((data[20] & 0xFF) << 24) | ((data[21] & 0xFF) << 16) | ((data[22] & 0xFF) << 8) | (data[23] & 0xFF);
                return new int[]{w, h};
            }
            // JPEG: 扫 SOF0/SOF2 段
            if (pictureType == XWPFDocument.PICTURE_TYPE_JPEG) {
                int i = 2; // 跳过 0xFFD8
                while (i < data.length - 8) {
                    if ((data[i] & 0xFF) != 0xFF) {
                        i++;
                        continue;
                    }
                    int marker = data[i + 1] & 0xFF;
                    if (marker == 0xC0 || marker == 0xC2) {
                        int h = ((data[i + 5] & 0xFF) << 8) | (data[i + 6] & 0xFF);
                        int w = ((data[i + 7] & 0xFF) << 8) | (data[i + 8] & 0xFF);
                        return new int[]{w, h};
                    }
                    int segLen = ((data[i + 2] & 0xFF) << 8) | (data[i + 3] & 0xFF);
                    i += 2 + segLen;
                }
            }
        } catch (Exception ignored) {
        }
        return new int[]{0, 0};
    }

    private String getMimeType(int pictureType) {
        switch (pictureType) {
            case XWPFDocument.PICTURE_TYPE_JPEG:
                return "image/jpeg";
            case XWPFDocument.PICTURE_TYPE_GIF:
                return "image/gif";
            case XWPFDocument.PICTURE_TYPE_BMP:
                return "image/bmp";
            default:
                return "image/png";
        }
    }

    // ===== 表格单元格字号解析 =====

    /**
     * 从 styles.xml 直接解析 Normal Table 样式的有效字号（half-pt → pt）。
     * <p>
     * 样式用 w:name 而非 styleId 区分类型：w:name="Normal Table" 或 "Table Normal" 等。
     * 沿 basedOn 链向上找首个有 w:sz 的样式。
     */
    private double resolveTableCellFontPtFromZip(byte[] docxBytes) {
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(
                java.io.File.createTempFile("docx-", ".zip"))) {
            // 空实现，留给下面 try-with-resources
        } catch (Exception suppressed) {
        }
        java.util.zip.ZipFile zip = null;
        try {
            java.nio.file.Path tmp = java.nio.file.Files.createTempFile(
                    SimpleDocTemplateConstant.TEMP_DOCX_PREFIX, SimpleDocTemplateConstant.TEMP_ZIP_SUFFIX);
            java.nio.file.Files.write(tmp, docxBytes);
            zip = new java.util.zip.ZipFile(tmp.toFile());
            tmp.toFile().deleteOnExit();

            java.util.zip.ZipEntry stylesEntry = zip.getEntry("word/styles.xml");
            if (stylesEntry == null) return -1;
            String raw = new String(StreamUtils.copyToByteArray(zip.getInputStream(stylesEntry)),
                    java.nio.charset.StandardCharsets.UTF_8);
            return parseTableCellFontPtFromXml(raw);
        } catch (Exception e) {
            log.debug("解析表格字号失败: {}", e.getMessage());
            return -1;
        } finally {
            if (zip != null) try {
                zip.close();
            } catch (Exception ignored) {
            }
        }
    }

    private double parseTableCellFontPtFromXml(String xml) {
        // 匹配 <w:style ...>...<w:name w:val="Normal Table"/>...<w:sz w:val="20"/>
        // 找所有 table 类型 style，匹配 name 后沿 basedOn 链读 sz
        java.util.regex.Pattern stylePat =
                java.util.regex.Pattern.compile(
                        "<w:style\\b[^>]*>.*?</w:style>",
                        java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher styleM = stylePat.matcher(xml);
        java.util.Map<String, String> styleById = new java.util.LinkedHashMap<>();
        java.util.Map<String, String> nameById = new java.util.HashMap<>();
        java.util.Map<String, String> basedOnById = new java.util.HashMap<>();
        java.util.Map<String, String> szById = new java.util.HashMap<>();
        java.util.regex.Pattern idPat = java.util.regex.Pattern.compile(
                "<w:style\\b[^>]*styleId=\"([^\"]+)\"[^>]*>");
        java.util.regex.Pattern namePat = java.util.regex.Pattern.compile(
                "<w:name\\b[^>]*w:val=\"([^\"]+)\"");
        java.util.regex.Pattern basedPat = java.util.regex.Pattern.compile(
                "<w:basedOn\\b[^>]*w:val=\"([^\"]+)\"");
        java.util.regex.Pattern szPat = java.util.regex.Pattern.compile(
                "<w:sz\\b[^>]*w:val=\"([^\"]+)\"");
        java.util.regex.Pattern typePat = java.util.regex.Pattern.compile(
                "<w:style\\b[^>]*w:type=\"([^\"]+)\"");
        // 快速收集所有 style 信息
        while (styleM.find()) {
            String block = styleM.group();
            java.util.regex.Matcher idM = idPat.matcher(block);
            if (!idM.find()) continue;
            String id = idM.group(1);
            java.util.regex.Matcher typeM = typePat.matcher(block);
            String type = typeM.find() ? typeM.group(1) : "";
            java.util.regex.Matcher nameM = namePat.matcher(block);
            String name = nameM.find() ? nameM.group(1) : "";
            java.util.regex.Matcher basedM = basedPat.matcher(block);
            String based = basedM.find() ? basedM.group(1) : "";
            java.util.regex.Matcher szM = szPat.matcher(block);
            String sz = szM.find() ? szM.group(1) : "";
            styleById.put(id, block);
            nameById.put(id, name);
            basedOnById.put(id, based);
            if (!sz.isEmpty()) szById.put(id, sz);
        }
        // 找 Normal Table 样式
        String rootId = null;
        for (java.util.Map.Entry<String, String> e : nameById.entrySet()) {
            String n = e.getValue();
            if (n != null && (n.equalsIgnoreCase("Normal Table")
                    || n.equalsIgnoreCase("Table Normal")
                    || n.equalsIgnoreCase("Normal"))) {
                // 确认是 table 类型
                String block = styleById.get(e.getKey());
                if (block != null && block.contains("w:type=\"table\"")) {
                    rootId = e.getKey();
                    break;
                }
            }
        }
        if (rootId == null) {
            // 退化：找根 table 样式（无 basedOn）
            for (java.util.Map.Entry<String, String> e : basedOnById.entrySet()) {
                String id = e.getKey();
                String block = styleById.get(id);
                if (block != null && block.contains("w:type=\"table\"")
                        && (e.getValue() == null || e.getValue().isEmpty()
                        || !basedOnById.containsKey(e.getValue()))) {
                    rootId = id;
                    break;
                }
            }
        }
        if (rootId == null) return -1;
        // 沿 basedOn 链找 sz
        String cur = rootId;
        int safety = 16;
        while (cur != null && safety-- > 0) {
            String sz = szById.get(cur);
            if (sz != null && !sz.isEmpty()) {
                try {
                    return Long.parseLong(sz) / 2.0;
                } catch (NumberFormatException ignored) {
                }
            }
            cur = basedOnById.get(cur);
        }
        return -1;
    }

    // ===== 表格 =====

    private void writeTable(StringBuilder out, XWPFTable table) {
        // table-layout:fixed（来自全局样式）+ 显式 width + colgroup：列宽由 tblGrid 严格驱动。
        // 不再无条件 width:100%，否则会丢失 OOXML 中 tblW="pct">100% 或 tblW="dxa" 的真实表宽，
        // 也丢失 jc=center / tblInd 带来的水平偏移（page 5 表格右移 +7.11mm 即此问题）。
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTbl ctTbl = table.getCTTbl();
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr tblPr =
                (ctTbl != null) ? ctTbl.getTblPr() : null;

        // 1) 解析表宽（mm）；<=0 表示无显式宽度，走 100%
        double widthMm = resolveTblWidthMm(tblPr);

        // 2) 解析 jc + tblInd → 外层 wrapper 的偏移（mm）。当 widthMm > contentWidthMm 时为负
        //    用 wrapper div 而不是 table 自身的 margin：openhtmltopdf 对 table 的负 margin 会被钳制
        double[] wrapMargins = resolveWrapperMarginMm(tblPr, widthMm);
        double leftMm = wrapMargins[0];
        double rightMm = wrapMargins[1];

        boolean hasWrapper = leftMm != 0 || rightMm != 0
                || (Double.isNaN(leftMm) || Double.isNaN(rightMm)); // NaN sentinel = auto
        if (hasWrapper) {
            out.append("<div style=\"");
            // NaN sentinel 表示 auto；具体数值（含负数）走显式 mm
            if (Double.isNaN(leftMm)) {
                out.append("margin-left:auto;");
            } else if (leftMm != 0) {
                out.append(String.format(java.util.Locale.ROOT, "margin-left:%.2fmm;", leftMm));
            }
            if (Double.isNaN(rightMm)) {
                out.append("margin-right:auto;");
            } else if (rightMm != 0) {
                out.append(String.format(java.util.Locale.ROOT, "margin-right:%.2fmm;", rightMm));
            }
            out.append("\">\n");
        }

        String widthCss = (widthMm > 0)
                ? String.format(java.util.Locale.ROOT, "width:%.2fmm;", widthMm)
                : "width:100%;";
        out.append("<table style=\"").append(widthCss).append("\">\n");

        // 3) 用 tblGrid 的 gridCol 计算每列的百分比，emit <colgroup>
        appendColgroupFromTblGrid(out, ctTbl);

        for (XWPFTableRow row : table.getRows()) {
            out.append("<tr>");
            for (XWPFTableCell cell : row.getTableCells()) {
                StringBuilder tdStyle = new StringBuilder();

                // 行高控制：从段落属性推断 Word 默认 line-height（1.1×字号），
                // 覆盖浏览器默认 normal（≈1.2×字号），避免行高偏大导致 page5 similarity 低至 1-2%。
                // - w:line 显式：writeParagraph 已覆盖（如表头 line=320 exact → 16pt）
                // - 无显式 w:line：Word 默认 = 字号×1.1，字号默认 11pt → 12.1pt
                long halfPt = -1;
                for (XWPFParagraph para : cell.getParagraphs()) {
                    if (styleResolver != null) {
                        long sz = styleResolver.resolveMaxRunSzHalfPt(para);
                        if (sz > 0) {
                            halfPt = sz;
                            break;
                        }
                    }
                }
                if (halfPt <= 0)
                    halfPt = SimpleDocTemplateConstant.WORD_DEFAULT_FONT_HALF_PT; // Word 默认字号 11pt = 22 halfPt
                double dataLinePt = (halfPt / 2.0) * 1.1;
                // data 格默认 line-height（覆盖浏览器 normal），writeParagraph 会覆盖此值如果段落有显式 w:line
                tdStyle.append(String.format(java.util.Locale.ROOT, "line-height:%.1fpt;", dataLinePt));

                // 背景色
                String bgColor = cell.getColor();
                if (bgColor != null && !bgColor.isEmpty() && !"auto".equalsIgnoreCase(bgColor)) {
                    tdStyle.append("background-color:#").append(bgColor).append(";");
                }
                // 垂直对齐：仅在显式设置为 center/bottom 时写，避免覆盖默认 top
                XWPFTableCell.XWPFVertAlign vAlign = cell.getVerticalAlignment();
                if (vAlign == XWPFTableCell.XWPFVertAlign.CENTER) {
                    tdStyle.append("vertical-align:middle;");
                } else if (vAlign == XWPFTableCell.XWPFVertAlign.BOTTOM) {
                    tdStyle.append("vertical-align:bottom;");
                }
                // colspan：master 单元格 gridSpan>1 时输出 colspan，让 colgroup 列宽正确累加
                int colspan = getCellColspan(cell);
                out.append("<td");
                if (colspan > 1) {
                    out.append(" colspan=\"").append(colspan).append("\"");
                }
                out.append(" style=\"").append(tdStyle).append("\">");
                for (XWPFParagraph para : cell.getParagraphs()) {
                    writeParagraph(out, para, null);
                }
                out.append("</td>");
            }
            out.append("</tr>\n");
        }

        out.append("</table>\n");
        if (hasWrapper) {
            out.append("</div>\n");
        }
    }

    /**
     * tblPr/tblW → 表格宽度（mm）。
     * - type="pct"（5000=100%）：基于 contentWidthMm 转为绝对 mm
     * - type="dxa"：twips → mm
     * - type="auto"/"nil"/缺失：返回 -1，表示"无显式宽度，走 100%"
     * <p>结果会被钳制到 contentWidthMm 上限：openhtmltopdf 对负 margin 会单侧钳制导致表格不对称溢出，
     * 视觉上比"略窄但完整"更糟。优先保证表格完整显示在内容区内。
     */
    private double resolveTblWidthMm(org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr tblPr) {
        if (tblPr == null || !tblPr.isSetTblW()) return -1;
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth tw = tblPr.getTblW();
        String type = (tw.getType() != null) ? tw.getType().toString() : "auto";
        long w = getSchemaLong(tw, "getW", -1);
        if (w <= 0 || "auto".equalsIgnoreCase(type) || "nil".equalsIgnoreCase(type)) {
            return -1;
        }
        double mm;
        if ("pct".equalsIgnoreCase(type)) {
            double pct = w / 50.0; // 50ths of a percent
            double cw = contentWidthMm > 0 ? contentWidthMm : 146.6;
            mm = cw * pct / 100.0;
        } else if ("dxa".equalsIgnoreCase(type)) {
            mm = w / TWIPS_PER_MM;
        } else {
            return -1;
        }
        // 钳制到内容区宽度，避免单侧溢出
        if (contentWidthMm > 0 && mm > contentWidthMm) {
            mm = contentWidthMm;
        }
        return mm;
    }

    /**
     * 计算 wrapper div 的 margin-left/right（mm）。
     * <p>返回 {leftMm, rightMm}；{@code Double.NaN} 表示该侧用 {@code margin:auto}。
     * <p>策略：把 table 自身保持"裸"的 width 不带 margin，所有水平定位/溢出由外层 div 负责，
     * 因为 openhtmltopdf 对 &lt;table&gt; 自身的负 margin 会被钳制，对 &lt;div&gt; 不会。
     * <ul>
     *   <li>jc=center + 已知 widthMm：左右各 (content-width)/2 mm，可负</li>
     *   <li>jc=center + 未知 widthMm：左右 auto</li>
     *   <li>jc=right + 已知 widthMm：left=(content-width) mm，right=0</li>
     *   <li>jc=right + 未知 widthMm：left=auto</li>
     *   <li>默认/jc=left：tblInd → margin-left（dxa→mm）；为 0 时 {0,0}（无 wrapper）</li>
     * </ul>
     */
    private double[] resolveWrapperMarginMm(org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr tblPr,
                                            double widthMm) {
        if (tblPr == null) return new double[]{0, 0};
        String jc = null;
        if (tblPr.isSetJc()) {
            Object tableJc = invokeSchemaMethod(tblPr, "getJc");
            Object value = invokeSchemaMethod(tableJc, "getVal");
            if (value != null) {
                jc = value.toString();
            }
        }
        long indDxa = 0;
        if (tblPr.isSetTblInd()) {
            indDxa = getSchemaLong(tblPr.getTblInd(), "getW", -1);
        }
        if ("center".equalsIgnoreCase(jc)) {
            if (widthMm > 0 && contentWidthMm > 0) {
                double sideMm = (contentWidthMm - widthMm) / 2.0;
                return new double[]{sideMm, sideMm};
            }
            return new double[]{Double.NaN, Double.NaN};
        }
        if ("right".equalsIgnoreCase(jc) || "end".equalsIgnoreCase(jc)) {
            if (widthMm > 0 && contentWidthMm > 0) {
                return new double[]{contentWidthMm - widthMm, 0};
            }
            return new double[]{Double.NaN, 0};
        }
        // left / start / 默认
        if (indDxa > 0) {
            return new double[]{indDxa / TWIPS_PER_MM, 0};
        }
        return new double[]{0, 0};
    }

    /**
     * 从 tblGrid/gridCol 序列产出 <colgroup>，每列宽度按 gridCol w 比例分配。
     * - 任一 gridCol 缺失或总和 ≤ 0 时直接跳过 colgroup，浏览器走默认列分配
     * - 与 table-layout:fixed 配合：第一行的 gridSpan 不会再决定列宽，列宽完全由 colgroup 决定
     */
    private void appendColgroupFromTblGrid(StringBuilder out,
                                           org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTbl ctTbl) {
        if (ctTbl == null) return;
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGrid grid = ctTbl.getTblGrid();
        if (grid == null) return;
        java.util.List<org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGridCol> cols =
                grid.getGridColList();
        if (cols == null || cols.isEmpty()) return;
        long[] widths = new long[cols.size()];
        long total = 0;
        for (int i = 0; i < cols.size(); i++) {
            long w = getSchemaLong(cols.get(i), "getW", -1);
            widths[i] = Math.max(0, w);
            total += widths[i];
        }
        if (total <= 0) return;
        out.append("<colgroup>");
        for (long w : widths) {
            double pct = (w * 100.0) / total;
            out.append(String.format(java.util.Locale.ROOT, "<col style=\"width:%.4f%%\"/>", pct));
        }
        out.append("</colgroup>\n");
    }

    /**
     * tblW/tblInd/gridCol 的 w 字段统一解析：可能是 BigInteger / Integer / "auto" / 数字字符串。
     * 非数字（如 "auto"）或负值返回 -1，由调用方按缺失处理。
     */
    private static long parseTblWidthW(Object wObj) {
        if (wObj == null) return -1;
        if (wObj instanceof BigInteger) return ((BigInteger) wObj).longValue();
        if (wObj instanceof Integer) return ((Integer) wObj).longValue();
        if (wObj instanceof Long) return (Long) wObj;
        try {
            return Long.parseLong(wObj.toString().trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * 用第一行各 master 列宽（EMU）计算每列占总宽度的百分比。
     * - 跳过 vMerge="continue" 的续接格（宽度已计入 master）
     * - 有 colspan 的 master 格宽度均分到每列
     * - 返回 double[]，单位是小数（0.15 = 15%）
     */
    private double[] computeColWidthPcts(XWPFTable table) {
        if (table.getRows().isEmpty()) return new double[0];
        XWPFTableRow firstRow = table.getRows().get(0);

        // 收集第一行各 master 列宽度（EMU）
        double[] rawWidths = new double[firstRow.getTableCells().size()];
        for (int ci = 0; ci < firstRow.getTableCells().size(); ci++) {
            XWPFTableCell cell = firstRow.getTableCells().get(ci);
            Long wEmu = getCellWidthEmu(cell);
            if (wEmu != null && wEmu > 0) {
                int colspan = getCellColspan(cell);
                rawWidths[ci] = (double) wEmu / colspan;
            } else {
                rawWidths[ci] = -1; // 待补
            }
        }

        // 计算总宽度
        double totalWidth = 0;
        for (double w : rawWidths) {
            if (w > 0) totalWidth += w;
        }
        if (totalWidth <= 0) totalWidth = 1;

        // 补全无宽度列：均分剩余空间
        int missingCount = 0;
        for (double w : rawWidths) {
            if (w <= 0) missingCount++;
        }
        double fillWidth = (missingCount > 0) ? totalWidth / missingCount : 0;
        int missIdx = 0;
        for (int i = 0; i < rawWidths.length; i++) {
            if (rawWidths[i] <= 0) {
                rawWidths[i] = fillWidth;
                missIdx++;
            }
        }

        // 转换为百分比（小数）
        double[] pcts = new double[rawWidths.length];
        for (int i = 0; i < rawWidths.length; i++) {
            pcts[i] = rawWidths[i] / totalWidth;
        }
        return pcts;
    }

    private Long getCellWidthEmu(XWPFTableCell cell) {
        try {
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTc tc = cell.getCTTc();
            if (tc == null) return null;
            if (!tc.isSetTcPr()) return null;
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr tcPr = tc.getTcPr();
            if (tcPr == null || !tcPr.isSetTcW()) return null;
            long w = getSchemaLong(tcPr.getTcW(), "getW", -1);
            return w > 0 ? w : null;
        } catch (Exception e) {
            return null;
        }
    }

    private int getCellColspan(XWPFTableCell cell) {
        try {
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTc tc = cell.getCTTc();
            if (tc == null || !tc.isSetTcPr()) return 1;
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr tcPr = tc.getTcPr();
            if (tcPr == null || !tcPr.isSetGridSpan()) return 1;
            Object v = tcPr.getGridSpan().getVal();
            if (v instanceof BigInteger) return ((BigInteger) v).intValue();
            if (v instanceof Integer) return (Integer) v;
            return Integer.parseInt(v.toString().trim());
        } catch (Exception e) {
            return 1;
        }
    }

    // ===== 列表（前缀由 NumberingProcessor 在 writeParagraph 处生成）=====

    // ===== 内容宽度计算 =====

    /**
     * 从 sectPr 计算内容区宽度（px）：pageW - leftMargin - rightMargin。
     * 1 twip = 1/20 pt = 1/1440 inch；1 px = 914400 EMU = 127/20 twips。
     */
    private int computeContentWidthPx(XWPFDocument doc) {
        CTSectPr sectPr = doc.getDocument().getBody().getSectPr();
        if (sectPr == null) return SimpleDocTemplateConstant.CONTENT_WIDTH_PX_FALLBACK;
        CTPageSz pgSz = sectPr.getPgSz();
        CTPageMar pgMar = sectPr.getPgMar();
        if (pgSz == null || pgMar == null) return SimpleDocTemplateConstant.CONTENT_WIDTH_PX_FALLBACK;

        long pageW = getSchemaLong(pgSz, "getW", 11906L);
        long leftM = getSchemaLong(pgMar, "getLeft", 1800L);
        long rightM = getSchemaLong(pgMar, "getRight", 1800L);
        long contentTwips = pageW - leftM - rightM;
        if (contentTwips <= 0) return SimpleDocTemplateConstant.CONTENT_WIDTH_PX_FALLBACK;
        // 1 px = 127/20 twips
        return (int) Math.round(contentTwips * 20.0 / 127.0);
    }

    // ===== 工具方法 =====

    private long getSchemaLong(Object schemaObject, String methodName, long fallback) {
        Object result = invokeSchemaMethod(schemaObject, methodName);
        return result != null ? toLong(result, fallback) : fallback;
    }

    private Object invokeSchemaMethod(Object schemaObject, String methodName) {
        if (schemaObject == null) return null;
        try {
            return schemaObject.getClass().getMethod(methodName).invoke(schemaObject);
        } catch (Exception e) {
            log.debug("读取 OOXML {} 失败: {}", methodName, e.getMessage());
            return null;
        }
    }

    private boolean hasUnderline(Object rPr) {
        return hasRunProperty(rPr, "getU", "sizeOfUArray", "getUArray", true);
    }

    private boolean hasStrike(Object rPr) {
        return hasRunProperty(rPr, "getStrike", "sizeOfStrikeArray", "getStrikeArray", false);
    }

    private boolean hasRunProperty(Object rPr, String singleMethod, String arraySizeMethod,
                                   String arrayValueMethod, boolean checkNoneValue) {
        Object property = getRunProperty(rPr, singleMethod, arraySizeMethod, arrayValueMethod);
        try {
            return property != null && (!checkNoneValue || isEnabledRunProperty(property));
        } catch (Exception e) {
            log.debug("读取 OOXML run 属性失败: {}", e.getMessage());
            return false;
        }
    }

    private Object getRunProperty(Object rPr, String singleMethod, String arraySizeMethod,
                                  String arrayValueMethod) {
        try {
            return rPr.getClass().getMethod(singleMethod).invoke(rPr);
        } catch (NoSuchMethodException ignored) {
            try {
                Method sizeOfProperty = rPr.getClass().getMethod(arraySizeMethod);
                if (((Integer) sizeOfProperty.invoke(rPr)) <= 0) return null;
                return rPr.getClass().getMethod(arrayValueMethod, int.class).invoke(rPr, 0);
            } catch (Exception e) {
                log.debug("读取 OOXML run 属性失败: {}", e.getMessage());
                return null;
            }
        } catch (Exception e) {
            log.debug("读取 OOXML run 属性失败: {}", e.getMessage());
            return null;
        }
    }

    private boolean isEnabledRunProperty(Object property) throws Exception {
        Method getValue = property.getClass().getMethod("getVal");
        Object value = getValue.invoke(property);
        return value == null || !"none".equalsIgnoreCase(value.toString());
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
