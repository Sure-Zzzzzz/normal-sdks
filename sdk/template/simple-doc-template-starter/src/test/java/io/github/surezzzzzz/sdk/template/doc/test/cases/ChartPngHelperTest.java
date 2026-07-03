package io.github.surezzzzzz.sdk.template.doc.test.cases;

import io.github.surezzzzzz.sdk.template.doc.model.Chart;
import io.github.surezzzzzz.sdk.template.doc.support.ChartPngHelper;
import io.github.surezzzzzz.sdk.template.doc.test.SimpleDocTemplateTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chart PNG Helper Test
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleDocTemplateTestApplication.class)
@DisplayName("Chart PNG Helper 测试")
class ChartPngHelperTest {

    /**
     * PNG 文件头
     */
    private static final byte[] PNG_SIGNATURE = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47};

    /**
     * 测试输出目录
     */
    private static final Path OUTPUT_DIR = Paths.get("build/test-output/png");

    @Autowired
    private ChartPngHelper chartPngHelper;

    @BeforeEach
    void ensureOutputDir() throws Exception {
        Files.createDirectories(OUTPUT_DIR);
    }

    @Test
    @DisplayName("toPng：使用 Chart 自身尺寸渲染 PNG")
    void toPngWithChartSize() throws Exception {
        Chart chart = buildChart();

        byte[] pngBytes = chartPngHelper.toPng(chart);
        Path outputPath = OUTPUT_DIR.resolve("chart-default-size.png");
        Files.write(outputPath, pngBytes);

        log.info("Chart PNG 渲染完成: {}, {} bytes", outputPath.toAbsolutePath(), pngBytes.length);
        assertValidPng(pngBytes);
        assertTrue(Files.exists(outputPath), "PNG 输出文件应存在");
    }

    @Test
    @DisplayName("toPng：使用指定尺寸渲染 PNG")
    void toPngWithCustomSize() throws Exception {
        Chart chart = buildChart();

        byte[] pngBytes = chartPngHelper.toPng(chart, 480, 320);
        Path outputPath = OUTPUT_DIR.resolve("chart-custom-size.png");
        Files.write(outputPath, pngBytes);

        log.info("指定尺寸 Chart PNG 渲染完成: {}, {} bytes", outputPath.toAbsolutePath(), pngBytes.length);
        assertValidPng(pngBytes);
        assertTrue(Files.exists(outputPath), "PNG 输出文件应存在");
    }

    private Chart buildChart() {
        return new Chart(
                "访问趋势",
                Arrays.asList("周一", "周二", "周三", "周四"),
                Arrays.asList(new Chart.Series("访问量", Arrays.asList(120, 180, 160, 220), "4F81BD")),
                600,
                360,
                Chart.ChartType.LINE,
                14,
                Chart.LegendPosition.BOTTOM,
                true,
                true,
                false,
                true,
                null,
                null,
                null,
                null,
                null,
                true,
                false
        );
    }

    private void assertValidPng(byte[] pngBytes) {
        assertNotNull(pngBytes, "PNG 字节不应为空");
        assertTrue(pngBytes.length > PNG_SIGNATURE.length, "PNG 字节长度应大于文件头长度");
        assertArrayEquals(PNG_SIGNATURE, Arrays.copyOf(pngBytes, PNG_SIGNATURE.length), "PNG 文件头应正确");
    }
}
