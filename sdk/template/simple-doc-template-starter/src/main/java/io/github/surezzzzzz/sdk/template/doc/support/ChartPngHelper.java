package io.github.surezzzzzz.sdk.template.doc.support;

import io.github.surezzzzzz.sdk.template.doc.annotation.SimpleDocTemplateComponent;
import io.github.surezzzzzz.sdk.template.doc.configuration.SimpleDocTemplateProperties;
import io.github.surezzzzzz.sdk.template.doc.model.Chart;
import io.github.surezzzzzz.sdk.template.doc.renderer.ChartToPngRenderer;
import lombok.RequiredArgsConstructor;

import javax.annotation.PostConstruct;

/**
 * Chart PNG Helper
 *
 * <p>对外提供 Chart 模型直接渲染为 PNG 的能力，适用于邮件、HTML、预览等非 DOCX/PDF 场景。
 * 字体来源统一使用全局配置 {@link SimpleDocTemplateProperties#getFontPaths()}。
 *
 * @author surezzzzzz
 */
@SimpleDocTemplateComponent
@RequiredArgsConstructor
public class ChartPngHelper {

    private final SimpleDocTemplateProperties properties;

    private ChartToPngRenderer renderer;

    @PostConstruct
    void init() {
        renderer = new ChartToPngRenderer(properties.getFontPaths());
    }

    /**
     * 将 Chart 渲染为 PNG 字节数组
     *
     * @param chart 图表模型
     * @return PNG 字节数组
     */
    public byte[] toPng(Chart chart) {
        return renderer.render(chart);
    }

    /**
     * 将 Chart 渲染为指定尺寸的 PNG 字节数组
     *
     * @param chart  图表模型
     * @param width  输出宽度（px）
     * @param height 输出高度（px）
     * @return PNG 字节数组
     */
    public byte[] toPng(Chart chart, int width, int height) {
        return renderer.render(chart, width, height);
    }
}
