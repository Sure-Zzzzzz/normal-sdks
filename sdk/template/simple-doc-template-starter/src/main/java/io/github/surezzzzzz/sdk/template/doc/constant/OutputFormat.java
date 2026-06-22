package io.github.surezzzzzz.sdk.template.doc.constant;

import lombok.Getter;

/**
 * Output Format Enum
 *
 * @author surezzzzzz
 */
@Getter
public enum OutputFormat {

    DOCX(SimpleDocTemplateConstant.FORMAT_DOCX, "Word 文档"),
    MD(SimpleDocTemplateConstant.FORMAT_MD, "Markdown 文档"),
    PDF(SimpleDocTemplateConstant.FORMAT_PDF, "PDF 文档");

    private final String code;
    private final String description;

    OutputFormat(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据格式代码获取输出格式
     *
     * @param code 格式代码
     * @return 输出格式，不存在返回 null
     */
    public static OutputFormat fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (OutputFormat format : values()) {
            if (format.code.equalsIgnoreCase(code)) {
                return format;
            }
        }
        return null;
    }

    /**
     * 判断格式代码是否有效
     *
     * @param code 格式代码
     * @return true 有效，false 无效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取所有格式代码
     *
     * @return 格式代码数组
     */
    public static String[] getAllCodes() {
        OutputFormat[] formats = values();
        String[] codes = new String[formats.length];
        for (int i = 0; i < formats.length; i++) {
            codes[i] = formats[i].code;
        }
        return codes;
    }

    @Override
    public String toString() {
        return code;
    }
}