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
    PDF(SimpleDocTemplateConstant.FORMAT_PDF, "PDF 文档（P1）"),
    HTML(SimpleDocTemplateConstant.FORMAT_HTML, "HTML 文档（P1）");

    private final String code;
    private final String description;

    OutputFormat(String code, String description) {
        this.code = code;
        this.description = description;
    }

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

    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

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