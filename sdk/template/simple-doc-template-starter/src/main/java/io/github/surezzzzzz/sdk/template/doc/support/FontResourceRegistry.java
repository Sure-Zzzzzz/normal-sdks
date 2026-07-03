package io.github.surezzzzzz.sdk.template.doc.support;

import io.github.surezzzzzz.sdk.template.doc.annotation.SimpleDocTemplateComponent;
import io.github.surezzzzzz.sdk.template.doc.configuration.SimpleDocTemplateProperties;
import io.github.surezzzzzz.sdk.template.doc.constant.SimpleDocTemplateConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FilenameFilter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Font Resource Registry
 *
 * <p>统一解析字体资源，供 PDF 和 Chart 复用。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleDocTemplateComponent
@RequiredArgsConstructor
public class FontResourceRegistry {

    private static final FilenameFilter FONT_FILE_FILTER = (dir, name) -> {
        String lower = name.toLowerCase();
        return lower.endsWith(".ttf") || lower.endsWith(".ttc") || lower.endsWith(".otf");
    };

    private final SimpleDocTemplateProperties properties;

    /**
     * 解析字体文件。
     *
     * @return 字体文件列表
     */
    public List<File> resolveFontFiles() {
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
                    for (File font : fonts) {
                        result.add(font);
                    }
                }
            } else if (FONT_FILE_FILTER.accept(file.getParentFile(), file.getName())) {
                result.add(file);
            }
        }
        return result;
    }

    /**
     * 读取字体族名。
     *
     * @param fontFile 字体文件
     * @return 字体族名列表
     */
    public List<String> readFontFamilyNames(File fontFile) {
        List<String> names = new ArrayList<>();
        java.awt.Font font;
        try (java.io.InputStream is = Files.newInputStream(fontFile.toPath())) {
            font = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, is);
        } catch (Exception e) {
            log.warn("读取字体真实族名失败: {} - {}", fontFile.getAbsolutePath(), e.getMessage());
            return names;
        }
        java.util.Locale[] locales = {java.util.Locale.ENGLISH, java.util.Locale.SIMPLIFIED_CHINESE};
        for (java.util.Locale locale : locales) {
            String family = font.getFamily(locale);
            if (family != null && !family.isEmpty() && !names.contains(family)) {
                names.add(family);
            }
        }
        return names;
    }

    /**
     * 解析 Chart 字体。
     *
     * @return AWT 字体
     */
    public java.awt.Font resolveChartFont() {
        List<File> fontFiles = resolveFontFiles();
        for (File fontFile : fontFiles) {
            try (java.io.InputStream is = Files.newInputStream(fontFile.toPath())) {
                return java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, is).deriveFont(12f);
            } catch (Exception e) {
                log.debug("Chart 字体解析失败: {} - {}", fontFile.getAbsolutePath(), e.getMessage());
            }
        }
        return new java.awt.Font(SimpleDocTemplateConstant.EMPTY, java.awt.Font.PLAIN, 12);
    }
}
