package io.github.surezzzzzz.sdk.template.doc.model;

import lombok.Getter;

import java.util.List;

/**
 * Chart - Word 原生可编辑图表数据
 *
 * <p>封装图表的标题、分类标签、数据系列和样式选项，渲染阶段由 WordRenderer 生成 OOXML chart 文件。
 * 支持折线图、柱状图、饼图。
 *
 * <p>样式选项：
 * <ul>
 *   <li>每系列颜色、标题字号、图例位置（柱状/折线图）</li>
 *   <li>折线图：平滑曲线、数据点标记</li>
 *   <li>柱状图：柱宽百分比、分组间距</li>
 *   <li>饼图：引导线、起始角度</li>
 * </ul>
 *
 * @author surezzzzzz
 */
@Getter
public class Chart {

    /**
     * 图表标题
     */
    private final String title;

    /**
     * X 轴分类标签（如日期/类别名）
     */
    private final List<String> categories;

    /**
     * 数据系列列表
     */
    private final List<Series> series;

    /**
     * 图表宽度（px）。&lt;=0 时使用渲染器默认值（约 952px）
     */
    private final int width;

    /**
     * 图表高度（px）
     */
    private final int height;

    /**
     * 图表类型（默认折线图）
     */
    private final ChartType chartType;

    /**
     * 标题字号（默认 14）
     */
    private final int titleFontSize;

    /**
     * 图例位置（默认底部）。null 表示跟随渲染器默认值
     */
    private final LegendPosition legendPosition;

    /**
     * 折线图：是否使用平滑曲线（默认 true）
     */
    private final boolean smooth;

    /**
     * 折线图：是否显示数据点标记（默认 true）
     */
    private final boolean showMarker;

    /**
     * 是否显示网格线（默认 false）
     */
    private final boolean showGridLines;

    /**
     * 标题是否粗体（默认 true，与 Office 默认一致）
     */
    private final boolean titleBold;

    /**
     * 柱状图：分组间距百分比（OOXML c:gapWidth val），null 表示走 JFreeChart 默认值
     */
    private final Integer barGapWidth;

    /**
     * Y 轴最小值（c:scaling/c:min），null 表示自动量程
     */
    private final Double yMin;

    /**
     * Y 轴最大值（c:scaling/c:max），null 表示自动量程
     */
    private final Double yMax;

    /**
     * Y 轴主刻度间隔（c:majorUnit），null 表示自动
     */
    private final Double yMajorUnit;

    /**
     * Y 轴数字格式（c:numFmt formatCode，如 "0", "0.00", "0%"），null 走 General
     */
    private final String yNumFmt;

    /**
     * 是否在数据点上显示数值（c:dLbls/c:showVal），默认 false
     */
    private final boolean showVal;

    /**
     * 柱状图是否为 3D 样式（OOXML c:bar3DChart），默认 false（2D 平面 c:barChart）。
     * 2D 时柱子纯平色，3D 时带渐变和阴影。
     */
    private final boolean bar3D;

    // ===== 构造器 =====

    /**
     * 默认标题字号
     */
    private static final int DEFAULT_TITLE_FONT_SIZE = 14;

    /**
     * 基础构造，类型默认 LINE，样式使用默认值
     */
    public Chart(String title, List<String> categories, List<Series> series, int width, int height) {
        this(title, categories, series, width, height, ChartType.LINE,
                DEFAULT_TITLE_FONT_SIZE, LegendPosition.BOTTOM, true, true, false);
    }

    /**
     * 指定图表类型，样式使用默认值
     */
    public Chart(String title, List<String> categories, List<Series> series,
                 int width, int height, ChartType chartType) {
        this(title, categories, series, width, height, chartType,
                DEFAULT_TITLE_FONT_SIZE, LegendPosition.BOTTOM, true, true, false);
    }

    /**
     * 11 参构造器：兼容老调用方，扩展字段走默认值
     */
    public Chart(String title, List<String> categories, List<Series> series,
                 int width, int height, ChartType chartType,
                 int titleFontSize, LegendPosition legendPosition,
                 boolean smooth, boolean showMarker, boolean showGridLines) {
        this(title, categories, series, width, height, chartType,
                titleFontSize, legendPosition, smooth, showMarker, showGridLines,
                true, null, null, null, null, null, false, false);
    }

