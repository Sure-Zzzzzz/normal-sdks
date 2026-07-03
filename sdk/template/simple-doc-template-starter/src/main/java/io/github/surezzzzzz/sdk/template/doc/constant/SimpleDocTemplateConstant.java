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
     * 默认是否启用
     */
    public static final boolean DEFAULT_ENABLE = false;

    /**
     * 默认模板根路径
     */
    public static final String DEFAULT_TEMPLATE_LOCATION = "classpath:templates/";

    /**
     * 默认标签前缀
     */
    public static final String DEFAULT_TAG_PREFIX = "suredt";

    /**
     * 默认最大模板大小（字节）
     */
    public static final long DEFAULT_MAX_TEMPLATE_BYTES = 10 * 1024 * 1024;

    /**
     * 默认最大图片大小（字节）
     */
    public static final long DEFAULT_MAX_IMAGE_BYTES = 5 * 1024 * 1024;

    /**
     * 默认最大 DOCX 解压后大小（字节）
     */
    public static final long DEFAULT_MAX_DOCX_UNCOMPRESSED_BYTES = 50 * 1024 * 1024;

    /**
     * 默认最大 ZIP entry 数量
     */
    public static final int DEFAULT_MAX_ZIP_ENTRY_COUNT = 2048;

    /**
     * 是否默认允许远程资源
     */
    public static final boolean DEFAULT_ALLOW_REMOTE_RESOURCE = false;

    /**
     * 空字符串
     */
    public static final String EMPTY = "";

    /**
     * 默认字体路径列表（PDF 渲染用，路径可以是文件或目录）
     * <p>
     * 按优先级排列，.ttf 优先于 .ttc（openhtmltopdf 对 .ttc 支持有限）。
     * 业务方通过 font-paths 配置覆盖此默认值。
     */
    public static final String[] DEFAULT_FONT_PATHS = {
            "C:/Windows/Fonts/simsun.ttc",
            "C:/Windows/Fonts/simhei.ttf",
            "C:/Windows/Fonts/simfang.ttf",
            "C:/Windows/Fonts/simkai.ttf",
            "C:/Windows/Fonts/msyh.ttc",
            "C:/Windows/Fonts/msyhbd.ttc",
            "C:/Windows/Fonts/msyhl.ttc",
            "/System/Library/Fonts/PingFang.ttc",
            "/System/Library/Fonts/STHeiti Light.ttc",
            "/System/Library/Fonts/STHeiti Medium.ttc",
            "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
    };

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
     * PDF 模板支持后缀描述
     */
    public static final String PDF_TEMPLATE_SUFFIX_EXPECTED = ".docx/.md";

    /**
     * Markdown 换行符
     */
    public static final String MARKDOWN_LINE_SEPARATOR = "\n";

    /**
     * Markdown 表格单元格分隔符
     */
    public static final String MARKDOWN_TABLE_CELL_SEPARATOR = "|";

    /**
     * Markdown 图片模板，参数：description, src
     */
    public static final String MARKDOWN_IMAGE_TEMPLATE = "![%s](%s)";

    /**
     * SDK Markdown 图片 token 模板，参数：index
     */
    public static final String MARKDOWN_IMAGE_TOKEN_TEMPLATE = "suredt-img://%d";

    /**
     * HTML doctype（XML 严格格式，openhtmltopdf 要求大写）
     */
    public static final String HTML_DOCTYPE = "<!DOCTYPE html>";

    /**
     * HTML UTF-8 charset
     */
    public static final String HTML_CHARSET_UTF8 = "UTF-8";

    /**
     * URL scheme：http
     */
    public static final String URL_SCHEME_HTTP = "http";

    /**
     * URL scheme：https
     */
    public static final String URL_SCHEME_HTTPS = "https";

    /**
     * URL scheme：data
     */
    public static final String URL_SCHEME_DATA = "data";

    /**
     * URL scheme：file
     */
    public static final String URL_SCHEME_FILE = "file";

    /**
     * URL scheme：classpath
     */
    public static final String URL_SCHEME_CLASSPATH = "classpath";

    /**
     * URL scheme 前缀：classpath:
     */
    public static final String URL_SCHEME_CLASSPATH_PREFIX =
            URL_SCHEME_CLASSPATH + ":";

    /**
     * URL scheme：jar
     */
    public static final String URL_SCHEME_JAR = "jar";

    /**
     * URL scheme：javascript
     */
    public static final String URL_SCHEME_JAVASCRIPT = "javascript";

    /**
     * URL scheme：vbscript
     */
    public static final String URL_SCHEME_VBSCRIPT = "vbscript";

    // ==================== DOCX 内部路径 ====================

    /**
     * DOCX 主文档 XML 路径
     */
    public static final String DOCX_DOCUMENT_XML = "word/document.xml";

    /**
     * DOCX 主文档关系文件路径
     */
    public static final String DOCX_DOCUMENT_RELS = "word/_rels/document.xml.rels";

    /**
     * DOCX 内容类型文件路径
     */
    public static final String DOCX_CONTENT_TYPES = "[Content_Types].xml";

    /**
     * DOCX 图表目录
     */
    public static final String DOCX_CHARTS_DIR = "word/charts/";

    /**
     * DOCX 嵌入对象目录
     */
    public static final String DOCX_EMBEDDINGS_DIR = "word/embeddings/";

    /**
     * DOCX 媒体目录前缀
     */
    public static final String DOCX_MEDIA_DIR = "word/media/";

    /**
     * Chart marker 前缀（WordRenderer 写入、PdfOutputHandler 识别）
     */
    public static final String CHART_MARKER_PREFIX = "SUREDT_CHART_";

    /**
     * 渲染临时文件名前缀
     */
    public static final String TEMP_RENDERED_DOCX_PREFIX = "rendered-";

    // ==================== IO ====================

    /**
     * IO 缓冲区大小（字节）
     */
    public static final int IO_BUFFER_SIZE = 4096;

    // ==================== OOXML 命名空间 URI（DOCX 格式专属）====================

    /**
     * OOXML Relationships 命名空间
     */
    public static final String NS_RELATIONSHIPS = "http://schemas.openxmlformats.org/package/2006/relationships";

    /**
     * DrawingML WordprocessingDrawing 命名空间
     */
    public static final String NS_WP = "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing";

    /**
     * DrawingML Main 命名空间
     */
    public static final String NS_A = "http://schemas.openxmlformats.org/drawingml/2006/main";

    /**
     * DrawingML Chart 命名空间
     */
    public static final String NS_C = "http://schemas.openxmlformats.org/drawingml/2006/chart";

    /**
     * OfficeDocument Relationships 命名空间
     */
    public static final String NS_R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

    /**
     * Chart 关系类型
     */
    public static final String REL_TYPE_CHART = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/chart";

    /**
     * Image 关系类型
     */
    public static final String REL_TYPE_IMAGE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/image";

    /**
     * Package（嵌入式对象）关系类型
     */
    public static final String REL_TYPE_PACKAGE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/package";

    /**
     * Chart Content-Type
     */
    public static final String CT_CHART = "application/vnd.openxmlformats-officedocument.drawingml.chart+xml";

    /**
     * XLSX Content-Type
     */
    public static final String CT_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

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

    // ==================== 临时文件 ====================

    /**
     * DOCX 临时文件名前缀
     */
    public static final String TEMP_DOCX_PREFIX = "docx-";

    /**
     * ZIP 临时文件后缀
     */
    public static final String TEMP_ZIP_SUFFIX = ".zip";

    // ==================== 业务描述 ====================

    /**
     * WordRenderer 类名
     */
    public static final String RENDERER_WORD_CLASS_NAME = "WordRenderer";

    /**
     * PDF footer 不支持元素：表格
     */
    public static final String ELEMENT_TABLE = "表格";

    /**
     * PDF footer 不支持元素：列表
     */
    public static final String ELEMENT_LIST = "列表（项目符号/编号）";

    /**
     * PDF footer 不支持元素：图片
     */
    public static final String ELEMENT_IMAGE_DRAWING = "图片（drawing/pict）";

    /**
     * 直接转换 chart 标识
     */
    public static final String DIRECT_CONVERT_CHART_KEY = "(direct-convert)";

    /**
     * 页眉位置描述
     */
    public static final String POSITION_HEADER = "页眉";

    /**
     * 页脚位置描述
     */
    public static final String POSITION_FOOTER = "页脚";

    // ==================== OOXML 样式默认值 ====================

    /**
     * Word 默认段落样式 ID
     */
    public static final String WORD_DEFAULT_PARA_STYLE_ID = "999";

    /**
     * Word 默认字体大小（half-point），11pt = 22 halfPt
     */
    public static final int WORD_DEFAULT_FONT_HALF_PT = 22;

    /**
     * Word 默认正文字体大小（pt）
     */
    public static final double WORD_DEFAULT_BODY_FONT_PT = 12.0;

    /**
     * Word 页眉/页脚默认字号（pt）
     */
    public static final double WORD_HEADER_DEFAULT_FONT_PT = 9.0;

    /**
     * Word 表格单元格默认字号（pt）
     */
    public static final double WORD_TABLE_CELL_DEFAULT_FONT_PT = 10.0;

    /**
     * Word 空格宽度系数（相对于字号），用于估算中英文混排空格宽度
     */
    public static final double WORD_SPACE_WIDTH_COEFFICIENT = 0.5;

    // ==================== 渲染默认值 ====================

    /**
     * 内容区宽度回退值（CSS px），当文档无 sectPr/pgSz/pgMar 时使用
     */
    public static final int CONTENT_WIDTH_PX_FALLBACK = 952;

    // ==================== Chart 渲染默认值 ====================

    /**
     * Chart 默认宽度（px）
     */
    public static final int CHART_DEFAULT_WIDTH_PX = 600;

    /**
     * Chart 默认高度（px）
     */
    public static final int CHART_DEFAULT_HEIGHT_PX = 360;

}
