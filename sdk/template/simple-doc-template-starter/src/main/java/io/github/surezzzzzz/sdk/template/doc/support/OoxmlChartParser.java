package io.github.surezzzzzz.sdk.template.doc.support;

import io.github.surezzzzzz.sdk.template.doc.model.Chart;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OOXML chart{N}.xml → {@link Chart} 模型解析器
 * <p>
 * 用于"直接转换"链路（PdfOutputHandler.convertToPdf）：模板里已经是 OOXML chart，
 * 没有 Chart 业务对象，需要从 chart.xml 反解出 Chart 让 JFreeChart 渲染。
 * <p>
 * 仅识别 PIE / BAR / LINE 三类（与 Chart.ChartType 一致）；其它类型一律按 BAR 兜底。
 *
 * @author surezzzzzz
 */
@Slf4j
public class OoxmlChartParser {

    private static final int DEFAULT_TITLE_FONT_SIZE = 14;

    /**
     * 从标题 a:t 取首个文本（忽略多 run 的高级排版）
     */
    private static final Pattern TITLE_TEXT_PATTERN = Pattern.compile(
            "<c:title\\b[\\s\\S]*?<a:t>([\\s\\S]*?)</a:t>", Pattern.DOTALL);

    /**
     * 系列块 <c:ser>...</c:ser>
     */
    private static final Pattern SERIES_BLOCK_PATTERN = Pattern.compile(
            "<c:ser\\b[\\s\\S]*?</c:ser>", Pattern.DOTALL);

    /**
     * 系列名 <c:tx>...<c:v>NAME</c:v>...</c:tx>
     */
    private static final Pattern SERIES_NAME_PATTERN = Pattern.compile(
            "<c:tx>[\\s\\S]*?<c:v>([\\s\\S]*?)</c:v>[\\s\\S]*?</c:tx>", Pattern.DOTALL);

    /**
     * 系列颜色 <c:spPr>...<a:srgbClr val="HEX"/>...</c:spPr>
     */
    private static final Pattern SERIES_COLOR_PATTERN = Pattern.compile(
            "<c:spPr>[\\s\\S]*?<a:srgbClr val=\"([0-9A-Fa-f]{6})\"[\\s\\S]*?</c:spPr>", Pattern.DOTALL);

    /**
     * 类别块 <c:cat>...</c:cat> 内的 <c:pt idx="N"><c:v>STR</c:v></c:pt>
     */
    private static final Pattern CAT_BLOCK_PATTERN = Pattern.compile(
            "<c:cat>[\\s\\S]*?</c:cat>", Pattern.DOTALL);

    /**
     * 数值块 <c:val>...</c:val> 内的 <c:pt idx="N"><c:v>NUM</c:v></c:pt>
     */
    private static final Pattern VAL_BLOCK_PATTERN = Pattern.compile(
            "<c:val>[\\s\\S]*?</c:val>", Pattern.DOTALL);

    /**
     * 通用 pt 抽取：&lt;c:pt idx="..."&gt;&lt;c:v&gt;VALUE&lt;/c:v&gt;&lt;/c:pt&gt;
     */
    private static final Pattern PT_PATTERN = Pattern.compile(
            "<c:pt[^>]*>\\s*<c:v>([\\s\\S]*?)</c:v>\\s*</c:pt>", Pattern.DOTALL);

    private static final Pattern LEGEND_POS_PATTERN = Pattern.compile(
            "<c:legend>[\\s\\S]*?<c:legendPos val=\"([^\"]+)\"", Pattern.DOTALL);

    /**
     * lineChart 内的 c:smooth val="0|1"。OOXML 默认 smooth=0，但若 chart.xml 显式写 1 才走平滑。
     * 直接用 lineChart 块匹配，避免和 series 内的 smooth 混淆（series.smooth 也可单独覆盖，本期暂不区分）。
     */
    private static final Pattern LINE_SMOOTH_PATTERN = Pattern.compile(
            "<c:lineChart[\\s\\S]*?<c:smooth\\s+val=\"([01])\"", Pattern.DOTALL);

    /**
     * lineChart 内的 c:marker val="0|1"。默认无 marker。
     */
    private static final Pattern LINE_MARKER_PATTERN = Pattern.compile(
            "<c:lineChart[\\s\\S]*?<c:marker\\s+val=\"([01])\"", Pattern.DOTALL);

    /**
     * 标题字号 + 粗体：&lt;c:title&gt;...&lt;a:defRPr sz="1400" b="1"&gt; 或 &lt;a:rPr sz="14" b="1"&gt;
     * OOXML 字号单位是 1/100 pt（sz="1400" = 14pt）；少数模板写 sz="14"（直接 pt），两种都兼容。
     */
    private static final Pattern TITLE_DEFRPR_PATTERN = Pattern.compile(
            "<c:title\\b[\\s\\S]*?<a:(?:defRPr|rPr)\\b([^/>]*)/?>", Pattern.DOTALL);

