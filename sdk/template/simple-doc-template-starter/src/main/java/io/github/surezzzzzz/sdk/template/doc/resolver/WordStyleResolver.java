package io.github.surezzzzzz.sdk.template.doc.resolver;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;

import javax.xml.namespace.QName;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Word 段落/run 样式继承解析器。
 * <p>
 * OOXML 段落/run 的最终样式由四级合并而来（按覆盖优先级从低到高）：
 * <pre>
 *   docDefaults (rPrDefault / pPrDefault)
 *     ↓
 *   pStyle 链 (basedOn 反向遍历，最远祖先 → 最近后代)
 *     ↓
 *   段落自身 pPr
 *     ↓
 *   rStyle 链
 *     ↓
 *   run 自身 rPr
 * </pre>
 * <p>
 * 实现策略：不构造合并后的 CTPPr/CTRPr（xmlbeans schema 复杂、易踩坑），
 * 而是只提供 emitter 真正需要的属性级查询：spacing/before、ind/firstLine、
 * jc、rFonts、sz、b、color … 每个属性沿继承链向上找，命中即返回。
 *
 * @author surezzzzzz
 */
@Slf4j
public class WordStyleResolver {

    private static final String NS_W =
            "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

    /**
     * styleId → XWPFStyle
     */
    private final Map<String, XWPFStyle> stylesById = new HashMap<>();

    /**
     * docDefaults pPr / rPr（XmlObject，统一用 cursor 访问）
     */
    private final XmlObject defaultPPrXml;
    private final XmlObject defaultRPrXml;

    /**
     * docGrid type，可能为 default/lines/linesAndChars/snapToChars，未设为 null
     */
    @Getter
    private final String docGridType;

    /**
     * docGrid linePitch（twips），未设为 -1。type=lines/linesAndChars 时表示
     * 一行的"网格基准高度"（行盒高度的实际依据，而不是字号）。
     */
    @Getter
    private final long docGridLinePitchTwips;

