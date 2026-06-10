package io.github.surezzzzzz.sdk.oss.s3.constant;

import lombok.Getter;

/**
 * Content-Disposition 枚举
 */
@Getter
public enum FileDisposition {

    DOWNLOAD("attachment", "下载"),
    INLINE("inline", "预览");

    private final String disposition;
    private final String description;

    FileDisposition(String disposition, String description) {
        this.disposition = disposition;
        this.description = description;
    }

    /**
     * 根据 Content-Disposition 头部格式生成值
     */
    public String getContentDisposition(String fileName) {
        return String.format("%s; filename=\"%s\"", disposition, fileName);
    }

    /**
     * 根据 code 获取枚举
     */
    public static FileDisposition fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (FileDisposition d : values()) {
            if (d.name().equalsIgnoreCase(code)) {
                return d;
            }
        }
        return null;
    }

    /**
     * 判断类型代码是否有效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取所有有效的类型代码
     */
    public static String[] getAllCodes() {
        FileDisposition[] values = values();
        String[] codes = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            codes[i] = values[i].name();
        }
        return codes;
    }

    @Override
    public String toString() {
        return name();
    }
}