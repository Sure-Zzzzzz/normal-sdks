package io.github.surezzzzzz.sdk.template.doc.renderer;

import io.github.surezzzzzz.sdk.template.doc.constant.SimpleDocTemplateConstant;
import io.github.surezzzzzz.sdk.template.doc.exception.ChartPngGenerationException;
import io.github.surezzzzzz.sdk.template.doc.exception.FontNotFoundException;
import io.github.surezzzzzz.sdk.template.doc.model.Chart;
import lombok.extern.slf4j.Slf4j;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.labels.StandardCategoryItemLabelGenerator;
import org.jfree.chart.labels.StandardPieSectionLabelGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PiePlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.chart.title.TextTitle;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;

import java.awt.Font;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * Chart → PNG 渲染工具（直接转换链路使用）
 * <p>
 * 与 WordRenderer.chartToPng 同源，但抽出来不依赖 Spring Bean，
 * 由 PdfOutputHandler 在「直接转换 DOCX」场景下调用。
 *
 * @author surezzzzzz
 */
@Slf4j
public class ChartToPngRenderer {

    private static final int DEFAULT_WIDTH_PX = SimpleDocTemplateConstant.CHART_DEFAULT_WIDTH_PX;
    private static final int DEFAULT_HEIGHT_PX = SimpleDocTemplateConstant.CHART_DEFAULT_HEIGHT_PX;

    private final Font cjkFont;

    public ChartToPngRenderer(List<String> fontPaths) {
        this.cjkFont = loadCjkFont(fontPaths);
    }

    /**
     * 渲染 Chart 为 PNG 字节
     */
    public byte[] render(Chart chart) {
        int width = chart.getWidth() > 0 ? chart.getWidth() : DEFAULT_WIDTH_PX;
        int height = chart.getHeight() > 0 ? chart.getHeight() : DEFAULT_HEIGHT_PX;
        return render(chart, width, height);
    }

    /**
     * 渲染 Chart 为 PNG 字节（指定输出像素尺寸）
     * <p>
     * 用于 OOXML chart → PNG 替换场景：从 docx 模板里 wp:extent 抽到的实际显示尺寸
     * 不一定等于 chart.width/height（这两个值由调用方/解析器决定，可能为 0）。
     */
    public byte[] render(Chart chart, int width, int height) {
        try {
            if (width <= 0) width = DEFAULT_WIDTH_PX;
            if (height <= 0) height = DEFAULT_HEIGHT_PX;

            JFreeChart jfreeChart = buildJFreeChart(chart);

            // 标题：按 chart.xml 解析的字号 + 粗体派生 cjkFont
            float titleSizePt = chart.getTitleFontSize() > 0 ? chart.getTitleFontSize() : 14f;
            int titleStyle = chart.isTitleBold() ? Font.BOLD : Font.PLAIN;
            Font titleFont = cjkFont.deriveFont(titleStyle, titleSizePt);
            jfreeChart.setTitle(new TextTitle(chart.getTitle() == null ? "" : chart.getTitle(), titleFont));

            // 图例位置 + 字体
            if (jfreeChart.getLegend() != null) {
                jfreeChart.getLegend().setItemFont(cjkFont);
                Chart.LegendPosition lp = chart.getLegendPosition();
                if (lp != null) {
                    switch (lp) {
                        case TOP:
                            jfreeChart.getLegend().setPosition(RectangleEdge.TOP);
                            break;
                        case LEFT:
                            jfreeChart.getLegend().setPosition(RectangleEdge.LEFT);
                            break;
                        case RIGHT:
                            jfreeChart.getLegend().setPosition(RectangleEdge.RIGHT);
                            break;
                        case NONE:
                            jfreeChart.removeLegend();
                            break;
                        case BOTTOM:
                        default:
                            jfreeChart.getLegend().setPosition(RectangleEdge.BOTTOM);
                            break;
                    }
                }
            }

            // Y 轴自动量程修正：JFreeChart 默认算到 220，Office 算到 250（"漂亮数字"）
            applyNiceAxisRange(jfreeChart, chart);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ChartUtils.writeChartAsPNG(bos, jfreeChart, width, height);
            return bos.toByteArray();
        } catch (FontNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw ChartPngGenerationException.failed(SimpleDocTemplateConstant.DIRECT_CONVERT_CHART_KEY, e.getMessage(), e);
        }
    }