    /**
     * 全量构造器（含 OOXML 解析的扩展字段）
     *
     * @param title          图表标题
     * @param categories     X 轴分类标签
     * @param series         数据系列
     * @param width          宽度（px）。0 = 使用渲染器默认值（约 952px）
     * @param height         高度（px）
     * @param chartType      图表类型（默认 LINE）
     * @param titleFontSize  标题字号（默认 14）
     * @param legendPosition 图例位置（默认底部）
     * @param smooth         折线图：是否平滑
     * @param showMarker     折线图：是否显示标记点
     * @param showGridLines  是否显示网格线
     * @param titleBold      标题粗体（默认 true）
     * @param barGapWidth    柱状图分组间距百分比（null 走默认）
     * @param yMin           Y 轴最小值（null 自动）
     * @param yMax           Y 轴最大值（null 自动）
     * @param yMajorUnit     Y 轴主刻度间隔（null 自动）
     * @param yNumFmt        Y 轴数字格式（null 走 General）
     * @param showVal        数据标签是否显示数值
     * @param bar3D          柱状图是否 3D 样式（c:bar3DChart），默认 false
     */
    public Chart(String title, List<String> categories, List<Series> series,
                 int width, int height, ChartType chartType,
                 int titleFontSize, LegendPosition legendPosition,
                 boolean smooth, boolean showMarker, boolean showGridLines,
                 boolean titleBold, Integer barGapWidth,
                 Double yMin, Double yMax, Double yMajorUnit, String yNumFmt,
                 boolean showVal, boolean bar3D) {
        this.title = title;
        this.categories = categories;
        this.series = series;
        this.width = width;
        this.height = height;
        this.chartType = chartType;
        this.titleFontSize = titleFontSize > 0 ? titleFontSize : DEFAULT_TITLE_FONT_SIZE;
        this.legendPosition = legendPosition;
        this.smooth = smooth;
        this.showMarker = showMarker;
        this.showGridLines = showGridLines;
        this.titleBold = titleBold;
        this.barGapWidth = barGapWidth;
        this.yMin = yMin;
        this.yMax = yMax;
        this.yMajorUnit = yMajorUnit;
        this.yNumFmt = yNumFmt;
        this.showVal = showVal;
        this.bar3D = bar3D;
    }

    /**
     * width == 0 时使用渲染器默认值
     */
    public boolean isAutoWidth() {
        return width == 0;
    }

    /**
     * height == 0 时使用渲染器默认值
     */
    public boolean isAutoHeight() {
        return height == 0;
    }

    /**
     * 获取实际宽度：width &gt; 0 返回指定值，否则返回 0（由渲染器填默认值）
     */
    public int resolveWidth() {
        return width > 0 ? width : 0;
    }

    /**
     * 图例位置枚举
     */
    @Getter
    public enum LegendPosition {

        /**
         * 底部
         */
        BOTTOM("b", "底部"),

        /**
         * 顶部
         */
        TOP("t", "顶部"),

        /**
         * 左侧
         */
        LEFT("l", "左侧"),

        /**
         * 右侧
         */
        RIGHT("r", "右侧"),

        /**
         * 不显示
         */
        NONE("0", "不显示");

        private final String code;
        private final String description;

        LegendPosition(String code, String description) {
            this.code = code;
            this.description = description;
        }

        /**
         * 根据 code 获取枚举
         *
         * @param code 位置代码
         * @return 枚举，不存在返回 null
         */
        public static LegendPosition fromCode(String code) {
            if (code == null) {
                return null;
            }
            for (LegendPosition pos : values()) {
                if (pos.code.equalsIgnoreCase(code)) {
                    return pos;
                }
            }
            return null;
        }

        /**
         * 判断 code 是否有效
         */
        public static boolean isValid(String code) {
            return fromCode(code) != null;
        }

        /**
         * 获取所有有效 code
         */
        public static String[] getAllCodes() {
            LegendPosition[] positions = values();
            String[] codes = new String[positions.length];
            for (int i = 0; i < positions.length; i++) {
                codes[i] = positions[i].code;
            }
            return codes;
        }

        @Override
        public String toString() {
            return code;
        }
    }

    /**
     * 图表类型枚举
     */
    @Getter
    public enum ChartType {

        /**
         * 折线图
         */
        LINE("line", "折线图"),

        /**
         * 柱状图
         */
        BAR("bar", "柱状图"),

        /**
         * 饼图
         */
        PIE("pie", "饼图");

        private final String code;
        private final String description;

        ChartType(String code, String description) {
            this.code = code;
            this.description = description;
        }

        /**
         * 根据 code 获取枚举
         *
         * @param code 类型代码
         * @return 枚举，不存在返回 null
         */
        public static ChartType fromCode(String code) {
            if (code == null) {
                return null;
            }
            for (ChartType type : values()) {
                if (type.code.equalsIgnoreCase(code)) {
                    return type;
                }
            }
            return null;
        }

        /**
         * 判断 code 是否有效
         */
        public static boolean isValid(String code) {
            return fromCode(code) != null;
        }

        /**
         * 获取所有有效 code
         */
        public static String[] getAllCodes() {
            ChartType[] types = values();
            String[] codes = new String[types.length];
            for (int i = 0; i < types.length; i++) {
                codes[i] = types[i].code;
            }
            return codes;
        }

        @Override
        public String toString() {
            return code;
        }
    }

    /**
     * 数据系列
     */
    @Getter
    public static class Series {
        /**
         * 系列名称（图例中显示）
         */
        private final String name;

        /**
         * 数据值（与 categories 一一对应）
         */
        private final List<Number> values;

        /**
         * 系列颜色（如 "4F81BD"，null 表示使用渲染器默认色）
         */
        private final String color;

        /**
         * 构造系列，颜色为 null（使用默认色）
         */
        public Series(String name, List<Number> values) {
            this(name, values, null);
        }

        /**
         * 构造系列，指定颜色
         *
         * @param name   系列名称
         * @param values 数据值
         * @param color  RGB 颜色值，如 "4F81BD"，null 表示默认色
         */
        public Series(String name, List<Number> values, String color) {
            this.name = name;
            this.values = values;
            this.color = color;
        }
    }
}