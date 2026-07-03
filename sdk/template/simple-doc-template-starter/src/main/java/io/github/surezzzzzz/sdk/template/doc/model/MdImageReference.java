package io.github.surezzzzzz.sdk.template.doc.model;

import lombok.Getter;

/**
 * Markdown Image Reference
 *
 * <p>记录 Markdown 模板中由 SDK 图片标签生成的图片引用。
 *
 * @author surezzzzzz
 */
@Getter
public class MdImageReference {

    private final String key;
    private final String token;
    private final String src;
    private final String description;
    private final int width;
    private final int height;
    private final Image image;

    public MdImageReference(String key, String token, Image image) {
        this.key = key;
        this.token = token;
        this.image = image;
        this.src = image == null ? null : image.getSrc();
        this.description = image == null ? null : image.getDescription();
        this.width = image == null ? 0 : image.getWidth();
        this.height = image == null ? 0 : image.getHeight();
    }
}
