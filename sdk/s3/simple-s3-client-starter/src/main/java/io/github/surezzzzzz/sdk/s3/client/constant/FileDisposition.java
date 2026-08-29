package io.github.surezzzzzz.sdk.s3.client.constant;

import lombok.Getter;

/**
 * Content-Disposition 枚举（迁自老 s3-client-starter，语义不变）。
 *
 * @author surezzzzzz
 */
@Getter
public enum FileDisposition {

    /**
     * 附件下载模式
     */
    DOWNLOAD(SimpleS3ClientConstant.CONTENT_DISPOSITION_ATTACHMENT, "下载"),

    /**
     * 内联预览模式
     */
    INLINE(SimpleS3ClientConstant.CONTENT_DISPOSITION_INLINE, "预览");

    /**
     * Content-Disposition 值
     */
    private final String disposition;

    /**
     * 描述
     */
    private final String description;

    FileDisposition(String disposition, String description) {
        this.disposition = disposition;
        this.description = description;
    }

    /**
     * 根据 code 获取枚举（大小写不敏感）。
     *
     * @param code 枚举名
     * @return 对应枚举，无法识别时返回 null
     */
    public static FileDisposition fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (FileDisposition disposition : values()) {
            if (disposition.name().equalsIgnoreCase(code)) {
                return disposition;
            }
        }
        return null;
    }

    /**
     * 判断类型代码是否有效。
     *
     * @param code 枚举名
     * @return 有效返回 true
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取所有有效的类型代码。
     *
     * @return 枚举名数组
     */
    public static String[] getAllCodes() {
        FileDisposition[] dispositions = values();
        String[] codes = new String[dispositions.length];
        for (int i = 0; i < dispositions.length; i++) {
            codes[i] = dispositions[i].name();
        }
        return codes;
    }

    /**
     * 按 Content-Disposition 头部格式生成值。
     *
     * @param fileName 文件名
     * @return Content-Disposition 头部值
     */
    public String getContentDisposition(String fileName) {
        return String.format(SimpleS3ClientConstant.CONTENT_DISPOSITION_TEMPLATE, disposition, fileName);
    }

    @Override
    public String toString() {
        return name();
    }
}