    private JFreeChart buildJFreeChart(Chart chart) {
        Chart.ChartType type = chart.getChartType();
        if (type == Chart.ChartType.PIE) {
            return buildPie(chart);
        }
        return buildCategory(chart, type);
    }

    private JFreeChart buildPie(Chart chart) {
        DefaultPieDataset dataset = new DefaultPieDataset();
        Chart.Series s = chart.getSeries().get(0);
        for (int ci = 0; ci < chart.getCategories().size(); ci++) {
            if (ci < s.getValues().size()) {
                dataset.setValue(chart.getCategories().get(ci), s.getValues().get(ci).doubleValue());
            }
        }
        PiePlot plot = new PiePlot(dataset);
        plot.setLabelFont(cjkFont);
        plot.setLabelGenerator(new StandardPieSectionLabelGenerator("{0}={1} ({2})"));
        // 饼图颜色：用户传了 series.color 就用用户的，没传则不设（让 JFreeChart 自行决定）
        java.awt.Color pieColor = parseHexColor(s.getColor(), -1);
        for (int ci = 0; ci < chart.getCategories().size(); ci++) {
            plot.setSectionPaint(chart.getCategories().get(ci), pieColor);
        }
        return new JFreeChart(plot);
    }

    private JFreeChart buildCategory(Chart chart, Chart.ChartType type) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (int si = 0; si < chart.getSeries().size(); si++) {
            Chart.Series s = chart.getSeries().get(si);
            for (int ci = 0; ci < chart.getCategories().size(); ci++) {
                if (ci < s.getValues().size()) {
                    dataset.addValue(s.getValues().get(ci).doubleValue(),
                            s.getName(), chart.getCategories().get(ci));
                }
            }
        }
        CategoryAxis categoryAxis = new CategoryAxis();
        categoryAxis.setTickLabelFont(cjkFont);
        categoryAxis.setLabelFont(cjkFont);
        NumberAxis valueAxis = new NumberAxis();
        valueAxis.setTickLabelFont(cjkFont);
        valueAxis.setLabelFont(cjkFont);
        applyValueAxisScaling(valueAxis, chart);