    public WordStyleResolver(XWPFDocument doc) {
        XWPFStyles styles = doc.getStyles();
        if (styles != null) {
            try {
                List<XWPFStyle> all = collectAllStyles(styles);
                for (XWPFStyle s : all) {
                    if (s != null && s.getStyleId() != null) {
                        stylesById.put(s.getStyleId(), s);
                    }
                }
            } catch (Exception e) {
                log.debug("枚举 XWPFStyles 失败，pStyle 继承将退化: {}", e.getMessage());
            }
        }

        // docDefaults：不同 POI 版本的默认样式实现存在差异，getPPr()/getRPr() 通过反射访问。
        XmlObject pd = null;
        XmlObject rd = null;
        try {
            if (styles != null) {
                Object dp = styles.getDefaultParagraphStyle();
                if (dp != null) {
                    pd = invokeProtectedXml(dp, "getPPr");
                }
                Object dr = styles.getDefaultRunStyle();
                if (dr != null) {
                    rd = invokeProtectedXml(dr, "getRPr");
                }
            }
        } catch (Exception e) {
            log.debug("解析 docDefaults 失败: {}", e.getMessage());
        }
        this.defaultPPrXml = pd;
        this.defaultRPrXml = rd;

        // docGrid：从 body 末尾 sectPr/docGrid 读出 type 和 linePitch（twips）
        String gridType = null;
        long gridPitch = -1;
        try {
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBody body = doc.getDocument().getBody();
            if (body != null && body.isSetSectPr()) {
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr sectPr = body.getSectPr();
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTDocGrid grid = sectPr.getDocGrid();
                if (grid != null) {
                    if (grid.isSetType()) gridType = grid.getType().toString();
                    if (grid.isSetLinePitch()) {
                        Object lp = grid.getLinePitch();
                        if (lp != null) {
                            try {
                                gridPitch = new java.math.BigInteger(lp.toString()).longValue();
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("读取 docGrid 失败: {}", e.getMessage());
        }
        this.docGridType = gridType;
        this.docGridLinePitchTwips = gridPitch;
    }

    private XmlObject invokeProtectedXml(Object target, String methodName) {
        try {
            java.lang.reflect.Method m = target.getClass().getDeclaredMethod(methodName);
            m.setAccessible(true);
            Object res = m.invoke(target);
            return res instanceof XmlObject ? (XmlObject) res : null;
        } catch (Exception e) {
            log.debug("反射 {}.{} 失败: {}", target.getClass().getSimpleName(), methodName, e.getMessage());
            return null;
        }
    }

    /**
     * 枚举所有 styles。不同 POI 版本的 {@link XWPFStyles} 没有统一的公开列表入口，
     * 优先读取内部 {@code listStyle}（List&lt;XWPFStyle&gt;）私有字段。
     * 兜底再走 {@code CTStyles.getStyleList()} → {@code styles.getStyle(id)}。
     */
    private List<XWPFStyle> collectAllStyles(XWPFStyles styles) {
        List<XWPFStyle> out = new java.util.ArrayList<>();
        // 路径 1：直接拿私有字段 listStyle
        try {
            java.lang.reflect.Field f = XWPFStyles.class.getDeclaredField("listStyle");
            f.setAccessible(true);
            Object v = f.get(styles);
            if (v instanceof Iterable) {
                for (Object o : (Iterable<?>) v) {
                    if (o instanceof XWPFStyle) out.add((XWPFStyle) o);
                }
                if (!out.isEmpty()) return out;
            }
        } catch (Exception e) {
            log.debug("反射 XWPFStyles.listStyle 失败: {}", e.getMessage());
        }
        // 路径 2：通过 CTStyles 枚举 styleId 再 styles.getStyle(id)
        try {
            java.lang.reflect.Field f = XWPFStyles.class.getDeclaredField("ctStyles");
            f.setAccessible(true);
            Object ct = f.get(styles);
            if (ct != null) {
                java.lang.reflect.Method m = ct.getClass().getMethod("getStyleList");
                Object list = m.invoke(ct);
                if (list instanceof Iterable) {
                    for (Object o : (Iterable<?>) list) {
                        if (o == null) continue;
                        try {
                            java.lang.reflect.Method gid = o.getClass().getMethod("getStyleId");
                            Object sidObj = gid.invoke(o);
                            String sid = sidObj != null ? sidObj.toString() : null;
                            if (sid != null) {
                                XWPFStyle s = styles.getStyle(sid);
                                if (s != null) out.add(s);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("反射 XWPFStyles.ctStyles 失败: {}", e.getMessage());
        }
        return out;
    }

    // ===== 继承链 =====

    /**
     * 收集 styleId 的 basedOn 链，返回顺序「最远祖先 → 当前 styleId」
     */
    private List<XWPFStyle> styleChain(String styleId) {
        LinkedList<XWPFStyle> chain = new LinkedList<>();
        String cur = styleId;
        int safety = 32;
        java.util.Set<String> seen = new java.util.HashSet<>();
        while (cur != null && safety-- > 0) {
            if (!seen.add(cur)) break;
            XWPFStyle s = stylesById.get(cur);
            if (s == null) break;
            chain.addFirst(s);
            try {
                if (s.getCTStyle() != null && s.getCTStyle().getBasedOn() != null) {
                    cur = s.getCTStyle().getBasedOn().getVal();
                } else {
                    cur = null;
                }
            } catch (Exception e) {
                cur = null;
            }
        }
        return chain;
    }

    /**
     * 段落 pPr 查询的源序列：低优先级 → 高优先级。emitter 反向遍历找属性值。
     */
    private List<XmlObject> paragraphPPrSources(XWPFParagraph para) {
        List<XmlObject> out = new java.util.ArrayList<>();
        if (defaultPPrXml != null) out.add(defaultPPrXml);
        String pStyleId = para.getStyle();
        if (pStyleId != null) {
            for (XWPFStyle s : styleChain(pStyleId)) {
                XmlObject sp = stylePPr(s);
                if (sp != null) out.add(sp);
            }
        }
        CTPPr selfPPr = para.getCTP().getPPr();
        if (selfPPr != null) out.add(selfPPr);
        return out;
    }

    /**
     * 段落级 rPr 查询源序列（不含 run 自身）：
     * docDefaults rPr → pStyle 链 rPr → 段落 mark rPr。
     * <p>
     * 用于「段落默认 run 样式」查询：段落里没在 run rPr 里覆盖 sz/font/color/b 的字符，
     * 应当继承段落级默认值。emitter 用这套值在 &lt;p&gt; 上 emit 一份兜底 CSS，
     * 让裸文本和未带 sz 的 run 都能拿到正确字号/字体。
     */
    private List<XmlObject> paragraphRPrSources(XWPFParagraph para) {
        List<XmlObject> out = new java.util.ArrayList<>();
        if (defaultRPrXml != null) out.add(defaultRPrXml);
        String pStyleId = para != null ? para.getStyle() : null;
        if (pStyleId != null) {
            for (XWPFStyle s : styleChain(pStyleId)) {
                CTRPr sr = styleRPr(s);
                if (sr != null) out.add(sr);
            }
        }
        if (para != null) {
            CTPPr selfPPr = para.getCTP().getPPr();
            if (selfPPr != null && selfPPr.getRPr() != null) {
                out.add(selfPPr.getRPr());
            }
        }
        return out;
    }

    /**
     * run rPr 查询的源序列。run 自身 rPr 最高，docDefaults rPr 最低。
     */
    private List<XmlObject> runRPrSources(XWPFRun run, XWPFParagraph para) {
        List<XmlObject> out = paragraphRPrSources(para);
        CTRPr selfRPr = run != null ? run.getCTR().getRPr() : null;
        if (selfRPr != null) {
            // rStyle 子元素：用 cursor 读 @val（CTRPr 没有 public getRStyle）
            String rStyleId = readChildAttr(selfRPr, "rStyle", "val");
            if (rStyleId != null) {
                for (XWPFStyle s : styleChain(rStyleId)) {
                    CTRPr sr = styleRPr(s);
                    if (sr != null) out.add(sr);
                }
            }
            out.add(selfRPr);
        }
        return out;
    }

    private XmlObject stylePPr(XWPFStyle s) {
        return invokeSchemaXml(s != null ? s.getCTStyle() : null, "getPPr");
    }

    private CTRPr styleRPr(XWPFStyle s) {
        XmlObject result = invokeSchemaXml(s != null ? s.getCTStyle() : null, "getRPr");
        return result instanceof CTRPr ? (CTRPr) result : null;
    }

    private XmlObject invokeSchemaXml(Object target, String methodName) {
        if (target == null) return null;
        try {
            java.lang.reflect.Method method = target.getClass().getMethod(methodName);
            Object result = method.invoke(target);
            return result instanceof XmlObject ? (XmlObject) result : null;
        } catch (Exception e) {
            log.debug("反射 {}.{} 失败: {}", target.getClass().getSimpleName(), methodName, e.getMessage());
            return null;
        }
    }

    // ===== 段落属性 resolve（从高优先级源向低优先级源找首个命中）=====

    /**
     * 段落对齐（jc）；命中返回 STJc 字符串值（如 "center"/"left"/"both"），
     * 未命中返回 null。
     */
    public String resolveJc(XWPFParagraph para) {
        List<XmlObject> sources = paragraphPPrSources(para);
        for (int i = sources.size() - 1; i >= 0; i--) {
            String v = readChildAttr(sources.get(i), "jc", "val");
            if (v != null) return v;
        }
        return null;
    }

    /**
     * 段落首行缩进（twips），未设返回 -1
     */
    public long resolveIndFirstLine(XWPFParagraph para) {
        List<XmlObject> sources = paragraphPPrSources(para);
        for (int i = sources.size() - 1; i >= 0; i--) {
            String v = readChildAttr(sources.get(i), "ind", "firstLine");
            if (v != null) {
                try {
                    return Long.parseLong(v);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return -1;
    }

    /**
     * 段落左缩进（twips），未设返回 -1
     */
    public long resolveIndLeft(XWPFParagraph para) {
        List<XmlObject> sources = paragraphPPrSources(para);
        for (int i = sources.size() - 1; i >= 0; i--) {
            String v = readChildAttr(sources.get(i), "ind", "left");
            if (v != null) {
                try {
                    return Long.parseLong(v);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return -1;
    }

    /**
     * 段落 spacing/before（twips），未设返回 -1
     */
    public long resolveSpacingBefore(XWPFParagraph para) {
        // 先查 beforeAutospacing，如果开了就忽略 before（Word 自己算）
        if (resolveAutospacing(para, "beforeAutospacing")) return -1;
        return resolveSpacingAttrTwips(para, "before");
    }

    /**
     * 段落 spacing/after（twips），未设或 afterAutospacing 启用时返回 -1
     */
    public long resolveSpacingAfter(XWPFParagraph para) {
        if (resolveAutospacing(para, "afterAutospacing")) return -1;
        return resolveSpacingAttrTwips(para, "after");
    }

    /**
     * spacing/line（int，单位由 lineRule 决定）；未设返回 -1
     */
    public long resolveSpacingLine(XWPFParagraph para) {
        return resolveSpacingAttrTwips(para, "line");
    }

    /**
     * spacing/lineRule，返回 "auto"/"exact"/"atLeast" 或 null
     */
    public String resolveSpacingLineRule(XWPFParagraph para) {
        List<XmlObject> sources = paragraphPPrSources(para);
        for (int i = sources.size() - 1; i >= 0; i--) {
            String v = readChildAttr(sources.get(i), "spacing", "lineRule");
            if (v != null) return v.toLowerCase();
        }
        return null;
    }

    private long resolveSpacingAttrTwips(XWPFParagraph para, String attrName) {
        List<XmlObject> sources = paragraphPPrSources(para);
        for (int i = sources.size() - 1; i >= 0; i--) {
            String v = readChildAttr(sources.get(i), "spacing", attrName);
            if (v != null) {
                try {
                    return Long.parseLong(v);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return -1;
    }

    private boolean resolveAutospacing(XWPFParagraph para, String attrName) {
        List<XmlObject> sources = paragraphPPrSources(para);
        for (int i = sources.size() - 1; i >= 0; i--) {
            String v = readChildAttr(sources.get(i), "spacing", attrName);
            if (v != null) {
                String t = v.toLowerCase();
                return "1".equals(t) || "true".equals(t) || "on".equals(t);
            }
        }
        return false;
    }

    // ===== docGrid 信息（section 级常量）=====

    /**
     * docGrid 是否启用 type=lines / linesAndChars（即「按行网格定行高」语义）
     */
    public boolean isLineGridActive() {
        return docGridLinePitchTwips > 0
                && ("lines".equalsIgnoreCase(docGridType)
                || "linesAndChars".equalsIgnoreCase(docGridType));
    }

    // ===== docDefaults 直读（body 级 fallback 用）=====

    /**
     * 读 docDefaults 的 rPrDefault/sz @val（half-point），未设返回 -1。
     * 用于 body { font-size } 兜底，避免硬编码 12pt。
     */
    public long resolveDocDefaultSzHalfPt() {
        if (defaultRPrXml == null) return -1;
        String v = readChildAttr(defaultRPrXml, "sz", "val");
        if (v == null) return -1;
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    // ===== run 属性 resolve =====

    /**
     * run 字号（half-point），未设返回 -1。例如 sz=24 → 12pt。
     */
    public long resolveRunSzHalfPt(XWPFRun run, XWPFParagraph para) {
        List<XmlObject> sources = runRPrSources(run, para);
        for (int i = sources.size() - 1; i >= 0; i--) {
            String v = readChildAttr(sources.get(i), "sz", "val");
            if (v != null) {
                try {
                    return Long.parseLong(v);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return -1;
    }

    /**
     * run 是否加粗
     */
    public boolean resolveRunBold(XWPFRun run, XWPFParagraph para) {
        return resolveRunOnOff(run, para, "b");
    }

    /**
     * run 是否斜体
     */
    public boolean resolveRunItalic(XWPFRun run, XWPFParagraph para) {
        return resolveRunOnOff(run, para, "i");
    }

    private boolean resolveRunOnOff(XWPFRun run, XWPFParagraph para, String elemName) {
        List<XmlObject> sources = runRPrSources(run, para);
        for (int i = sources.size() - 1; i >= 0; i--) {
            String v = readChildAttrOrEmpty(sources.get(i), elemName, "val");
            if (v == null) continue;
            // 元素存在但 @val 缺省 → on（OOXML 约定）
            if (v.isEmpty()) return true;
            String t = v.toLowerCase();
            return "1".equals(t) || "true".equals(t) || "on".equals(t);
        }
        return false;
    }

    /**
     * run 颜色 hex（不带 #），未设/auto 返回 null
     */
    public String resolveRunColor(XWPFRun run, XWPFParagraph para) {
        List<XmlObject> sources = runRPrSources(run, para);
        for (int i = sources.size() - 1; i >= 0; i--) {
            String v = readChildAttr(sources.get(i), "color", "val");
            if (v != null) {
                if ("auto".equalsIgnoreCase(v)) return null;
                return v;
            }
        }
        return null;
    }

    /**
     * run 字体（按 ascii / eastAsia / hAnsi 优先级取第一个非空），
     * hint=eastAsia 时优先 eastAsia。未设返回 null。
     * <p>
     * OOXML rFonts 语义：ascii/eastAsia/hAnsi/hint 四个属性各自沿继承链独立覆盖。
     * 例如 run rPr 设 ascii=宋体 但没设 eastAsia，pStyle 链上 eastAsia=仿宋 仍生效。
     * 因此这里按属性维度合并：每个属性取最高优先级的非空值，再按 hint 选用。
     */
    public String resolveRunFontFamily(XWPFRun run, XWPFParagraph para) {
        List<XmlObject> sources = runRPrSources(run, para);
        return resolveFontFromSources(sources);
    }

    // ===== 段落默认 run 属性 resolve（仅 docDefaults → pStyle 链 → paraMark.rPr）=====
    //
    // 这些方法用于 emitter 在 <p> 上 emit 一份「段落里裸文本/未带样式 run 的兜底样式」。
    // CSS 继承让 <span> 自身覆盖 <p>，<p> 又覆盖 body，所以即便 run 自己没写 sz，
    // 也能从段落级继承到 pStyle 链上的 sz=N，避免落到 body 12pt 默认值。

    /**
     * 段落默认字号（half-point），未在段落级（paraMark.rPr / pStyle 链 / docDefaults）
     * 找到 sz 时返回 -1。
     */
    public long resolveParaDefaultSzHalfPt(XWPFParagraph para) {
        List<XmlObject> sources = paragraphRPrSources(para);
        for (int i = sources.size() - 1; i >= 0; i--) {
            String v = readChildAttr(sources.get(i), "sz", "val");
            if (v != null) {
                try {
                    return Long.parseLong(v);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return -1;
    }

    /**
     * 段内最大 run 字号（half-pt），用于行高估算（docGrid baseline-snap）。
     * 取所有 run 的解析字号最大值；若全部未设，回退到段落默认；再无则返回 -1。
     */
    public long resolveMaxRunSzHalfPt(XWPFParagraph para) {
        long max = -1;
        if (para != null) {
            for (XWPFRun r : para.getRuns()) {
                long sz = resolveRunSzHalfPt(r, para);
                if (sz > max) max = sz;
            }
        }
        if (max <= 0) max = resolveParaDefaultSzHalfPt(para);
        return max;
    }

    /**
     * 段落默认字体（按 hint/ascii/eastAsia/hAnsi 同 run 级规则，属性维度合并），未设返回 null
     */
    public String resolveParaDefaultFontFamily(XWPFParagraph para) {
        List<XmlObject> sources = paragraphRPrSources(para);
        return resolveFontFromSources(sources);
    }

    /**
     * 段落默认颜色 hex（不带 #），未设/auto 返回 null
     */
    public String resolveParaDefaultColor(XWPFParagraph para) {
        List<XmlObject> sources = paragraphRPrSources(para);
        for (int i = sources.size() - 1; i >= 0; i--) {
            String v = readChildAttr(sources.get(i), "color", "val");
            if (v != null) {
                if ("auto".equalsIgnoreCase(v)) return null;
                return v;
            }
        }
        return null;
    }

    /**
     * 段落默认是否加粗（仅段落级 sources，OOXML "元素存在即开"语义同 run 级）
     */
    /**
     * 表格单元格默认字号（half-pt），从 Normal Table 样式链解析。
     * 返回 -1 表示未设置，由调用方决定兜底值。
     * <p>
     * 优先找 styleId="Normal" 的表格样式；若无，则找根表格样式（无 basedOn）。
     * 沿 basedOn 链向上找首个有 sz 的样式。
     */
    public long resolveTableCellSzHalfPt() {
        // 优先 Normal Table
        XWPFStyle normalTable = stylesById.get("Normal");
        if (normalTable == null) {
            // 找根 table 样式（type=table 且无 basedOn）
            for (XWPFStyle s : stylesById.values()) {
                if (!"TABLE".equalsIgnoreCase(s.getType().toString())) continue;
                String based = getBasedOnStyleId(s);
                if (based == null || based.isEmpty() || !stylesById.containsKey(based)) {
                    normalTable = s;
                    break;
                }
            }
        }
        if (normalTable == null) return -1;
        // 沿 basedOn 链找 sz（用 readChildAttr 读 w:sz/@w:val，与 resolveRunSzHalfPt 同一路径）
        XWPFStyle cur = normalTable;
        int safety = 16;
        while (cur != null && safety > 0) {
            CTRPr rPr = styleRPr(cur);
            if (rPr != null) {
                String v = readChildAttr(rPr, "sz", "val");
                if (v != null) {
                    try {
                        return Long.parseLong(v);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            String based = getBasedOnStyleId(cur);
            cur = (based != null && !based.isEmpty()) ? stylesById.get(based) : null;
            safety--;
        }
        return -1;
    }

    private static String getBasedOnStyleId(XWPFStyle s) {
        try {
            if (s.getCTStyle() != null && s.getCTStyle().getBasedOn() != null) {
                return s.getCTStyle().getBasedOn().getVal();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 段落默认 run 属性是否加粗
     */
    public boolean resolveParaDefaultBold(XWPFParagraph para) {
        List<XmlObject> sources = paragraphRPrSources(para);
        for (int i = sources.size() - 1; i >= 0; i--) {
            String v = readChildAttrOrEmpty(sources.get(i), "b", "val");
            if (v == null) continue;
            if (v.isEmpty()) return true;
            String t = v.toLowerCase();
            return "1".equals(t) || "true".equals(t) || "on".equals(t);
        }
        return false;
    }

    // ===== XmlCursor 工具 =====

    /**
     * 在多个 rPr 源（已按低→高优先级排序）上沿属性维度合并 rFonts，
     * 返回最终生效的字体名（按 hint 决定 ascii/eastAsia 优先级）。
     * <p>
     * OOXML 的 rFonts 四个属性 (ascii/eastAsia/hAnsi/hint) 各自独立沿
     * 继承链覆盖：高优先级源未声明的属性，应当从低优先级源透传过来。
     */
    private String resolveFontFromSources(List<XmlObject> sources) {
        String hint = null;
        String ascii = null;
        String eastAsia = null;
        String hAnsi = null;
        for (XmlObject src : sources) {
            String h = readChildAttr(src, "rFonts", "hint");
            String a = readChildAttr(src, "rFonts", "ascii");
            String ea = readChildAttr(src, "rFonts", "eastAsia");
            String ha = readChildAttr(src, "rFonts", "hAnsi");
            if (h != null && !h.isEmpty()) hint = h;
            if (a != null && !a.isEmpty()) ascii = a;
            if (ea != null && !ea.isEmpty()) eastAsia = ea;
            if (ha != null && !ha.isEmpty()) hAnsi = ha;
        }
        if (ascii == null && eastAsia == null && hAnsi == null) return null;
        if ("eastAsia".equalsIgnoreCase(hint)) {
            if (eastAsia != null) return eastAsia;
            if (ascii != null) return ascii;
            return hAnsi;
        }
        if (ascii != null) return ascii;
        if (eastAsia != null) return eastAsia;
        return hAnsi;
    }

    /**
     * 在 XmlObject 下查找指定 localName 的子元素，返回其 attrName 属性值。
     * 找不到子元素返回 null；找到子元素但属性缺省，返回 null（如需区分见
     * {@link #readChildAttrOrEmpty(XmlObject, String, String)}）。
     */
    private String readChildAttr(XmlObject parent, String childLocalName, String attrLocalName) {
        if (parent == null) return null;
        try (CursorWrap cw = new CursorWrap(parent.newCursor())) {
            XmlCursor c = cw.cursor;
            if (!c.toFirstChild()) return null;
            do {
                if (NS_W.equals(c.getName().getNamespaceURI())
                        && childLocalName.equals(c.getName().getLocalPart())) {
                    String v = c.getAttributeText(new QName(NS_W, attrLocalName));
                    return v;  // null when attribute absent
                }
            } while (c.toNextSibling());
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 同 readChildAttr，但找到了子元素但 @val 缺省时返回 ""（用于 OOXML
     * b/i/u 等"元素存在即表示开启"的语义）
     */
    private String readChildAttrOrEmpty(XmlObject parent, String childLocalName, String attrLocalName) {
        if (parent == null) return null;
        try (CursorWrap cw = new CursorWrap(parent.newCursor())) {
            XmlCursor c = cw.cursor;
            if (!c.toFirstChild()) return null;
            do {
                if (NS_W.equals(c.getName().getNamespaceURI())
                        && childLocalName.equals(c.getName().getLocalPart())) {
                    String v = c.getAttributeText(new QName(NS_W, attrLocalName));
                    return v != null ? v : "";
                }
            } while (c.toNextSibling());
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * try-with-resources 包装 XmlCursor（XmlCursor 自带 close）
     */
    private static class CursorWrap implements AutoCloseable {
        final XmlCursor cursor;

        CursorWrap(XmlCursor cursor) {
            this.cursor = cursor;
        }

        @Override
        public void close() {
            cursor.dispose();
        }
    }
}
