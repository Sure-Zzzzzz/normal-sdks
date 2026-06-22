package io.github.surezzzzzz.sdk.template.doc.model;

import lombok.Getter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Image - 图片数据（格式无关的渲染输入模型）
 *
 * <p>封装图片的路径/URL、宽高，渲染阶段由各 Renderer 读取并插入到文档对应位置。
 *
 * <p>宽高语义：
 * <ul>
 *   <li>width = 0：使用原图宽度（原图也读不到时用容器宽度）</li>
 *   <li>width &gt; 0：使用指定宽度（px）</li>
 *   <li>height = 0：使用原图高度</li>
 *   <li>height &gt; 0：使用指定高度（px）</li>
 * </ul>
 *
 * @author surezzzzzz
 */
@Getter
public class Image {

    private final String src;
    private final int width;
    private final int height;
    private final String description;
    private final ImageType type;

    /**
     * 构造图片，宽高从原图读取原始尺寸（等比不变形）
     *
     * @param src 图片路径/URL
     */
    public Image(String src) {
        this(src, 0, 0, src, ImageType.fromFileName(src));
    }

    /**
     * 构造图片数据，类型根据文件后缀自动推断，描述取自 src
     *
     * @param src    图片路径/URL
     * @param width  宽度（px）。0 = 使用原图宽度
     * @param height 高度（px）。0 = 使用原图高度
     */
    public Image(String src, int width, int height) {
        this(src, width, height, src, ImageType.fromFileName(src));
    }

    /**
     * 构造图片数据，类型根据文件后缀自动推断
     *
     * @param src         图片路径/URL
     * @param width       宽度（px）。0 = 使用原图宽度
     * @param height      高度（px）。0 = 使用原图高度
     * @param description 描述
     */
    public Image(String src, int width, int height, String description) {
        this(src, width, height, description, ImageType.fromFileName(src));
    }

    /**
     * 全量构造器
     *
     * @param src         图片路径/URL
     * @param width       宽度（px）。0 = 使用原图宽度
     * @param height      高度（px）。0 = 使用原图高度
     * @param description 描述
     * @param type        图片类型
     */
    public Image(String src, int width, int height, String description, ImageType type) {
        this.src = src;
        this.width = width;
        this.height = height;
        this.description = description;
        this.type = type;
    }

    /**
     * 宽高是否全为 0（使用原图尺寸）
     */
    public boolean isNaturalSize() {
        return width <= 0 && height <= 0;
    }

    /**
     * 获取实际宽度（px）：width &gt; 0 返回指定值，否则读取图片原始宽度
     */
    public int resolveWidth() {
        if (width > 0) {
            return width;
        }
        return readNaturalWidth();
    }

    /**
     * 获取实际高度（px）：height &gt; 0 返回指定值，否则读取图片原始高度
     */
    public int resolveHeight() {
        if (height > 0) {
            return height;
        }
        return readNaturalHeight();
    }

    private int readNaturalWidth() {
        BufferedImage img = readImage();
        return img != null ? img.getWidth() : 0;
    }

    private int readNaturalHeight() {
        BufferedImage img = readImage();
        return img != null ? img.getHeight() : 0;
    }

    private BufferedImage readImage() {
        try {
            if (src == null || src.isEmpty()) {
                return null;
            }
            Path path = Paths.get(src);
            if (Files.exists(path)) {
                return ImageIO.read(path.toFile());
            }
            if (src.startsWith("classpath:")) {
                String resourcePath = src.substring("classpath:".length());
                try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                    if (is != null) {
                        return ImageIO.read(is);
                    }
                }
            }
            try {
                URL url = new URL(src);
                return ImageIO.read(url);
            } catch (Exception ignored) {
            }
        } catch (IOException e) {
            // fall through
        }
        return null;
    }

    /**
     * 图片类型枚举
     */
    @Getter
    public enum ImageType {

        PNG("png", "PNG 图片"),
        JPEG("jpeg", "JPEG 图片"),
        GIF("gif", "GIF 图片");

        private static final String EXT_JPG = ".jpg";
        private static final String EXT_JPEG = ".jpeg";
        private static final String EXT_GIF = ".gif";

        private final String code;
        private final String description;

        ImageType(String code, String description) {
            this.code = code;
            this.description = description;
        }

        /**
         * 根据图片类型代码获取图片类型
         *
         * @param code 图片类型代码
         * @return 图片类型，不存在返回 null
         */
        public static ImageType fromCode(String code) {
            if (code == null) {
                return null;
            }
            for (ImageType type : values()) {
                if (type.code.equalsIgnoreCase(code)) {
                    return type;
                }
            }
            return null;
        }

        /**
         * 根据文件名推断图片类型
         *
         * @param fileName 文件名
         * @return 图片类型，无法识别时返回 PNG
         */
        public static ImageType fromFileName(String fileName) {
            if (fileName == null) {
                return PNG;
            }
            String lower = fileName.toLowerCase();
            if (lower.endsWith(EXT_JPG) || lower.endsWith(EXT_JPEG)) {
                return JPEG;
            }
            if (lower.endsWith(EXT_GIF)) {
                return GIF;
            }
            return PNG;
        }

        /**
         * 判断图片类型代码是否有效
         *
         * @param code 图片类型代码
         * @return true 有效，false 无效
         */
        public static boolean isValid(String code) {
            return fromCode(code) != null;
        }

        /**
         * 获取所有图片类型代码
         *
         * @return 图片类型代码数组
         */
        public static String[] getAllCodes() {
            ImageType[] types = values();
            String[] codes = new String[types.length];
            for (int i = 0; i < types.length; i++) {
                codes[i] = types[i].code;
            }
            return codes;
        }

        @Override
        public String toString() {
            return code;
        }
    }
}