        if (type == Chart.ChartType.BAR) {
            BarRenderer barRenderer = new BarRenderer();
            if (!chart.isBar3D()) {
                // c:barChart（2D）：纯平色，无渐变无阴影
                barRenderer.setBarPainter(new org.jfree.chart.renderer.category.StandardBarPainter());
                barRenderer.setShadowVisible(false);
            }
            // c:bar3DChart：保留 JFreeChart 默认渐变+阴影
            applySeriesColors(barRenderer, chart.getSeries());
            applyBarGapWidth(barRenderer, chart);
            applyShowVal(barRenderer, chart);
            CategoryPlot plot = new CategoryPlot(dataset, categoryAxis, valueAxis, barRenderer);
            plot.setOrientation(PlotOrientation.VERTICAL);
            return new JFreeChart(plot);
        }
        // LINE 或其它兜底；smooth=true 时走 Catmull-Rom 平滑曲线（OOXML c:smooth）
        LineAndShapeRenderer renderer = chart.isSmooth()
                ? new SmoothLineAndShapeRenderer(true, chart.isShowMarker())
                : new LineAndShapeRenderer(true, chart.isShowMarker());
        applySeriesColors(renderer, chart.getSeries());
        applyShowVal(renderer, chart);
        CategoryPlot plot = new CategoryPlot(dataset, categoryAxis, valueAxis, renderer);
        plot.setOrientation(PlotOrientation.VERTICAL);
        return new JFreeChart(plot);
    }

    /**
     * 应用 chart.xml 解析出的 valAx 配置：min/max/majorUnit/numFmt
     */
    private void applyValueAxisScaling(NumberAxis axis, Chart chart) {
        Double yMin = chart.getYMin();
        Double yMax = chart.getYMax();
        if (yMin != null && yMax != null && yMax > yMin) {
            axis.setRange(yMin, yMax);
        } else if (yMin != null) {
            axis.setLowerBound(yMin);
        } else if (yMax != null) {
            axis.setUpperBound(yMax);
        }
        Double major = chart.getYMajorUnit();
        if (major != null && major > 0) {
            axis.setTickUnit(new NumberTickUnit(major));
        }
        String fmt = chart.getYNumFmt();
        if (fmt != null && !fmt.isEmpty()) {
            try {
                NumberFormat nf = new DecimalFormat(toJavaPattern(fmt));
                axis.setNumberFormatOverride(nf);
            } catch (IllegalArgumentException ignored) {
                // 模板里写了 Excel 专属格式（如 #,##0;[Red]-#,##0），交给 NumberAxis 默认即可
            }
        }
    }

    /**
     * Excel formatCode → Java DecimalFormat pattern 兜底转换。
     * 简单情况直接复用（"0", "0.00", "0%", "#,##0"）；复杂格式抛给调用方接异常。
     */
    private String toJavaPattern(String excelFmt) {
        // Excel 把负数分支用分号分割，DecimalFormat 也支持，但 [Red] 这种条件标记不识别
        return excelFmt.replaceAll("\\[[^\\]]*]", "");
    }

    /**
     * BarRenderer.itemMargin 是同 category 内多 series 之间的间距比例（0..1）。
     * OOXML gapWidth 是相邻 category 之间的间距占柱宽百分比，这里取一个折中映射：
     *   margin = clamp(gap / (gap + 100), 0.0, 0.95)
     * 这样 gapWidth=150 → margin≈0.6；gapWidth=50 → margin≈0.33；与 Office 视觉接近。
     */
    private void applyBarGapWidth(BarRenderer barRenderer, Chart chart) {
        Integer gap = chart.getBarGapWidth();
        if (gap == null || gap < 0) return;
        double margin = (double) gap / (gap + 100.0);
        if (margin > 0.95) margin = 0.95;
        barRenderer.setItemMargin(margin);
    }

    /**
     * 数据标签 showVal：打开 default item labels，使用通用数字格式。
     */
    private void applyShowVal(org.jfree.chart.renderer.category.AbstractCategoryItemRenderer renderer,
                              Chart chart) {
        if (!chart.isShowVal()) return;
        renderer.setDefaultItemLabelGenerator(new StandardCategoryItemLabelGenerator());
        renderer.setDefaultItemLabelsVisible(true);
        renderer.setDefaultItemLabelFont(cjkFont);
    }

    /**
     * Y 轴自动量程修正：JFreeChart 默认量程偏紧（如 max=210 → 量程 0~220），
     * Office/WPS 会向上取整到"漂亮数字"（0~250，tick=50）。
     * 仅在 chart 未显式指定 yMax 时生效，避免覆盖业务设定值。
     */
    private static void applyNiceAxisRange(JFreeChart jfreeChart, Chart chart) {
        if (chart.getChartType() == Chart.ChartType.PIE) return;
        if (chart.getYMax() != null) return;

        CategoryPlot plot = jfreeChart.getCategoryPlot();
        NumberAxis valueAxis = (NumberAxis) plot.getRangeAxis();
        valueAxis.configure();

        double autoUpper = valueAxis.getUpperBound();
        if (autoUpper <= 0) return;

        double niceTick = niceTickUnit(autoUpper / 5.0);
        double niceUpper = Math.ceil(autoUpper / niceTick) * niceTick;

        double lower = valueAxis.getLowerBound();
        if (chart.getYMin() == null) {
            if (lower > 0) {
                lower = Math.floor(lower / niceTick) * niceTick;
            }
            // lower <= 0 时保持（通常是 0）
        }

        valueAxis.setRange(lower, niceUpper);
        if (chart.getYMajorUnit() == null) {
            valueAxis.setTickUnit(new NumberTickUnit(niceTick));
        }
    }

    /**
     * "漂亮"刻度间距：1-2-5 序列 × 10^n。
     * 例如：44 → 50, 8 → 10, 0.3 → 0.5, 300 → 500
     */
    private static double niceTickUnit(double rawTick) {
        if (rawTick <= 0) return 1;
        double exponent = Math.floor(Math.log10(rawTick));
        double fraction = rawTick / Math.pow(10, exponent);
        double niceFraction;
        if (fraction <= 1.0) niceFraction = 1;
        else if (fraction <= 2.0) niceFraction = 2;
        else if (fraction <= 5.0) niceFraction = 5;
        else niceFraction = 10;
        return niceFraction * Math.pow(10, exponent);
    }

    /**
     * 把每个 series 的 color 应用到 JFreeChart 的 category renderer 上。
     * series.color 为空时按 Office 默认调色板兜底，与 Word 内嵌 chart 默认色一致。
     */
    private static void applySeriesColors(org.jfree.chart.renderer.category.AbstractCategoryItemRenderer renderer,
                                          List<Chart.Series> seriesList) {
        for (int si = 0; si < seriesList.size(); si++) {
            renderer.setSeriesPaint(si, parseHexColor(seriesList.get(si).getColor(), si));
        }
    }

    /**
     * hex（如 "4F81BD"）→ AWT Color；为空/非法时按 paletteIndex 取 Office 默认调色板。
     */
    private static java.awt.Color parseHexColor(String hex, int paletteIndex) {
        if (hex != null) {
            String s = hex.trim();
            if (s.startsWith("#")) s = s.substring(1);
            if (s.length() == 6) {
                try {
                    return new java.awt.Color(Integer.parseInt(s, 16));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (paletteIndex < 0) return null;
        return OFFICE_DEFAULT_PALETTE[Math.floorMod(paletteIndex, OFFICE_DEFAULT_PALETTE.length)];
    }

    /**
     * Office 2007 chart 默认调色板（accent1..accent6）
     */
    private static final java.awt.Color[] OFFICE_DEFAULT_PALETTE = new java.awt.Color[]{
            new java.awt.Color(0x4F81BD),
            new java.awt.Color(0xC0504D),
            new java.awt.Color(0x9BBB59),
            new java.awt.Color(0x8064A2),
            new java.awt.Color(0x4BACC6),
            new java.awt.Color(0xF79646),
    };

    private Font loadCjkFont(List<String> fontPaths) {
        if (fontPaths == null || fontPaths.isEmpty()) {
            throw FontNotFoundException.unsupported(
                    "未配置 simple-doc-template.font-paths，chart PNG 渲染需要至少一个可用字体文件");
        }
        for (String pathStr : fontPaths) {
            Path p = Paths.get(pathStr);
            if (!Files.exists(p)) continue;
            if (Files.isDirectory(p)) {
                Font f = scanDir(p);
                if (f != null) return f;
            } else {
                Font f = tryCreate(p);
                if (f != null) return f;
            }
        }
        throw FontNotFoundException.unsupported(
                "simple-doc-template.font-paths 中没有可用的字体文件: " + fontPaths);
    }

    private Font scanDir(Path dir) {
        try (java.util.stream.Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(ChartToPngRenderer::isFontFile)
                    .map(this::tryCreate)
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isFontFile(Path p) {
        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".ttf") || name.endsWith(".ttc") || name.endsWith(".otf");
    }

    private Font tryCreate(Path file) {
        try (InputStream is = Files.newInputStream(file)) {
            return Font.createFont(Font.TRUETYPE_FONT, is).deriveFont(12f);
        } catch (Exception e) {
            return null;
        }
    }
}
