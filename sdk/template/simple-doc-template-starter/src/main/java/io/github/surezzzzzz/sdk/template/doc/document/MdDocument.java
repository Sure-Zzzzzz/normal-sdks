package io.github.surezzzzzz.sdk.template.doc.document;

import io.github.surezzzzzz.sdk.template.doc.constant.SimpleDocTemplateConstant;
import io.github.surezzzzzz.sdk.template.doc.model.MdImageReference;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Markdown Document
 *
 * <p>Markdown 渲染产物，包含业务可见 Markdown 和 PDF 内部使用 Markdown。
 *
 * @author surezzzzzz
 */
@Getter
public class MdDocument implements Document {

    private final byte[] mdBytes;
    private final byte[] internalMdBytes;
    private final List<MdImageReference> imageReferences;

    public MdDocument(byte[] mdBytes, byte[] internalMdBytes, List<MdImageReference> imageReferences) {
        this.mdBytes = mdBytes != null ? mdBytes : new byte[0];
        this.internalMdBytes = internalMdBytes != null ? internalMdBytes : this.mdBytes;
        this.imageReferences = imageReferences != null
                ? Collections.unmodifiableList(new ArrayList<>(imageReferences))
                : Collections.emptyList();
    }

    @Override
    public String getFormat() {
        return SimpleDocTemplateConstant.FORMAT_MD;
    }

    @Override
    public boolean isEmpty() {
        return mdBytes.length == 0;
    }
}