    /**
     * 柱状图分组间距：&lt;c:gapWidth val="150"/&gt;
     */
    private static final Pattern BAR_GAP_WIDTH_PATTERN = Pattern.compile(
            "<c:barChart[\\s\\S]*?<c:gapWidth\\s+val=\"(-?\\d+)\"", Pattern.DOTALL);

    /**
     * 值轴块 &lt;c:valAx&gt;...&lt;/c:valAx&gt;
     */
    private static final Pattern VAL_AX_BLOCK_PATTERN = Pattern.compile(
            "<c:valAx\\b[\\s\\S]*?</c:valAx>", Pattern.DOTALL);

    /**
     * c:scaling 中的 c:min val
     */
    private static final Pattern SCALING_MIN_PATTERN = Pattern.compile(
            "<c:scaling>[\\s\\S]*?<c:min\\s+val=\"([-\\d.eE+]+)\"", Pattern.DOTALL);

    /**
     * c:scaling 中的 c:max val
     */
    private static final Pattern SCALING_MAX_PATTERN = Pattern.compile(
            "<c:scaling>[\\s\\S]*?<c:max\\s+val=\"([-\\d.eE+]+)\"", Pattern.DOTALL);

    /**
     * c:majorUnit val
     */
    private static final Pattern MAJOR_UNIT_PATTERN = Pattern.compile(
            "<c:majorUnit\\s+val=\"([-\\d.eE+]+)\"", Pattern.DOTALL);

    /**
     * c:numFmt formatCode（valAx 内）
     */
    private static final Pattern NUM_FMT_PATTERN = Pattern.compile(
            "<c:numFmt\\s+[^>]*formatCode=\"([^\"]+)\"", Pattern.DOTALL);

    /**
     * 数据标签：&lt;c:dLbls&gt;...&lt;c:showVal val="1"/&gt;
     * 任一处出现即认为 showVal=true
     */
    private static final Pattern SHOW_VAL_PATTERN = Pattern.compile(
            "<c:dLbls>[\\s\\S]*?<c:showVal\\s+val=\"1\"", Pattern.DOTALL);

