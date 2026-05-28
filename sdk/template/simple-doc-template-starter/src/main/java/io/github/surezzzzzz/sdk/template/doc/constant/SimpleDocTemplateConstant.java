package io.github.surezzzzzz.sdk.template.doc.constant;

/**
 * Simple Doc Template Constants
 *
 * @author surezzzzzz
 */
public final class SimpleDocTemplateConstant {

    private SimpleDocTemplateConstant() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== 配置相关 ====================

    /**
     * 配置前缀
     */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.template.doc";

    /**
     * 默认模板根路径
     */
    public static final String DEFAULT_TEMPLATE_LOCATION = "classpath:templates/";

    /**
     * 默认标签前缀
     */
    public static final String DEFAULT_TAG_PREFIX = "suredt";

    /**
     * 文件编码
     */
    public static final String CHARSET_UTF8 = "UTF-8";

    // ==================== 文件相关 ====================

    /**
     * DOCX 文件后缀
     */
    public static final String SUFFIX_DOCX = ".docx";

    /**
     * Markdown 文件后缀
     */
    public static final String SUFFIX_MD = ".md";

    // ==================== 格式标识 ====================

    /**
     * 格式标识：DOCX
     */
    public static final String FORMAT_DOCX = "docx";

    /**
     * 格式标识：Markdown
     */
    public static final String FORMAT_MD = "md";

    /**
     * 格式标识：PDF（P1）
     */
    public static final String FORMAT_PDF = "pdf";

    /**
     * 格式标识：HTML（P1）
     */
    public static final String FORMAT_HTML = "html";

    // ==================== DOCX 内部路径 ====================

    /**
     * DOCX 主文档 XML 路径
     */
    public static final String DOCX_DOCUMENT_XML = "word/document.xml";

    // ==================== IO ====================

    /**
     * IO 缓冲区大小（字节）
     */
    public static final int IO_BUFFER_SIZE = 4096;

    // ==================== 标签前缀模板（参数：prefix） ====================

    /**
     * 标签前缀模板：文本变量
     */
    public static final String TAG_VAR_PREFIX_TEMPLATE = "[%s.var:";

    /**
     * 标签前缀模板：图片
     */
    public static final String TAG_IMG_PREFIX_TEMPLATE = "[%s.img:";

    /**
     * 标签前缀模板：图表
     */
    public static final String TAG_CHART_PREFIX_TEMPLATE = "[%s.chart:";

    /**
     * 标签前缀模板：条件块开始
     */
    public static final String TAG_START_PREFIX_TEMPLATE = "[%s.start:";

    /**
     * 标签前缀模板：条件块结束
     */
    public static final String TAG_END_PREFIX_TEMPLATE = "[%s.end:";

    /**
     * 标签前缀模板：循环开始
     */
    public static final String TAG_FOR_PREFIX_TEMPLATE = "[%s.for:";

    /**
     * 标签前缀模板：循环结束
     */
    public static final String TAG_ENDFOR_PREFIX_TEMPLATE = "[%s.endfor:";

    // ==================== 完整标签模板（参数：prefix, key） ====================

    /**
     * 完整标签模板：条件块开始
     */
    public static final String TAG_START_TAG_TEMPLATE = "[%s.start:%s]";

    /**
     * 完整标签模板：条件块结束
     */
    public static final String TAG_END_TAG_TEMPLATE = "[%s.end:%s]";

    /**
     * 完整标签模板：循环开始
     */
    public static final String TAG_FOR_TAG_TEMPLATE = "[%s.for:%s]";

    /**
     * 完整标签模板：循环结束
     */
    public static final String TAG_ENDFOR_TAG_TEMPLATE = "[%s.endfor:%s]";
}
