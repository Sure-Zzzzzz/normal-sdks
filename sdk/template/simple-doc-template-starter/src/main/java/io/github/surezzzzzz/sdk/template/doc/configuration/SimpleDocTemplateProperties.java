package io.github.surezzzzzz.sdk.template.doc.configuration;

import io.github.surezzzzzz.sdk.template.doc.constant.SimpleDocTemplateConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Simple Doc Template Properties
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(SimpleDocTemplateConstant.CONFIG_PREFIX)
public class SimpleDocTemplateProperties {

    /**
     * 是否启用（默认 false）
     */
    private boolean enable = SimpleDocTemplateConstant.DEFAULT_ENABLE;

    /**
     * 模板根路径
     */
    private String templateLocation = SimpleDocTemplateConstant.DEFAULT_TEMPLATE_LOCATION;

    /**
     * 标签前缀，用于拼接模板占位符（如 [suredt.var:key]），可自定义但需与模板中占位符一致
     */
    private String tagPrefix = SimpleDocTemplateConstant.DEFAULT_TAG_PREFIX;

    /**
     * 字体文件路径列表（PDF 渲染用）
     * <p>
     * 路径可以是字体文件或目录（目录时自动扫描其下所有 .ttf/.ttc 文件）。
     * 不存在的路径静默跳过。配了就覆盖默认值。
     */
    private List<String> fontPaths = new ArrayList<>(Arrays.asList(SimpleDocTemplateConstant.DEFAULT_FONT_PATHS));

    /**
     * 最大模板大小（字节）
     */
    private long maxTemplateBytes = SimpleDocTemplateConstant.DEFAULT_MAX_TEMPLATE_BYTES;

    /**
     * 最大图片大小（字节）
     */
    private long maxImageBytes = SimpleDocTemplateConstant.DEFAULT_MAX_IMAGE_BYTES;

    /**
     * 最大 DOCX 解压后大小（字节）
     */
    private long maxDocxUncompressedBytes = SimpleDocTemplateConstant.DEFAULT_MAX_DOCX_UNCOMPRESSED_BYTES;

    /**
     * 最大 ZIP entry 数量
     */
    private int maxZipEntryCount = SimpleDocTemplateConstant.DEFAULT_MAX_ZIP_ENTRY_COUNT;

    /**
     * 是否允许远程资源
     */
    private boolean allowRemoteResource = SimpleDocTemplateConstant.DEFAULT_ALLOW_REMOTE_RESOURCE;

}