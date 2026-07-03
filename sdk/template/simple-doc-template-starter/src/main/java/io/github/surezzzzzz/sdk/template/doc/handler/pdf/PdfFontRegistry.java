package io.github.surezzzzzz.sdk.template.doc.handler.pdf;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import io.github.surezzzzzz.sdk.template.doc.annotation.SimpleDocTemplateComponent;
import io.github.surezzzzzz.sdk.template.doc.support.FontResourceRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.*;

/**
 * PDF Font Registry
 *
 * <p>负责创建字体注册计划、向 PdfRendererBuilder 注册字体、生成 CSS font-family 链。
 * 每个字体文件只注册自己解析出的族名，避免笛卡尔积导致 OOM。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleDocTemplateComponent
@RequiredArgsConstructor
public class PdfFontRegistry {

    private final FontResourceRegistry fontResourceRegistry;

    /**
     * 创建字体注册计划。
     *
     * @return 字体注册计划
     */
    public PdfFontRegistrationPlan createPlan() {
        List<File> fontFiles = fontResourceRegistry.resolveFontFiles();
        Map<File, List<String>> fileFamilyNames = new LinkedHashMap<>();
        List<String> allFamilies = new ArrayList<>();

        for (File fontFile : fontFiles) {
            List<String> families = new ArrayList<>();
            // 真实族名（中文名 + 英文名）
            families.addAll(fontResourceRegistry.readFontFamilyNames(fontFile));
            // 文件名兜底
            String fileBase = stripExtension(fontFile.getName());
            if (!families.contains(fileBase)) {
                families.add(fileBase);
            }
            fileFamilyNames.put(fontFile, families);
            for (String family : families) {
                if (!allFamilies.contains(family)) {
                    allFamilies.add(family);
                }
            }
        }

        return new PdfFontRegistrationPlan(fontFiles, fileFamilyNames, allFamilies, buildCssFontFamily(allFamilies));
    }

    /**
     * 注册字体到 PDF builder。
     * 每个文件只注册自己的族名，不做笛卡尔积。
     *
     * @param builder PDF builder
     * @param plan    字体计划
     */
    public void registerFonts(PdfRendererBuilder builder, PdfFontRegistrationPlan plan) {
        if (builder == null || plan == null) {
            return;
        }
        for (File fontFile : plan.getFontFiles()) {
            List<String> families = plan.getFileFamilyNames().getOrDefault(fontFile, Collections.emptyList());
            for (String family : families) {
                try {
                    builder.useFont(fontFile, family);
                } catch (Exception e) {
                    log.debug("注册字体失败: {} -> {} - {}", fontFile.getName(), family, e.getMessage());
                }
            }
            if (!families.isEmpty()) {
                log.debug("外部字体注册: {} -> {}", fontFile.getName(), families);
            }
        }
        if (plan.getFamilyNames().isEmpty()) {
            log.warn("未注册任何字体（fontPaths 为空或文件不存在），PDF 中中文可能显示为 #");
        } else {
            log.debug("字体注册完成，族名共 {} 个", plan.getFamilyNames().size());
        }
    }

    private String buildCssFontFamily(List<String> familyNames) {
        StringBuilder sb = new StringBuilder();
        for (String family : familyNames) {
            if (family == null || family.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append('\'').append(cssEscape(family)).append('\'');
        }
        if (sb.length() > 0) {
            sb.append(", sans-serif");
        } else {
            sb.append("sans-serif");
        }
        return sb.toString();
    }

    private String cssEscape(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
