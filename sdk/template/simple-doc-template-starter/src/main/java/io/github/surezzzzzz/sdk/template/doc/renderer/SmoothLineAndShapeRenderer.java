package io.github.surezzzzzz.sdk.template.doc.renderer;

import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.entity.EntityCollection;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.data.category.CategoryDataset;

import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;

/**
 * 用 Catmull-Rom spline 渲染平滑折线（OOXML c:smooth="1" 的 JFreeChart 等价实现）。
 * <p>
 * JFreeChart 1.5.x 的 category 系列只有直线 {@link LineAndShapeRenderer}，没有 spline 版本。
 * 这里继承 LineAndShapeRenderer，在 pass=0（画线段）时拦截：
 *   - 第一个 item 累积，最后一个 item 用整段所有点做一次 Catmull-Rom→cubic-bezier 转换，绘制为 GeneralPath；
 *   - shape/marker（pass=1）走父类不变。
 * <p>
 * 这样：
 *   - 颜色/笔触/marker 全部继承父类设置（applySeriesColors 仍生效）
 *   - 不破坏 series 的 entity / tooltip 注册
 *   - 完全数据驱动，模板里 c:smooth="0" 不走这条路径，自然回退直线
 *
 * @author surezzzzzz
 */
public class SmoothLineAndShapeRenderer extends LineAndShapeRenderer {

    private static final long serialVersionUID = 1L;

    /** Catmull-Rom 张力参数；0.5 是经典值，与 Office 视觉接近。 */
    private static final double TENSION = 0.5d;

    public SmoothLineAndShapeRenderer(boolean lines, boolean shapes) {
        super(lines, shapes);
    }

    @Override
    public void drawItem(Graphics2D g2, org.jfree.chart.renderer.category.CategoryItemRendererState state,
                         Rectangle2D dataArea, CategoryPlot plot, CategoryAxis domainAxis,
                         ValueAxis rangeAxis, CategoryDataset dataset, int row, int column,
                         int pass) {
        // pass=0 画线，pass=1 画 shape/marker。仅 pass=0 走平滑；shape 交回父类。
        if (pass != 0 || !getItemLineVisible(row, column)) {
            super.drawItem(g2, state, dataArea, plot, domainAxis, rangeAxis,
                    dataset, row, column, pass);
            return;
        }
        int colCount = dataset.getColumnCount();
        // 仅在系列最后一个有效点触发整条曲线绘制（一次性绘制，避免 N 次重画）
        if (column != colCount - 1) {
            return;
        }
        // 收集系列所有非空点坐标
        double[] xs = new double[colCount];
        double[] ys = new double[colCount];
        int n = 0;
        PlotOrientation orientation = plot.getOrientation();
        for (int c = 0; c < colCount; c++) {
            Number v = dataset.getValue(row, c);
            if (v == null) continue;
            double x = domainAxis.getCategoryMiddle(c, colCount, dataArea, plot.getDomainAxisEdge());
            double y = rangeAxis.valueToJava2D(v.doubleValue(), dataArea, plot.getRangeAxisEdge());
            if (orientation == PlotOrientation.HORIZONTAL) {
                xs[n] = y;
                ys[n] = x;
            } else {
                xs[n] = x;
                ys[n] = y;
            }
            n++;
        }
        if (n < 2) return;

        GeneralPath path = buildCatmullRomPath(xs, ys, n);
        g2.setPaint(getItemPaint(row, column));
        g2.setStroke(getItemStroke(row, column));
        g2.draw(path);

        // 注册 entity（与父类的最后一个 item 等价，保留交互/tooltip 兼容）
        EntityCollection entities = state.getEntityCollection();
        if (entities != null) {
            Shape hot = path.getBounds2D();
            addItemEntity(entities, dataset, row, column, hot);
        }
    }

    /**
     * 用 Catmull-Rom spline 把 n 个点连成平滑曲线，转成 cubic bezier 的 GeneralPath。
     * 端点用首/末点自身代替"前一点"/"后一点"（即 phantom 点），避免端点切线异常。
     */
    private static GeneralPath buildCatmullRomPath(double[] xs, double[] ys, int n) {
        GeneralPath path = new GeneralPath();
        path.moveTo((float) xs[0], (float) ys[0]);
        for (int i = 0; i < n - 1; i++) {
            double x0 = i == 0 ? xs[0] : xs[i - 1];
            double y0 = i == 0 ? ys[0] : ys[i - 1];
            double x1 = xs[i];
            double y1 = ys[i];
            double x2 = xs[i + 1];
            double y2 = ys[i + 1];
            double x3 = i + 2 < n ? xs[i + 2] : xs[i + 1];
            double y3 = i + 2 < n ? ys[i + 2] : ys[i + 1];
            // Catmull-Rom → cubic Bezier 控制点（标准公式）
            double cp1x = x1 + (x2 - x0) * TENSION / 3.0;
            double cp1y = y1 + (y2 - y0) * TENSION / 3.0;
            double cp2x = x2 - (x3 - x1) * TENSION / 3.0;
            double cp2y = y2 - (y3 - y1) * TENSION / 3.0;
            path.curveTo((float) cp1x, (float) cp1y,
                    (float) cp2x, (float) cp2y,
                    (float) x2, (float) y2);
        }
        return path;
    }
}