    /**
     * 解析 chart.xml 字节为 {@link Chart} 模型；解析失败返回 null（调用方自行兜底）
     */
    public Chart parse(byte[] chartXmlBytes) {
        if (chartXmlBytes == null || chartXmlBytes.length == 0) {
            return null;
        }
        String xml = new String(chartXmlBytes, java.nio.charset.StandardCharsets.UTF_8);

        try {
            Chart.ChartType type = detectChartType(xml);
            String title = extractTitle(xml);
            Chart.LegendPosition legendPosition = extractLegendPosition(xml);

            // 类别 & 系列：取所有 <c:ser>
            List<String> categories = new ArrayList<>();
            List<Chart.Series> series = new ArrayList<>();

            Matcher serMatcher = SERIES_BLOCK_PATTERN.matcher(xml);
            boolean catFilled = false;
            while (serMatcher.find()) {
                String serBlock = serMatcher.group();
                String name = firstGroup(SERIES_NAME_PATTERN, serBlock, "");
                String color = firstGroup(SERIES_COLOR_PATTERN, serBlock, null);

                // 第一个系列里把 categories 取出来
                if (!catFilled) {
                    Matcher catBlockM = CAT_BLOCK_PATTERN.matcher(serBlock);
                    if (catBlockM.find()) {
                        Matcher ptM = PT_PATTERN.matcher(catBlockM.group());
                        while (ptM.find()) {
                            categories.add(unescapeXml(ptM.group(1).trim()));
                        }
                        catFilled = !categories.isEmpty();
                    }
                }

                // 取数值
                List<Number> values = new ArrayList<>();
                Matcher valBlockM = VAL_BLOCK_PATTERN.matcher(serBlock);
                if (valBlockM.find()) {
                    Matcher ptM = PT_PATTERN.matcher(valBlockM.group());
                    while (ptM.find()) {
                        String raw = ptM.group(1).trim();
                        try {
                            values.add(Double.parseDouble(raw));
                        } catch (NumberFormatException nfe) {
                            values.add(0d);
                        }
                    }
                }

                series.add(new Chart.Series(unescapeXml(name), values, color));
            }

            if (series.isEmpty() || categories.isEmpty()) {
                log.debug("chart.xml 无可用 series/categories，跳过解析");
                return null;
            }

            // width/height 走 Chart 默认值（直接转换链路里没有 wp:extent 的有效值，让渲染器兜底）
            // smooth/marker 来自 chart.xml 实际值；只对 LINE 类型有意义，其它类型忽略
            boolean smooth = type == Chart.ChartType.LINE && parseBoolFlag(LINE_SMOOTH_PATTERN, xml, false);
            boolean showMarker = type == Chart.ChartType.LINE && parseBoolFlag(LINE_MARKER_PATTERN, xml, false);

            // 标题字号 + 粗体（OOXML sz 是 1/100 pt，>=100 才认为是 hundredth；否则按 pt）
            int titleFontSize = DEFAULT_TITLE_FONT_SIZE;
            boolean titleBold = true;
            String titleAttrs = firstGroup(TITLE_DEFRPR_PATTERN, xml, null);
            if (titleAttrs != null) {
                Matcher szM = Pattern.compile("sz=\"(\\d+)\"").matcher(titleAttrs);
                if (szM.find()) {
                    int raw = Integer.parseInt(szM.group(1));
                    titleFontSize = raw >= 100 ? raw / 100 : raw;
                }
                Matcher bM = Pattern.compile("\\bb=\"([01])\"").matcher(titleAttrs);
                titleBold = !bM.find() || "1".equals(bM.group(1));
            }

            // 柱状图 gapWidth
            Integer barGapWidth = null;
            if (type == Chart.ChartType.BAR) {
                String gw = firstGroup(BAR_GAP_WIDTH_PATTERN, xml, null);
                if (gw != null) {
                    try {
                        barGapWidth = Integer.parseInt(gw);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

            // 值轴：min/max/majorUnit/numFmt（饼图无值轴）
            Double yMin = null;
            Double yMax = null;
            Double yMajorUnit = null;
            String yNumFmt = null;
            if (type != Chart.ChartType.PIE) {
                Matcher valAxM = VAL_AX_BLOCK_PATTERN.matcher(xml);
                if (valAxM.find()) {
                    String valAxBlock = valAxM.group();
                    yMin = parseDouble(firstGroup(SCALING_MIN_PATTERN, valAxBlock, null));
                    yMax = parseDouble(firstGroup(SCALING_MAX_PATTERN, valAxBlock, null));
                    yMajorUnit = parseDouble(firstGroup(MAJOR_UNIT_PATTERN, valAxBlock, null));
                    String fmt = firstGroup(NUM_FMT_PATTERN, valAxBlock, null);
                    // "General" 等同未设
                    if (fmt != null && !"General".equalsIgnoreCase(fmt)) {
                        yNumFmt = fmt;
                    }
                }
            }

            boolean showVal = SHOW_VAL_PATTERN.matcher(xml).find();
            boolean bar3D = xml.contains("<c:bar3DChart");

            return new Chart(
                    title == null ? "" : title,
                    categories,
                    series,
                    0, 0,
                    type,
                    titleFontSize,
                    legendPosition,
                    smooth, showMarker, false,
                    titleBold, barGapWidth,
                    yMin, yMax, yMajorUnit, yNumFmt,
                    showVal, bar3D);
        } catch (Exception e) {
            log.warn("解析 chart.xml 失败，跳过该 chart: {}", e.getMessage());
            return null;
        }
    }

    private Chart.ChartType detectChartType(String xml) {
        if (xml.contains("<c:pieChart")) {
            return Chart.ChartType.PIE;
        }
        if (xml.contains("<c:barChart") || xml.contains("<c:bar3DChart")) {
            return Chart.ChartType.BAR;
        }
        if (xml.contains("<c:lineChart")) {
            return Chart.ChartType.LINE;
        }
        // 其它（area / scatter / radar / doughnut...）一律按 BAR 兜底，至少能出图
        return Chart.ChartType.BAR;
    }

    private String extractTitle(String xml) {
        return unescapeXml(firstGroup(TITLE_TEXT_PATTERN, xml, ""));
    }

    private Chart.LegendPosition extractLegendPosition(String xml) {
        String code = firstGroup(LEGEND_POS_PATTERN, xml, null);
        if (code == null) return Chart.LegendPosition.BOTTOM;
        Chart.LegendPosition lp = Chart.LegendPosition.fromCode(code);
        return lp != null ? lp : Chart.LegendPosition.BOTTOM;
    }

    private String firstGroup(Pattern pattern, String input, String defaultValue) {
        Matcher m = pattern.matcher(input);
        if (m.find()) {
            return m.group(1);
        }
        return defaultValue;
    }

    private boolean parseBoolFlag(Pattern pattern, String input, boolean defaultValue) {
        String v = firstGroup(pattern, input, null);
        if (v == null) return defaultValue;
        return "1".equals(v) || "true".equalsIgnoreCase(v);
    }

    private Double parseDouble(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String unescapeXml(String s) {
        if (s == null) return null;
        return s.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&amp;", "&");
    }
}
